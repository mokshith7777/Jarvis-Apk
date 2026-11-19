package com.mokshith.jarvis.ai.gateway

import kotlinx.serialization.Serializable

enum class ProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val suggestedDefaultModel: String,
    val supportsOpenAiModelsEndpoint: Boolean
) {
    GROQ(
        displayName = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        suggestedDefaultModel = "llama-3.3-70b-versatile",
        supportsOpenAiModelsEndpoint = true
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        suggestedDefaultModel = "meta-llama/llama-3.3-70b-instruct",
        supportsOpenAiModelsEndpoint = true
    ),
    GOOGLE_AI(
        displayName = "Google AI (Gemini)",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        suggestedDefaultModel = "gemini-2.0-flash",
        supportsOpenAiModelsEndpoint = false
    ),
    MISTRAL(
        displayName = "Mistral AI",
        defaultBaseUrl = "https://api.mistral.ai/v1",
        suggestedDefaultModel = "mistral-small-latest",
        supportsOpenAiModelsEndpoint = true
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        suggestedDefaultModel = "deepseek-chat",
        supportsOpenAiModelsEndpoint = true
    ),
    CUSTOM_OPENAI(
        displayName = "Custom OpenAI-Compatible",
        defaultBaseUrl = "https://api.openai.com/v1",
        suggestedDefaultModel = "gpt-4o-mini",
        supportsOpenAiModelsEndpoint = true
    )
}

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolCalls: List<ToolCallRequest>? = null
)

@Serializable
data class ToolDefinitionDto(
    val name: String,
    val description: String,
    val parametersSchemaJson: String
)

@Serializable
data class ToolCallRequest(
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class GatewayRequest(
    val messages: List<ChatMessageDto>,
    val preferredProviderId: String? = null,
    val preferredModelId: String? = null,
    val tools: List<ToolDefinitionDto> = emptyList(),
    val temperature: Double = 0.4,
    val maxTokens: Int = 2048,
    val stream: Boolean = true
)

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

data class GatewayResponse(
    val providerId: String,
    val modelId: String,
    val content: String,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val usage: TokenUsage = TokenUsage(),
    val latencyMs: Long = 0L
)

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class Completed(val response: GatewayResponse) : StreamEvent
    data class Failed(val error: GatewayException) : StreamEvent
}

data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val discoveredModelCount: Int,
    val message: String
)

class GatewayException(
    message: String,
    val httpCode: Int? = null,
    val retryable: Boolean = false,
    cause: Throwable? = null
) : Exception(message, cause)
