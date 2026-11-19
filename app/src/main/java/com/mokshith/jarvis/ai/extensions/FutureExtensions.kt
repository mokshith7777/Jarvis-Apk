package com.mokshith.jarvis.ai.extensions

import kotlinx.coroutines.flow.StateFlow

/**
 * Voice state machine prepared for future native wake-word, STT, and TTS modules.
 * Not active in Alpha UI until a concrete voice implementation is registered.
 */
sealed interface VoiceState {
    data object Idle : VoiceState
    data object WakeListening : VoiceState
    data object CommandListening : VoiceState
    data object Thinking : VoiceState
    data object Speaking : VoiceState
    data object Cooldown : VoiceState
    data class Paused(val reason: String) : VoiceState
}

interface VoiceModule {
    val state: StateFlow<VoiceState>
    val isAvailable: Boolean
    suspend fun startWakeListening()
    suspend fun startCommandListening()
    suspend fun speak(text: String)
    suspend fun stop()
}

interface VisionModule {
    val isAvailable: Boolean
    suspend fun analyzeImageBytes(bytes: ByteArray, mimeType: String, prompt: String): Result<String>
}

interface AccessibilityModule {
    val isServiceConnected: StateFlow<Boolean>
    suspend fun captureReadableScreenSummary(): Result<String>
}

interface LocalModelModule {
    val isAvailable: Boolean
    val loadedModelName: StateFlow<String?>
    suspend fun generateLocal(prompt: String): Result<String>
}

interface AndroidDeviceAutomationModule {
    val isAvailable: Boolean
    suspend fun performApprovedAction(actionId: String, parameters: Map<String, String>): Result<String>
}
