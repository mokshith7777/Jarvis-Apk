package com.mokshith.jarvis.ai.gateway

import com.mokshith.jarvis.ai.memory.ProviderConfigEntity

interface ProviderAdapter {
    suspend fun chat(
        provider: ProviderConfigEntity,
        apiKey: String,
        modelId: String,
        request: GatewayRequest,
        onDelta: (String) -> Unit
    ): GatewayResponse

    suspend fun fetchModels(
        provider: ProviderConfigEntity,
        apiKey: String
    ): List<String>
}
