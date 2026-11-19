package com.mokshith.jarvis.ai.tools

import com.mokshith.jarvis.ai.context.DeviceContextProvider
import com.mokshith.jarvis.ai.security.ToolSensitivity
import kotlinx.serialization.json.JsonObject

class DeviceContextTool(
    private val deviceContextProvider: DeviceContextProvider
) : JarvisTool {

    override val id = "device_context"
    override val name = "Device Context"
    override val description = "Get current local date, time, timezone, battery percentage, charging state, and network connectivity."
    override val requiredPermissions = listOf("android.permission.ACCESS_NETWORK_STATE")
    override val sensitivity = ToolSensitivity.LOW
    override val parametersSchemaJson = """
        {
          "type": "object",
          "properties": {}
        }
    """.trimIndent()

    override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
        val snap = deviceContextProvider.snapshot()
        val out = buildString {
            appendLine("Date: ${snap.localDate}")
            appendLine("Time: ${snap.localTime} (${snap.timezone})")
            appendLine("Battery: ${if (snap.batteryPercent >= 0) "${snap.batteryPercent}%" else "Unknown"} (Charging: ${snap.isCharging})")
            appendLine("Network: ${if (snap.isOnline) "Online (${snap.networkType})" else "Offline"}")
        }
        return ToolExecutionResult(true, out.trim())
    }
}
