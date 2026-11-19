package com.mokshith.jarvis.ai

import com.mokshith.jarvis.ai.gateway.AiGateway
import com.mokshith.jarvis.ai.gateway.ChatMessageDto
import com.mokshith.jarvis.ai.gateway.GatewayRequest
import com.mokshith.jarvis.ai.gateway.GoogleAiAdapter
import com.mokshith.jarvis.ai.gateway.OpenAiCompatibleAdapter
import com.mokshith.jarvis.ai.gateway.ProviderType
import com.mokshith.jarvis.ai.memory.ProviderConfigEntity
import com.mokshith.jarvis.ai.security.KeystoreCredentialVault
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun openAiSseStreamingAndModelDiscoveryWork() = runTest {
        val sseBody = """
            data: {"choices":[{"delta":{"content":"Hello "}}]}

            data: {"choices":[{"delta":{"content":"JARVIS"}}]}

            data: {"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":4,"total_tokens":14}}

            data: [DONE]

        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(sseBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[{"id":"llama-3.3-70b-versatile"},{"id":"mixtral-8x7b"}]}"""))

        val dao = InMemoryProviderDao()
        val vault = InMemoryVault()
        val baseUrl = server.url("/openai/v1").toString().trimEnd('/')
        val config = ProviderConfigEntity(
            id = "groq_1",
            type = ProviderType.GROQ.name,
            displayName = "Groq",
            baseUrl = baseUrl,
            defaultModelId = "llama-3.3-70b-versatile",
            priority = 1,
            isDefault = true
        )
        dao.upsertProvider(config)
        vault.putSecret(KeystoreCredentialVault.providerKey("groq_1"), "gsk_test_secret_1234567890")

        val gateway = AiGateway(dao, vault, OpenAiCompatibleAdapter(client), GoogleAiAdapter(client), TestDispatchers())

        val deltas = mutableListOf<String>()
        val resp = gateway.execute(
            GatewayRequest(messages = listOf(ChatMessageDto("user", "Say hi")), stream = true),
            onDelta = { deltas.add(it) }
        )

        assertEquals("Hello JARVIS", resp.content)
        assertEquals(listOf("Hello ", "JARVIS"), deltas)
        assertEquals(14, resp.usage.totalTokens)

        val recorded = server.takeRequest()
        assertEquals("Bearer gsk_test_secret_1234567890", recorded.getHeader("Authorization"))

        val models = gateway.discoverModels("groq_1").getOrThrow()
        assertTrue(models.contains("llama-3.3-70b-versatile"))
        assertTrue(models.contains("mixtral-8x7b"))
    }

    @Test
    fun automaticFailoverTriggersOnPrimaryRateLimit() = runTest {
        // Primary fails twice with 429, fallback succeeds with 200
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))
        server.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"choices":[{"message":{"content":"Fallback succeeded"}}],"usage":{"prompt_tokens":5,"completion_tokens":3,"total_tokens":8}}"""
            )
        )

        val dao = InMemoryProviderDao()
        val vault = InMemoryVault()
        val baseUrl = server.url("/v1").toString().trimEnd('/')

        dao.upsertProvider(
            ProviderConfigEntity("primary", ProviderType.GROQ.name, "Groq", baseUrl, "m1", priority = 1, isDefault = true)
        )
        dao.upsertProvider(
            ProviderConfigEntity("fallback", ProviderType.MISTRAL.name, "Mistral", baseUrl, "m2", priority = 2, isDefault = false)
        )
        vault.putSecret(KeystoreCredentialVault.providerKey("primary"), "key_primary_123456")
        vault.putSecret(KeystoreCredentialVault.providerKey("fallback"), "key_fallback_123456")

        val gateway = AiGateway(dao, vault, OpenAiCompatibleAdapter(client), GoogleAiAdapter(client), TestDispatchers())
        val resp = gateway.execute(GatewayRequest(messages = listOf(ChatMessageDto("user", "Ping")), stream = false))

        assertEquals("fallback", resp.providerId)
        assertEquals("Fallback succeeded", resp.content)
        assertEquals(3, dao.usageLog.size)
    }

    @Test
    fun googleAiAdapterUsesHeaderAuthAndParsesSse() = runTest {
        val geminiSse = """
            data: {"candidates":[{"content":{"parts":[{"text":"Gemini "}]}}]}

            data: {"candidates":[{"content":{"parts":[{"text":"Ready"}]}}],"usageMetadata":{"promptTokenCount":8,"candidatesTokenCount":2,"totalTokenCount":10}}

        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(geminiSse))

        val adapter = GoogleAiAdapter(client)
        val provider = ProviderConfigEntity(
            id = "google_1",
            type = ProviderType.GOOGLE_AI.name,
            displayName = "Google AI",
            baseUrl = server.url("/v1beta").toString().trimEnd('/'),
            defaultModelId = "gemini-2.0-flash"
        )
        val resp = adapter.chat(provider, "AIzaTestKey123456789012345", "gemini-2.0-flash", GatewayRequest(listOf(ChatMessageDto("user", "Hi")), stream = true)) {}
        assertEquals("Gemini Ready", resp.content)
        assertEquals(10, resp.usage.totalTokens)
        val req = server.takeRequest()
        assertEquals("AIzaTestKey123456789012345", req.getHeader("x-goog-api-key"))
    }
}
