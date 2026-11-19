package com.mokshith.jarvis.ai.tools

import com.mokshith.jarvis.ai.gateway.ToolDefinitionDto
import com.mokshith.jarvis.ai.security.ToolSensitivity
import kotlinx.serialization.json.JsonObject

data class ToolExecutionResult(
    val success: Boolean,
    val output: String,
    val isUntrustedExternalData: Boolean = false,
    val sourceLabel: String = ""
)

interface JarvisTool {
    val id: String
    val name: String
    val description: String
    val parametersSchemaJson: String
    val requiredPermissions: List<String>
    val sensitivity: ToolSensitivity

    suspend fun execute(arguments: JsonObject): ToolExecutionResult

    fun toDefinition(): ToolDefinitionDto = ToolDefinitionDto(
        name = id,
        description = description,
        parametersSchemaJson = parametersSchemaJson
    )
}
