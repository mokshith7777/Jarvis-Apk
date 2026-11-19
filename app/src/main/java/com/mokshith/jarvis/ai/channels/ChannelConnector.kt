package com.mokshith.jarvis.ai.channels

import kotlinx.coroutines.flow.StateFlow

data class ChannelRuntimeState(
    val channelId: String,
    val displayName: String,
    val running: Boolean,
    val lastPollAt: Long = 0L,
    val lastError: String? = null
)

interface ChannelConnector {
    val channelId: String
    val displayName: String
    val runtimeState: StateFlow<ChannelRuntimeState>

    suspend fun start()
    suspend fun stop()
    suspend fun sendOutbound(targetId: String, text: String): Result<Unit>
}

/**
 * Central Communication Hub decoupling JARVIS Agent Engine from individual channels
 * (Telegram first; extensible to Discord, WhatsApp, Email, Slack, or custom connectors).
 */
class CommunicationHub {
    private val connectors = linkedMapOf<String, ChannelConnector>()

    fun register(connector: ChannelConnector) {
        connectors[connector.channelId] = connector
    }

    fun allConnectors(): List<ChannelConnector> = connectors.values.toList()
    fun getConnector(channelId: String): ChannelConnector? = connectors[channelId]
}
