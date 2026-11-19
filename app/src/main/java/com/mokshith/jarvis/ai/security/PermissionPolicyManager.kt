package com.mokshith.jarvis.ai.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ToolSensitivity {
    LOW,
    MEDIUM,
    HIGH_REQUIRES_APPROVAL
}

data class ToolPermissionState(
    val toolId: String,
    val enabled: Boolean,
    val requireConfirmation: Boolean
)

class PermissionPolicyManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_tool_permissions_v1", Context.MODE_PRIVATE)

    private val _policies = MutableStateFlow<Map<String, ToolPermissionState>>(emptyMap())
    val policies: StateFlow<Map<String, ToolPermissionState>> = _policies.asStateFlow()

    fun ensureDefaultPolicy(toolId: String, defaultEnabled: Boolean = true, defaultConfirm: Boolean = false) {
        val enabledKey = "${toolId}_enabled"
        val confirmKey = "${toolId}_confirm"
        if (!prefs.contains(enabledKey)) {
            prefs.edit()
                .putBoolean(enabledKey, defaultEnabled)
                .putBoolean(confirmKey, defaultConfirm)
                .apply()
        }
        refreshTool(toolId, defaultEnabled, defaultConfirm)
    }

    fun isToolEnabled(toolId: String): Boolean {
        return prefs.getBoolean("${toolId}_enabled", true)
    }

    fun requiresConfirmation(toolId: String, sensitivity: ToolSensitivity): Boolean {
        if (sensitivity == ToolSensitivity.HIGH_REQUIRES_APPROVAL) return true
        return prefs.getBoolean("${toolId}_confirm", false)
    }

    fun setToolEnabled(toolId: String, enabled: Boolean) {
        prefs.edit().putBoolean("${toolId}_enabled", enabled).apply()
        refreshTool(toolId, enabled, prefs.getBoolean("${toolId}_confirm", false))
    }

    fun setRequireConfirmation(toolId: String, require: Boolean) {
        prefs.edit().putBoolean("${toolId}_confirm", require).apply()
        refreshTool(toolId, prefs.getBoolean("${toolId}_enabled", true), require)
    }

    private fun refreshTool(toolId: String, enabled: Boolean, confirm: Boolean) {
        val current = _policies.value.toMutableMap()
        current[toolId] = ToolPermissionState(toolId, enabled, confirm)
        _policies.value = current
    }
}
