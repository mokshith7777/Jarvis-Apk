package com.mokshith.jarvis.ai.gateway

import com.mokshith.jarvis.ai.core.JarvisJson
import com.mokshith.jarvis.ai.memory.ProviderConfigEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Native Google AI (Gemini REST API v1beta) adapter.
 * Uses x-goog-api-key header (never places API key in URL query logs) and supports SSE streaming + function calling.
 */
class GoogleAiAdapter(
    private val client: OkHttpClient
) : ProviderAdapter {

    private val json = JarvisJson.instance
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun chat(
        provider: ProviderConfigEntity,
        apiKey: String,
        modelId: String,
        request: GatewayRequest,
        onDelta: (String) -> Unit
    ): GatewayResponse {
        val start = System.currentTimeMillis()
        val normalizedModel = modelId.removePrefix("models/")
        val action = if (request.stream) "streamGenerateContent?alt=sse" else "generateContent"
        val url = "${provider.baseUrl.trimEnd('/')}/models/$normalizedModel:$action"
        val bodyJson = buildGeminiPayload(request)

        val httpRequest = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(mediaType))
            .build()

        val call = client.newCall(httpRequest)
        try {
            call.execute().use { response ->
                val code = response.code
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty().take(500)
                    throw GatewayException(
                        message = "Google AI returned HTTP $code: $err",
                        httpCode = code,
                        retryable = code == 429 || code in 500..599
                    )
                }
                val body = response.body ?: throw GatewayException("Empty Google AI response", code, retryable = true)
                return if (request.stream) {
                    parseGeminiSse(provider.id, modelId, body.charStream(), start, onDelta)
                } else {
                    parseGeminiJson(provider.id, modelId, body.string(), start, onDelta)
                }
            }
        } catch (e: IOException) {
            call.cancel()
            throw GatewayException("Network error calling Google AI: ${e.message}", retryable = true, cause = e)
        }
    }

    override suspend fun fetchModels(provider: ProviderConfigEntity, apiKey: String): List<String> {
        val url = "${provider.baseUrl.trimEnd('/')}/models"
        val req = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .get()
            .build()
        try {
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    throw GatewayException("Google AI model discovery failed (${response.code})", response.code)
                }
                val raw = response.body?.string().orEmpty()
                val models = json.parseToJsonElement(raw).jsonObject["models"]?.jsonArray ?: return emptyList()
                return models.mapNotNull { el ->
                    val obj = el.jsonObject
                    val methods = obj["supportedGenerationMethods"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                    if ("generateContent" in methods) {
                        obj["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/")
                    } else null
                }.distinct().sorted()
            }
        } catch (e: IOException) {
            throw GatewayException("Network error discovering Google AI models: ${e.message}", retryable = true, cause = e)
        }
    }

    internal fun buildGeminiPayload(request: GatewayRequest): String {
        val systemText = request.messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }
        val nonSystem = request.messages.filter { it.role != "system" }

        return buildJsonObject {
            if (systemText.isNotBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", systemText) }) })
                })
            }
            put("contents", buildJsonArray {
                nonSystem.forEach { msg ->
                    val role = when (msg.role) {
                        "assistant" -> "model"
                        else -> "user"
                    }
                    add(buildJsonObject {
                        put("role", role)
                        put("parts", buildJsonArray {
                            val text = if (msg.role == "tool") {
                                "[Tool Result: ${msg.toolName ?: "tool"}]\n${msg.content}"
                            } else msg.content
                            add(buildJsonObject { put("text", text) })
                        })
                    })
                }
            })
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("functionDeclarations", buildJsonArray {
                            request.tools.forEach { tool ->
                                val paramsEl = runCatching { json.parseToJsonElement(tool.parametersSchemaJson) }
                                    .getOrElse { buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}) } }
                                add(buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", paramsEl)
                                })
                            }
                        })
                    })
                })
            }
            put("generationConfig", buildJsonObject {
                put("temperature", request.temperature)
                put("maxOutputTokens", request.maxTokens)
            })
        }.toString()
    }

    private suspend fun parseGeminiSse(
        providerId: String,
        modelId: String,
        reader: java.io.Reader,
        startMillis: Long,
        onDelta: (String) -> Unit
    ): GatewayResponse {
        val textBuilder = StringBuilder()
        val toolCalls = mutableListOf<ToolCallRequest>()
        var usage = TokenUsage()
        var callCounter = 0

        reader.buffered().use { br ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = br.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue
                val chunk = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                usage = extractUsage(chunk, usage)
                val parts = chunk["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: continue
                for (part in parts) {
                    val obj = part.jsonObject
                    obj["text"]?.jsonPrimitive?.contentOrNull?.let { t ->
                        if (t.isNotEmpty()) {
                            textBuilder.append(t)
                            onDelta(t)
                        }
                    }
                    obj["functionCall"]?.jsonObject?.let { fc ->
                        val name = fc["name"]?.jsonPrimitive?.contentOrNull ?: return@let
                        val args = fc["args"]?.toString() ?: "{}"
                        toolCalls.add(ToolCallRequest("gemini_call_${callCounter++}", name, args))
                    }
                }
            }
        }

        return GatewayResponse(
            providerId = providerId,
            modelId = modelId,
            content = textBuilder.toString(),
            toolCalls = toolCalls,
            usage = usage,
            latencyMs = System.currentTimeMillis() - startMillis
        )
    }

    private fun parseGeminiJson(
        providerId: String,
        modelId: String,
        raw: String,
        startMillis: Long,
        onDelta: (String) -> Unit
    ): GatewayResponse {
        val root = json.parseToJsonElement(raw).jsonObject
        val usage = extractUsage(root, TokenUsage())
        val textBuilder = StringBuilder()
        val toolCalls = mutableListOf<ToolCallRequest>()
        val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray.orEmpty()
        parts.forEachIndexed { idx, part ->
            val obj = part.jsonObject
            obj["text"]?.jsonPrimitive?.contentOrNull?.let { textBuilder.append(it) }
            obj["functionCall"]?.jsonObject?.let { fc ->
                val name = fc["name"]?.jsonPrimitive?.contentOrNull ?: return@let
                val args = fc["args"]?.toString() ?: "{}"
                toolCalls.add(ToolCallRequest("gemini_call_$idx", name, args))
            }
        }
        val finalContent = textBuilder.toString()
        if (finalContent.isNotEmpty()) onDelta(finalContent)
        return GatewayResponse(providerId, modelId, finalContent, toolCalls, usage, System.currentTimeMillis() - startMillis)
    }

    private fun extractUsage(root: kotlinx.serialization.json.JsonObject, fallback: TokenUsage): TokenUsage {
        val meta = root["usageMetadata"]?.jsonObject ?: return fallback
        return TokenUsage(
            promptTokens = meta["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: fallback.promptTokens,
            completionTokens = meta["candidatesTokenCount"]?.jsonPrimitive?.intOrNull ?: fallback.completionTokens,
            totalTokens = meta["totalTokenCount"]?.jsonPrimitive?.intOrNull ?: fallback.totalTokens
        )
    }
}
