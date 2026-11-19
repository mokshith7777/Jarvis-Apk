package com.mokshith.jarvis.ai.app

import android.content.Context
import com.mokshith.jarvis.ai.brain.AgentEngine
import com.mokshith.jarvis.ai.brain.SkillManager
import com.mokshith.jarvis.ai.channels.CommunicationHub
import com.mokshith.jarvis.ai.channels.TelegramChannelConnector
import com.mokshith.jarvis.ai.context.DeviceContextProvider
import com.mokshith.jarvis.ai.context.SafFileAccessManager
import com.mokshith.jarvis.ai.core.DefaultJarvisDispatchers
import com.mokshith.jarvis.ai.core.JarvisDispatchers
import com.mokshith.jarvis.ai.gateway.AiGateway
import com.mokshith.jarvis.ai.gateway.GoogleAiAdapter
import com.mokshith.jarvis.ai.gateway.OpenAiCompatibleAdapter
import com.mokshith.jarvis.ai.memory.AppSettingsStore
import com.mokshith.jarvis.ai.memory.JarvisDatabase
import com.mokshith.jarvis.ai.memory.MemoryRetrievalPipeline
import com.mokshith.jarvis.ai.network.HttpClientFactory
import com.mokshith.jarvis.ai.security.CredentialVault
import com.mokshith.jarvis.ai.security.KeystoreCredentialVault
import com.mokshith.jarvis.ai.security.PermissionPolicyManager
import com.mokshith.jarvis.ai.tasks.TaskEngine
import com.mokshith.jarvis.ai.tools.DeviceContextTool
import com.mokshith.jarvis.ai.tools.FileSafTool
import com.mokshith.jarvis.ai.tools.GitHubTool
import com.mokshith.jarvis.ai.tools.MemoryTool
import com.mokshith.jarvis.ai.tools.TaskTool
import com.mokshith.jarvis.ai.tools.ToolRegistry
import com.mokshith.jarvis.ai.tools.WebResearchTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Clean manual dependency injection container avoiding circular dependencies.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val dispatchers: JarvisDispatchers = DefaultJarvisDispatchers()
    val appScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    // Security & Settings
    val credentialVault: CredentialVault = KeystoreCredentialVault(appContext)
    val permissionPolicyManager = PermissionPolicyManager(appContext)
    val settingsStore = AppSettingsStore(appContext)

    // Persistence (Room + FTS4)
    val database: JarvisDatabase = JarvisDatabase.build(appContext)
    val memoryPipeline = MemoryRetrievalPipeline(database.memoryDao())

    // Networking & Multi-Provider Gateway
    val standardHttpClient = HttpClientFactory.createStandardClient()
    val streamingHttpClient = HttpClientFactory.createStreamingClient()
    val longPollHttpClient = HttpClientFactory.createLongPollClient()

    val aiGateway = AiGateway(
        providerDao = database.providerDao(),
        credentialVault = credentialVault,
        openAiAdapter = OpenAiCompatibleAdapter(streamingHttpClient),
        googleAiAdapter = GoogleAiAdapter(streamingHttpClient),
        dispatchers = dispatchers
    )

    // Device Context & Native Tools
    val deviceContextProvider = DeviceContextProvider(appContext)
    val safFileAccessManager = SafFileAccessManager(appContext)

    val toolRegistry = ToolRegistry(
        permissionPolicyManager = permissionPolicyManager,
        toolExecutionDao = database.toolExecutionDao(),
        credentialVault = credentialVault
    ).apply {
        register(WebResearchTool(standardHttpClient))
        register(MemoryTool(database.memoryDao(), memoryPipeline))
        register(FileSafTool(safFileAccessManager, settingsStore))
        register(GitHubTool(standardHttpClient, credentialVault))
        register(TaskTool(database.taskDao()))
        register(DeviceContextTool(deviceContextProvider))
    }

    // Brain & Skills
    val skillManager = SkillManager(database.skillDao())
    val agentEngine = AgentEngine(
        aiGateway = aiGateway,
        toolRegistry = toolRegistry,
        memoryPipeline = memoryPipeline,
        skillManager = skillManager
    )

    // Task Engine
    val taskEngine = TaskEngine(
        context = appContext,
        taskDao = database.taskDao(),
        agentEngine = agentEngine,
        settingsStore = settingsStore
    )

    // Communication Hub & Telegram Channel
    val telegramConnector = TelegramChannelConnector(
        httpClient = longPollHttpClient,
        credentialVault = credentialVault,
        channelDao = database.channelDao(),
        conversationDao = database.conversationDao(),
        agentEngine = agentEngine,
        taskEngine = taskEngine,
        dispatchers = dispatchers
    )

    val communicationHub = CommunicationHub().apply {
        register(telegramConnector)
    }

    init {
        appScope.launch {
            skillManager.ensureStarterSkills()
        }
    }
}
