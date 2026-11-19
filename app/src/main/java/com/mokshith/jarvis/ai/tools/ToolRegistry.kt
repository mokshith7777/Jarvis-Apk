package com.mokshith.jarvis.ai.tools

import com.mokshith.jarvis.ai.gateway.ToolDefinitionDto
import com.mokshith.jarvis.ai.memory.ToolExecutionDao
import com.mokshith.jarvis.ai.memory.ToolExecutionEntity
import com.mokshith.jarvis.ai.security.CredentialRedactor
import com.mokshith.jarvis.ai.security.CredentialVault
import com.mokshith.jarvis.ai.security.PermissionPolicyManager
import com.mokshith.jarvis.ai.security.PromptInjectionGuard
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import java.util.UUID

interface ToolApprovalHandler {
    suspend fun requestApproval(tool: JarvisTool, argumentsJson: String): Boolean
}

class ToolRegistry(
    private val permissionPolicyManager: PermissionPolicyManager,
    private val toolExecutionDao: ToolExecutionDao,
    private val credentialVault: CredentialVault
) {
    private val tools = linkedMapOf<String, JarvisTool>()

    fun register(tool: JarvisTool) {
        tools[tool.id] = tool
        permissionPolicyManager.ensureDefaultPolicy(
            toolId = tool.id,
            defaultEnabled = true,
            defaultConfirm = tool.sensitivity == com.mokshith.jarvis.ai.security.ToolSensitivity.HIGH_REQUIRES_APPROVAL
        )
    }

    fun allTools(): List<JarvisTool> = tools.values.toList()

    fun getTool(id: String): JarvisTool? = tools[id]

    fun enabledDefinitions(): List<ToolDefinitionDto> {
        return tools.values
            .filter { permissionPolicyManager.isToolEnabled(it.id) }
            .map { it.toDefinition() }
    }

    suspend fun executeTool(
        toolId: String,
        arguments: JsonObject,
        conversationId: String? = null,
        taskId: String? = null,
        approvalHandler: ToolApprovalHandler? = null
    ): ToolExecutionResult {
        val tool = tools[toolId]
            ?: return ToolExecutionResult(false, "Unknown tool: $toolId")

        if (!permissionPolicyManager.isToolEnabled(toolId)) {
            val blocked = ToolExecutionResult(false, "Tool '${tool.name}' is disabled in Settings > Tool Permissions.")
            logExecution(tool, arguments.toString(), blocked, approved = false, durationMs = 0L, taskId, conversationId)
            return blocked
        }

        val needsApproval = permissionPolicyManager.requiresConfirmation(toolId, tool.sensitivity)
        if (needsApproval) {
            val approved = approvalHandler?.requestApproval(tool, arguments.toString()) ?: false
            if (!approved) {
                val denied = ToolExecutionResult(false, "User denied permission to run '${tool.name}'.")
                logExecution(tool, arguments.toString(), denied, approved = false, durationMs = 0L, taskId, conversationId)
                return denied
            }
        }

        val start = System.currentTimeMillis()
        val rawResult = try {
            tool.execute(arguments)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            val safeErr = CredentialRedactor.redact(t.message ?: "Tool execution error", credentialVault.allSecretValues())
            ToolExecutionResult(false, "Tool '${tool.name}' failed: $safeErr")
        }

        val duration = System.currentTimeMillis() - start
        val redactedOutput = CredentialRedactor.redact(rawResult.output, credentialVault.allSecretValues())
        val wrappedOutput = if (rawResult.isUntrustedExternalData) {
            PromptInjectionGuard.wrapUntrustedToolOutput(
                sourceName = rawResult.sourceLabel.ifBlank { tool.id },
                content = redactedOutput
            )
        } else {
            redactedOutput
        }

        val finalResult = rawResult.copy(output = wrappedOutput)
        logExecution(tool, arguments.toString(), finalResult, approved = true, durationMs = duration, taskId, conversationId)
        return finalResult
    }

    private suspend fun logExecution(
        tool: JarvisTool,
        args: String,
        result: ToolExecutionResult,
        approved: Boolean,
        durationMs: Long,
        taskId: String?,
        conversationId: String?
    ) {
        val safeArgs = CredentialRedactor.redact(args, credentialVault.allSecretValues()).take(400)
        val safeSummary = CredentialRedactor.redact(result.output, credentialVault.allSecretValues()).take(600)
        toolExecutionDao.insert(
            ToolExecutionEntity(
                id = UUID.randomUUID().toString(),
                toolId = tool.id,
                toolName = tool.name,
                argumentsSummary = safeArgs,
                resultSummary = safeSummary,
                success = result.success,
                approvedByUser = approved,
                durationMs = durationMs,
                taskId = taskId,
                conversationId = conversationId,
                createdAt = System.currentTimeMillis()
            )
        )
    }
}
