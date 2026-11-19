package com.mokshith.jarvis.ai.gateway

import com.mokshith.jarvis.ai.core.JarvisDispatchers
import com.mokshith.jarvis.ai.core.JarvisJson
import com.mokshith.jarvis.ai.memory.ProviderConfigEntity
import com.mokshith.jarvis.ai.memory.ProviderDao
import com.mokshith.jarvis.ai.memory.ProviderUsageEntity
import com.mokshith.jarvis.ai.security.CredentialRedactor
import com.mokshith.jarvis.ai.security.CredentialVault
import com.mokshith.jarvis.ai.security.KeystoreCredentialVault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/**
 * Self-contained on-device multi-provider AI gateway with automatic failover,
 * exponential backoff, model discovery, health tracking, and encrypted credentials.
 */
class AiGateway(
    private val providerDao: ProviderDao,
    private val credentialVault: CredentialVault,
    private val openAiAdapter: ProviderAdapter,
    private val googleAiAdapter: ProviderAdapter,
    private val dispatchers: JarvisDispatchers
) {
    private val json = JarvisJson.instance

    fun observeProviders(): Flow<List<ProviderConfigEntity>> = providerDao.observeProviders()
    fun observeUsage(limit: Int = 200): Flow<List<ProviderUsageEntity>> = providerDao.observeUsage(limit)

    suspend fun saveProvider(
        config: ProviderConfigEntity,
        apiKey: String?,
        setAsDefault: Boolean = config.isDefault
    ) = withContext(dispatchers.io) {
        if (setAsDefault) {
            providerDao.clearDefaults()
        }
        providerDao.upsertProvider(config.copy(isDefault = setAsDefault))
        if (!apiKey.isNullOrBlank()) {
            credentialVault.putSecret(KeystoreCredentialVault.providerKey(config.id), apiKey)
        }
    }

    suspend fun deleteProvider(providerId: String) = withContext(dispatchers.io) {
        providerDao.deleteProvider(providerId)
        credentialVault.removeSecret(KeystoreCredentialVault.providerKey(providerId))
    }

    suspend fun setDefaultProvider(providerId: String) = withContext(dispatchers.io) {
        val target = providerDao.getById(providerId) ?: return@withContext
        providerDao.clearDefaults()
        providerDao.upsertProvider(target.copy(isDefault = true))
    }

    suspend fun addManualModel(providerId: String, modelId: String) = withContext(dispatchers.io) {
        val provider = providerDao.getById(providerId) ?: return@withContext
        val current = decodeModels(provider.cachedModelsJson).toMutableList()
        val trimmed = modelId.trim()
        if (trimmed.isNotBlank() && trimmed !in current) {
            current.add(0, trimmed)
            providerDao.upsertProvider(provider.copy(cachedModelsJson = json.encodeToString(current)))
        }
    }

    suspend fun discoverModels(providerId: String): Result<List<String>> = withContext(dispatchers.io) {
        val provider = providerDao.getById(providerId)
            ?: return@withContext Result.failure(GatewayException("Provider not found"))
        val apiKey = credentialVault.getSecret(KeystoreCredentialVault.providerKey(provider.id))
            ?: return@withContext Result.failure(GatewayException("API key is not configured for ${provider.displayName}"))

        val start = System.currentTimeMillis()
        runCatching {
            val models = adapterFor(provider).fetchModels(provider, apiKey)
            val latency = System.currentTimeMillis() - start
            val merged = (models + listOf(provider.defaultModelId)).filter { it.isNotBlank() }.distinct()
            providerDao.upsertProvider(
                provider.copy(
                    cachedModelsJson = json.encodeToString(merged),
                    lastHealthStatus = "HEALTHY",
                    lastLatencyMs = latency,
                    lastError = null,
                    lastCheckedAt = System.currentTimeMillis()
                )
            )
            merged
        }.onFailure { err ->
            val cleanError = CredentialRedactor.redact(err.message ?: "Discovery error", credentialVault.allSecretValues())
            providerDao.upsertProvider(
                provider.copy(
                    lastHealthStatus = "ERROR",
                    lastError = cleanError,
                    lastCheckedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun testConnection(providerId: String): ConnectionTestResult = withContext(dispatchers.io) {
        val start = System.currentTimeMillis()
        val discovery = discoverModels(providerId)
        val latency = System.currentTimeMillis() - start
        if (discovery.isSuccess) {
            val list = discovery.getOrDefault(emptyList())
            ConnectionTestResult(
                success = true,
                latencyMs = latency,
                discoveredModelCount = list.size,
                message = "Connected (${latency}ms). ${list.size} models available."
            )
        } else {
            val msg = CredentialRedactor.redact(
                discovery.exceptionOrNull()?.message ?: "Connection failed",
                credentialVault.allSecretValues()
            )
            ConnectionTestResult(
                success = false,
                latencyMs = latency,
                discoveredModelCount = 0,
                message = msg
            )
        }
    }

    suspend fun execute(
        request: GatewayRequest,
        onDelta: (String) -> Unit = {}
    ): GatewayResponse = withContext(dispatchers.io) {
        val allProviders = providerDao.getAllProviders().filter { it.enabled }
        val configured = allProviders.filter {
            credentialVault.hasSecret(KeystoreCredentialVault.providerKey(it.id))
        }
        if (configured.isEmpty()) {
            throw GatewayException(
                "No AI provider credentials configured. Open Models & Gateway to add a Groq, Google AI, OpenRouter, Mistral, DeepSeek, or Custom provider key."
            )
        }

        val orderedCandidates = buildCandidateOrder(configured, request.preferredProviderId)
        val errors = mutableListOf<String>()

        for (provider in orderedCandidates) {
            val apiKey = credentialVault.getSecret(KeystoreCredentialVault.providerKey(provider.id)) ?: continue
            val modelId = if (provider.id == request.preferredProviderId && !request.preferredModelId.isNullOrBlank()) {
                request.preferredModelId
            } else {
                provider.defaultModelId
            }
            if (modelId.isBlank()) {
                errors.add("${provider.displayName}: no model selected")
                continue
            }

            var attempt = 0
            val maxAttemptsPerProvider = 2
            while (attempt < maxAttemptsPerProvider) {
                attempt++
                val attemptStart = System.currentTimeMillis()
                try {
                    val response = adapterFor(provider).chat(provider, apiKey, modelId, request, onDelta)
                    recordSuccess(provider, response)
                    return@withContext response
                } catch (ce: CancellationException) {
                    throw ce
                } catch (ge: GatewayException) {
                    val latency = System.currentTimeMillis() - attemptStart
                    val safeMsg = CredentialRedactor.redact(ge.message ?: "Provider error", credentialVault.allSecretValues())
                    recordFailure(provider, modelId, latency, ge.httpCode?.toString() ?: "ERR", safeMsg)
                    errors.add("${provider.displayName}: $safeMsg")
                    if (ge.retryable && attempt < maxAttemptsPerProvider) {
                        delay(500L * (1L shl (attempt - 1)))
                    } else {
                        break // Failover to next configured provider
                    }
                } catch (t: Throwable) {
                    val latency = System.currentTimeMillis() - attemptStart
                    val safeMsg = CredentialRedactor.redact(t.message ?: "Unexpected error", credentialVault.allSecretValues())
                    recordFailure(provider, modelId, latency, "EX", safeMsg)
                    errors.add("${provider.displayName}: $safeMsg")
                    break
                }
            }
        }

        throw GatewayException("All configured providers failed: ${errors.joinToString(" | ")}")
    }

    private fun buildCandidateOrder(
        configured: List<ProviderConfigEntity>,
        preferredProviderId: String?
    ): List<ProviderConfigEntity> {
        val result = mutableListOf<ProviderConfigEntity>()
        if (!preferredProviderId.isNullOrBlank()) {
            configured.firstOrNull { it.id == preferredProviderId }?.let { result.add(it) }
        }
        configured.firstOrNull { it.isDefault && it !in result }?.let { result.add(it) }
        configured.sortedBy { it.priority }.forEach { if (it !in result) result.add(it) }
        return result
    }

    private fun adapterFor(provider: ProviderConfigEntity): ProviderAdapter {
        return if (provider.type == ProviderType.GOOGLE_AI.name) googleAiAdapter else openAiAdapter
    }

    private suspend fun recordSuccess(provider: ProviderConfigEntity, response: GatewayResponse) {
        providerDao.upsertProvider(
            provider.copy(
                lastHealthStatus = "HEALTHY",
                lastLatencyMs = response.latencyMs,
                lastError = null,
                lastCheckedAt = System.currentTimeMillis()
            )
        )
        providerDao.insertUsage(
            ProviderUsageEntity(
                providerId = provider.id,
                modelId = response.modelId,
                success = true,
                latencyMs = response.latencyMs,
                promptTokens = response.usage.promptTokens,
                completionTokens = response.usage.completionTokens,
                totalTokens = response.usage.totalTokens,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun recordFailure(
        provider: ProviderConfigEntity,
        modelId: String,
        latencyMs: Long,
        code: String,
        safeError: String
    ) {
        providerDao.upsertProvider(
            provider.copy(
                lastHealthStatus = "ERROR",
                lastLatencyMs = latencyMs,
                lastError = safeError.take(250),
                lastCheckedAt = System.currentTimeMillis()
            )
        )
        providerDao.insertUsage(
            ProviderUsageEntity(
                providerId = provider.id,
                modelId = modelId,
                success = false,
                latencyMs = latencyMs,
                errorCode = code,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun decodeModels(jsonStr: String): List<String> {
        return runCatching { json.decodeFromString<List<String>>(jsonStr) }.getOrDefault(emptyList())
    }
}
