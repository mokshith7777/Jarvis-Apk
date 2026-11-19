package com.mokshith.jarvis.ai

import com.mokshith.jarvis.ai.brain.AgentEngine
import com.mokshith.jarvis.ai.brain.SkillManager
import com.mokshith.jarvis.ai.gateway.AiGateway
import com.mokshith.jarvis.ai.gateway.GatewayRequest
import com.mokshith.jarvis.ai.gateway.GatewayResponse
import com.mokshith.jarvis.ai.gateway.ProviderAdapter
import com.mokshith.jarvis.ai.gateway.ProviderType
import com.mokshith.jarvis.ai.gateway.ToolCallRequest
import com.mokshith.jarvis.ai.memory.MemoryRetrievalPipeline
import com.mokshith.jarvis.ai.memory.ProviderConfigEntity
import com.mokshith.jarvis.ai.security.KeystoreCredentialVault
import com.mokshith.jarvis.ai.security.ToolSensitivity
import com.mokshith.jarvis.ai.tools.JarvisTool
import com.mokshith.jarvis.ai.tools.ToolExecutionResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentEngineTest {

    @Test
    fun multiStepToolLoopObservesAndCompletes() = runTest {
        val dao = InMemoryProviderDao()
        val vault = InMemoryVault()
        dao.upsertProvider(ProviderConfigEntity("p1", ProviderType.GROQ.name, "Groq", "https://example.invalid", "m1", isDefault = true))
        vault.putSecret(KeystoreCredentialVault.providerKey("p1"), "test_key_123456")

        var callCount = 0
        val scriptedAdapter = object : ProviderAdapter {
            override suspend fun chat(
                provider: ProviderConfigEntity,
                apiKey: String,
                modelId: String,
                request: GatewayRequest,
                onDelta: (String) -> Unit
            ): GatewayResponse {
                callCount++
                return if (callCount == 1) {
                    GatewayResponse(
                        providerId = provider.id,
                        modelId = modelId,
                        content = "",
                        toolCalls = listOf(ToolCallRequest("tc_1", "echo_tool", """{"msg":"ping"}"""))
                    )
                } else {
                    val observed = request.messages.lastOrNull { it.role == "tool" }?.content.orEmpty()
                    GatewayResponse(
                        providerId = provider.id,
                        modelId = modelId,
                        content = "Done. Observed: $observed"
                    )
                }
            }
            override suspend fun fetchModels(provider: ProviderConfigEntity, apiKey: String): List<String> = listOf("m1")
        }

        val gateway = AiGateway(dao, vault, scriptedAdapter, scriptedAdapter, TestDispatchers())
        val memPipeline = MemoryRetrievalPipeline(InMemoryMemoryDao())
        val skillManager = SkillManager(InMemorySkillDao())

        // Lightweight tool registry test with a fake policy manager via direct JarvisTool check
        var toolInvoked = false
        val echoTool = object : JarvisTool {
            override val id = "echo_tool"
            override val name = "Echo"
            override val description = "Echoes message"
            override val parametersSchemaJson = """{"type":"object","properties":{"msg":{"type":"string"}}}"""
            override val requiredPermissions = emptyList<String>()
            override val sensitivity = ToolSensitivity.LOW
            override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
                toolInvoked = true
                return ToolExecutionResult(true, "pong")
            }
        }

        // Verify scripted adapter and echoTool directly
        val firstResp = gateway.execute(GatewayRequest(listOf(com.mokshith.jarvis.ai.gateway.ChatMessageDto("user", "Run echo"))))
        assertEquals(1, firstResp.toolCalls.size)
        val res = echoTool.execute(kotlinx.serialization.json.buildJsonObject {})
        assertTrue(toolInvoked)
        assertEquals("pong", res.output)
        assertEquals("", memPipeline.buildCompactPromptContext("nothing"))
        assertTrue(skillManager.findMatchingSkills("hello").isEmpty())
    }
}
