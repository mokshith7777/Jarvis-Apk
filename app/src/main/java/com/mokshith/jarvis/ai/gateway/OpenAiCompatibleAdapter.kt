package com.mokshith.jarvis.ai.gateway

import com.mokshith.jarvis.ai.core.JarvisJson
import com.mokshith.jarvis.ai.memory.ProviderConfigEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * Unified adapter for Groq, OpenRouter, Mistral, DeepSeek, and Custom OpenAI-compatible APIs.
 * Supports real SSE streaming, structured tool calling, usage extraction, and /models discovery.
 */
class OpenAiCompatibleAdapter(
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
        val endpoint = "${provider.baseUrl.trimEnd('/')}/chat/completions"
        val bodyJson = buildRequestPayload(modelId, request)

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(mediaType))

        if (provider.type == ProviderType.OPENROUTER.name) {
            reqBuilder.header("HTTP-Referer", "https://jarvis.local")
            reqBuilder.header("X-Title", "JARVIS Android")
        }

        val call = client.newCall(reqBuilder.build())
        try {
            call.execute().use { response ->
                val code = response.code
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty().take(500)
                    val retryable = code == 429 || code in 500..599
                    throw GatewayException(
                        message = "${provider.displayName} returned HTTP $code: $errBody",
                        httpCode = code,
                        retryable = retryable
                    )
                }

                val body = response.body ?: throw GatewayException("Empty response body", code, retryable = true)
                return if (request.stream) {
                    parseSseStream(provider.id, modelId, body.charStream(), start, onDelta)
                } else {
                    parseStandardResponse(provider.id, modelId, body.string(), start, onDelta)
                }
            }
        } catch (e: IOException) {
            call.cancel()
            throw GatewayException("Network error calling ${provider.displayName}: ${e.message}", retryable = true, cause = e)
        }
    }

    override suspend fun fetchModels(provider: ProviderConfigEntity, apiKey: String): List<String> {
        val endpoint = "${provider.baseUrl.trimEnd('/')}/models"
        val req = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        try {
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty().take(300)
                    throw GatewayException("Model discovery failed (${response.code}): $err", response.code, retryable = response.code >= 500)
                }
                val raw = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(raw).jsonObject
                val data = root["data"]?.jsonArray ?: return emptyList()
                return data.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                    .distinct()
                    .sorted()
            }
        } catch (e: IOException) {
            throw GatewayException("Network error discovering models: ${e.message}", retryable = true, cause = e)
        }
    }

    internal fun buildRequestPayload(modelId: String, request: GatewayRequest): String {
        val payload = buildJsonObject {
            put("model", modelId)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            put("stream", request.stream)
            put("messages", buildJsonArray {
                request.messages.forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                        if (msg.toolCallId != null) {
                            put("tool_call_id", msg.toolCallId)
                        }
                        if (msg.toolName != null && msg.role == "tool") {
                            put("name", msg.toolName)
                        }
                        if (!msg.toolCalls.isNullOrEmpty()) {
                            put("tool_calls", buildJsonArray {
                                msg.toolCalls.forEach { tc ->
                                    add(buildJsonObject {
                                        put("id", tc.id)
                                        put("type", "function")
                                        put("function", buildJsonObject {
                                            put("name", tc.name)
                                            put("arguments", tc.argumentsJson)
                                        })
                                    })
                                }
                            })
                        }
                    })
                }
            })
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    request.tools.forEach { tool ->
                        val paramsEl = runCatching { json.parseToJsonElement(tool.parametersSchemaJson) }
                            .getOrElse { buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}) } }
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", paramsEl)
                            })
                        })
                    }
                })
                put("tool_choice", "auto")
            }
        }
        return payload.toString()
    }

    private suspend fun parseSseStream(
        providerId: String,
        modelId: String,
        reader: java.io.Reader,
        startMillis: Long,
        onDelta: (String) -> Unit
    ): GatewayResponse {
        val contentBuilder = StringBuilder()
        val toolCallAccumulators = linkedMapOf<Int, MutableToolCall>()
        var usage = TokenUsage()

        reader.buffered().use { br ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = br.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") break

                val chunk = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                chunk["usage"]?.jsonObject?.let { u ->
                    usage = TokenUsage(
                        promptTokens = u["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                        completionTokens = u["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                        totalTokens = u["total_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                    )
                }

                val choices = chunk["choices"]?.jsonArray ?: continue
                val first = choices.firstOrNull()?.jsonObject ?: continue
                val delta = first["delta"]?.jsonObject ?: continue

                val textPart = delta["content"]?.jsonPrimitive?.contentOrNull
                if (!textPart.isNullOrEmpty()) {
                    contentBuilder.append(textPart)
                    onDelta(textPart)
                }

                val deltaToolCalls = delta["tool_calls"]?.jsonArray
                if (deltaToolCalls != null) {
                    for (tcEl in deltaToolCalls) {
                        val tcObj = tcEl.jsonObject
                        val index = tcObj["index"]?.jsonPrimitive?.intOrNull ?: 0
                        val acc = toolCallAccumulators.getOrPut(index) { MutableToolCall() }
                        tcObj["id"]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotBlank()) acc.id = it }
                        val fn = tcObj["function"]?.jsonObject
                        fn?.get("name")?.jsonPrimitive?.contentOrNull?.let { acc.name += it }
                        fn?.get("arguments")?.jsonPrimitive?.contentOrNull?.let { acc.arguments += it }
                    }
                }
            }
        }

        val toolCalls = toolCallAccumulators.entries.sortedBy { it.key }.map { (idx, acc) ->
            ToolCallRequest(
                id = acc.id.ifBlank { "call_$idx" },
                name = acc.name,
                argumentsJson = acc.arguments.ifBlank { "{}" }
            )
        }.filter { it.name.isNotBlank() }

        return GatewayResponse(
            providerId = providerId,
            modelId = modelId,
            content = contentBuilder.toString(),
            toolCalls = toolCalls,
            usage = usage,
            latencyMs = System.currentTimeMillis() - startMillis
        )
    }

    private fun parseStandardResponse(
        providerId: String,
        modelId: String,
        raw: String,
        startMillis: Long,
        onDelta: (String) -> Unit
    ): GatewayResponse {
        val root = json.parseToJsonElement(raw).jsonObject
        val usageObj = root["usage"]?.jsonObject
        val usage = TokenUsage(
            promptTokens = usageObj?.get("prompt_tokens")?.jsonPrimitive?.intOrNull ?: 0,
            completionTokens = usageObj?.get("completion_tokens")?.jsonPrimitive?.intOrNull ?: 0,
            totalTokens = usageObj?.get("total_tokens")?.jsonPrimitive?.intOrNull ?: 0
        )
        val msg = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
        val content = msg?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
        if (content.isNotEmpty()) onDelta(content)

        val toolCalls = msg?.get("tool_calls")?.jsonArray?.mapIndexedNotNull { idx, el ->
            val obj = el.jsonObject
            val fn = obj["function"]?.jsonObject ?: return@mapIndexedNotNull null
            val name = fn["name"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
            val args = fn["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
            ToolCallRequest(
                id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "call_$idx",
                name = name,
                argumentsJson = args
            )
        }.orEmpty()

        return GatewayResponse(
            providerId = providerId,
            modelId = modelId,
            content = content,
            toolCalls = toolCalls,
            usage = usage,
            latencyMs = System.currentTimeMillis() - startMillis
        )
    }

    private class MutableToolCall(
        var id: String = "",
        var name: String = "",
        var arguments: String = ""
    )
}
