package com.mokshith.jarvis.ai.channels

import com.mokshith.jarvis.ai.brain.AgentEngine
import com.mokshith.jarvis.ai.core.JarvisDispatchers
import com.mokshith.jarvis.ai.core.JarvisJson
import com.mokshith.jarvis.ai.memory.ChannelConfigEntity
import com.mokshith.jarvis.ai.memory.ChannelDao
import com.mokshith.jarvis.ai.memory.ConversationDao
import com.mokshith.jarvis.ai.memory.ConversationEntity
import com.mokshith.jarvis.ai.memory.MessageEntity
import com.mokshith.jarvis.ai.security.CredentialRedactor
import com.mokshith.jarvis.ai.security.CredentialVault
import com.mokshith.jarvis.ai.security.KeystoreCredentialVault
import com.mokshith.jarvis.ai.security.PromptInjectionGuard
import com.mokshith.jarvis.ai.tasks.TaskEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class TelegramChannelConnector(
    private val httpClient: OkHttpClient,
    private val credentialVault: CredentialVault,
    private val channelDao: ChannelDao,
    private val conversationDao: ConversationDao,
    private val agentEngine: AgentEngine,
    private val taskEngine: TaskEngine,
    private val dispatchers: JarvisDispatchers
) : ChannelConnector {

    override val channelId = CHANNEL_ID
    override val displayName = "Telegram Bot"

    private val json = JarvisJson.instance
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private var pollingJob: Job? = null

    private val _runtimeState = MutableStateFlow(
        ChannelRuntimeState(channelId = CHANNEL_ID, displayName = displayName, running = false)
    )
    override val runtimeState: StateFlow<ChannelRuntimeState> = _runtimeState.asStateFlow()

    suspend fun saveConfiguration(botToken: String?, authorizedIdsCsv: String, enabled: Boolean) {
        if (!botToken.isNullOrBlank()) {
            credentialVault.putSecret(KeystoreCredentialVault.KEY_TELEGRAM_BOT_TOKEN, botToken)
        }
        val existing = channelDao.getChannel(CHANNEL_ID) ?: ChannelConfigEntity(
            channelId = CHANNEL_ID,
            displayName = displayName
        )
        channelDao.upsert(
            existing.copy(
                enabled = enabled,
                authorizedUserIdsCsv = authorizedIdsCsv.trim()
            )
        )
    }

    override suspend fun start() {
        val config = channelDao.getChannel(CHANNEL_ID)
        if (config == null || !config.enabled) {
            updateError("Telegram must be explicitly enabled before starting.")
            return
        }
        if (!credentialVault.hasSecret(KeystoreCredentialVault.KEY_TELEGRAM_BOT_TOKEN)) {
            updateError("Telegram bot token is not configured.")
            return
        }
        if (TelegramAllowlistPolicy.parseAuthorizedIds(config.authorizedUserIdsCsv).isEmpty()) {
            updateError("Configure at least one authorized Telegram user ID before starting.")
            return
        }
        if (pollingJob?.isActive == true) return

        _runtimeState.value = _runtimeState.value.copy(running = true, lastError = null)
        channelDao.upsert(config.copy(status = "RUNNING", lastError = null))

        pollingJob = scope.launch {
            var backoffMs = 2000L
            while (isActive) {
                try {
                    pollOnce()
                    backoffMs = 2000L
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    val safe = CredentialRedactor.redact(t.message ?: "Polling error", credentialVault.allSecretValues())
                    updateError(safe)
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    override suspend fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        _runtimeState.value = _runtimeState.value.copy(running = false)
        channelDao.getChannel(CHANNEL_ID)?.let {
            channelDao.upsert(it.copy(status = "STOPPED"))
        }
    }

    override suspend fun sendOutbound(targetId: String, text: String): Result<Unit> {
        val chatId = targetId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("Invalid Telegram chat ID"))
        return runCatching { sendMessage(chatId, text) }
    }

    suspend fun sendMessage(chatId: Long, text: String) = withContext(dispatchers.io) {
        val token = credentialVault.getSecret(KeystoreCredentialVault.KEY_TELEGRAM_BOT_TOKEN)
            ?: throw IllegalStateException("Telegram bot token missing")
        val url = "https://api.telegram.org/bot$token/sendMessage"
        val payload = buildJsonObject {
            put("chat_id", chatId)
            put("text", text.take(4000))
        }.toString()
        val req = Request.Builder().url(url).post(payload.toRequestBody(mediaType)).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Telegram sendMessage failed (HTTP ${resp.code})")
            }
        }
    }

    private suspend fun pollOnce() {
        val config = channelDao.getChannel(CHANNEL_ID) ?: return
        if (!config.enabled) {
            stop()
            return
        }
        val token = credentialVault.getSecret(KeystoreCredentialVault.KEY_TELEGRAM_BOT_TOKEN)
            ?: throw IllegalStateException("Bot token missing")

        val offset = config.lastUpdateOffset
        val url = "https://api.telegram.org/bot$token/getUpdates?timeout=25&offset=$offset"
        val req = Request.Builder().url(url).get().build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Telegram getUpdates HTTP ${resp.code}")
            }
            val raw = resp.body?.string().orEmpty()
            val root = json.parseToJsonElement(raw).jsonObject
            if (root["ok"]?.jsonPrimitive?.booleanOrNull != true) {
                throw IllegalStateException("Telegram API returned ok=false")
            }

            val now = System.currentTimeMillis()
            var nextOffset = offset
            val updates = root["result"]?.jsonArray.orEmpty()
            for (updateEl in updates) {
                val uObj = updateEl.jsonObject
                val updateId = uObj["update_id"]?.jsonPrimitive?.longOrNull ?: continue
                if (updateId >= nextOffset) nextOffset = updateId + 1

                val msg = uObj["message"]?.jsonObject ?: continue
                val senderId = msg["from"]?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull
                val chatId = msg["chat"]?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull ?: continue
                val text = msg["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (text.isBlank()) continue

                if (!TelegramAllowlistPolicy.isAuthorized(senderId, config.authorizedUserIdsCsv)) {
                    // Reject unauthorized senders silently (or minimal refusal) without running agent
                    continue
                }
                handleAuthorizedMessage(senderId!!, chatId, text)
            }

            channelDao.upsert(
                config.copy(
                    lastUpdateOffset = nextOffset,
                    lastSuccessfulPollAt = now,
                    lastError = null,
                    status = "RUNNING"
                )
            )
            _runtimeState.value = _runtimeState.value.copy(running = true, lastPollAt = now, lastError = null)
        }
    }

    private suspend fun handleAuthorizedMessage(senderId: Long, chatId: Long, text: String) {
        when {
            text.equals("/start", ignoreCase = true) || text.equals("/help", ignoreCase = true) -> {
                sendMessage(
                    chatId,
                    "JARVIS connected. Send any objective, or use:\n/status — Agent & gateway status\n/task <objective> — Queue a background task\n/tasks — List active tasks"
                )
            }
            text.equals("/status", ignoreCase = true) -> {
                sendMessage(chatId, "JARVIS Alpha online on Android. Authorized user: $senderId.")
            }
            text.equals("/tasks", ignoreCase = true) -> {
                val recent = taskEngine.observeAllTasks().first().take(8)
                val body = if (recent.isEmpty()) {
                    "No tasks yet. Use /task <objective> to queue a background task."
                } else {
                    recent.joinToString("\n") { "[${it.status} ${it.progressPercent}%] ${it.title}" }
                }
                sendMessage(chatId, body)
            }
            text.startsWith("/task ", ignoreCase = true) -> {
                val obj = text.removePrefix("/task ").trim()
                val task = taskEngine.enqueueTask(
                    title = obj.take(48),
                    objective = obj,
                    sourceChannel = "TELEGRAM",
                    telegramChatId = chatId
                )
                sendMessage(chatId, "Queued background task: ${task.title} (ID: ${task.id.take(8)}). I will notify you when it finishes.")
            }
            else -> {
                val convId = "telegram_$chatId"
                val now = System.currentTimeMillis()
                if (conversationDao.getById(convId) == null) {
                    conversationDao.upsert(
                        ConversationEntity(
                            id = convId,
                            title = "Telegram ($senderId)",
                            providerId = null,
                            modelId = null,
                            channelSource = "TELEGRAM",
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
                conversationDao.insertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = "user",
                        content = text,
                        createdAt = now
                    )
                )
                val safeInput = PromptInjectionGuard.sanitizeTelegramInput(senderId, text)
                val result = runCatching {
                    agentEngine.run(objective = safeInput, conversationId = convId)
                }
                val reply = result.fold(
                    onSuccess = { it.finalText },
                    onFailure = { "JARVIS encountered an error: ${CredentialRedactor.redact(it.message ?: "Unknown", credentialVault.allSecretValues())}" }
                )
                conversationDao.insertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = "assistant",
                        content = reply,
                        createdAt = System.currentTimeMillis()
                    )
                )
                sendMessage(chatId, reply)
            }
        }
    }

    private suspend fun updateError(safeError: String) {
        _runtimeState.value = _runtimeState.value.copy(lastError = safeError)
        channelDao.getChannel(CHANNEL_ID)?.let {
            channelDao.upsert(it.copy(lastError = safeError, status = if (_runtimeState.value.running) "ERROR" else "STOPPED"))
        }
    }

    companion object {
        const val CHANNEL_ID = "telegram"
    }
}
