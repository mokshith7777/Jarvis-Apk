package com.mokshith.jarvis.ai.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mokshith.jarvis.ai.app.AppContainer
import com.mokshith.jarvis.ai.brain.AgentEvent
import com.mokshith.jarvis.ai.gateway.ChatMessageDto
import com.mokshith.jarvis.ai.memory.ConversationEntity
import com.mokshith.jarvis.ai.memory.MessageEntity
import com.mokshith.jarvis.ai.tools.JarvisTool
import com.mokshith.jarvis.ai.tools.ToolApprovalHandler
import com.mokshith.jarvis.ai.ui.components.HudPanel
import com.mokshith.jarvis.ai.ui.components.MarkdownTextBlock
import com.mokshith.jarvis.ai.ui.components.StatusPill
import com.mokshith.jarvis.ai.ui.navigation.JarvisRoute
import com.mokshith.jarvis.ai.ui.theme.JarvisColors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class LiveToolCard(
    val step: Int,
    val toolName: String,
    val arguments: String,
    val finished: Boolean = false,
    val success: Boolean = true,
    val outputPreview: String = ""
)

data class PendingApproval(
    val toolName: String,
    val argumentsJson: String,
    val deferred: CompletableDeferred<Boolean>
)

@Composable
fun ChatScreen(
    container: AppContainer,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val conversations by container.database.conversationDao().observeAll().collectAsState(initial = emptyList())
    val providers by container.aiGateway.observeProviders().collectAsState(initial = emptyList())

    var activeConversationId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(conversations) {
        if (activeConversationId == null && conversations.isNotEmpty()) {
            activeConversationId = conversations.first().id
        }
    }

    val messages by remember(activeConversationId) {
        val cid = activeConversationId
        if (cid == null) kotlinx.coroutines.flow.flowOf(emptyList())
        else container.database.conversationDao().observeMessages(cid)
    }.collectAsState(initial = emptyList())

    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var selectedModelId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(providers) {
        if (selectedProviderId == null && providers.isNotEmpty()) {
            val def = providers.firstOrNull { it.isDefault } ?: providers.first()
            selectedProviderId = def.id
            selectedModelId = def.defaultModelId
        }
    }

    var inputText by remember { mutableStateOf("") }
    var streamingText by remember { mutableStateOf("") }
    var agentPhase by remember { mutableStateOf<String?>(null) }
    var errorBanner by remember { mutableStateOf<String?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    val liveTools = remember { mutableStateListOf<LiveToolCard>() }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var pendingApproval by remember { mutableStateOf<PendingApproval?>(null) }

    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId } ?: providers.firstOrNull()
    val availableModels = remember(selectedProvider) {
        if (selectedProvider == null) emptyList()
        else (container.aiGateway.decodeModels(selectedProvider.cachedModelsJson) + selectedProvider.defaultModelId)
            .filter { it.isNotBlank() }.distinct()
    }

    fun startNewConversation() {
        scope.launch {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            container.database.conversationDao().upsert(
                ConversationEntity(
                    id = id,
                    title = "New Session",
                    providerId = selectedProvider?.id,
                    modelId = selectedModelId ?: selectedProvider?.defaultModelId,
                    createdAt = now,
                    updatedAt = now
                )
            )
            activeConversationId = id
            liveTools.clear()
            errorBanner = null
        }
    }

    fun sendPrompt(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isEmpty() || activeJob?.isActive == true) return
        errorBanner = null
        streamingText = ""
        liveTools.clear()

        activeJob = scope.launch {
            val now = System.currentTimeMillis()
            val convId = activeConversationId ?: UUID.randomUUID().toString().also { newId ->
                container.database.conversationDao().upsert(
                    ConversationEntity(
                        id = newId,
                        title = trimmed.take(40),
                        providerId = selectedProvider?.id,
                        modelId = selectedModelId ?: selectedProvider?.defaultModelId,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                activeConversationId = newId
            }

            val existingConv = container.database.conversationDao().getById(convId)
            if (existingConv != null && existingConv.title == "New Session") {
                container.database.conversationDao().upsert(existingConv.copy(title = trimmed.take(40), updatedAt = now))
            }

            container.database.conversationDao().insertMessage(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = convId,
                    role = "user",
                    content = trimmed,
                    createdAt = now
                )
            )

            val historyDtos = container.database.conversationDao().getMessages(convId).map {
                ChatMessageDto(role = it.role, content = it.content, toolCallId = it.toolCallId, toolName = it.toolName)
            }
            val settings = container.settingsStore.settingsFlow.first()

            val approvalHandler = object : ToolApprovalHandler {
                override suspend fun requestApproval(tool: JarvisTool, argumentsJson: String): Boolean {
                    val def = CompletableDeferred<Boolean>()
                    pendingApproval = PendingApproval(tool.name, argumentsJson, def)
                    return try {
                        def.await()
                    } finally {
                        pendingApproval = null
                    }
                }
            }

            runCatching {
                container.agentEngine.run(
                    objective = trimmed,
                    history = historyDtos,
                    preferredProviderId = selectedProvider?.id,
                    preferredModelId = selectedModelId ?: selectedProvider?.defaultModelId,
                    conversationId = convId,
                    maxSteps = settings.maxAgentSteps,
                    injectMemory = settings.memoryAutoInjectionEnabled,
                    approvalHandler = approvalHandler,
                    onEvent = { event ->
                        when (event) {
                            is AgentEvent.PhaseChanged -> agentPhase = "${event.phase}: ${event.detail}"
                            is AgentEvent.TextDelta -> streamingText += event.delta
                            is AgentEvent.ToolStarted -> {
                                liveTools.add(LiveToolCard(event.step, event.toolName, event.argumentsJson))
                            }
                            is AgentEvent.ToolFinished -> {
                                val idx = liveTools.indexOfLast { it.toolName == event.toolName && !it.finished }
                                if (idx >= 0) {
                                    liveTools[idx] = liveTools[idx].copy(
                                        finished = true,
                                        success = event.success,
                                        outputPreview = event.outputPreview
                                    )
                                }
                            }
                            else -> Unit
                        }
                    }
                )
            }.onSuccess { result ->
                container.database.conversationDao().insertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = "assistant",
                        content = result.finalText,
                        providerId = result.providerId,
                        modelId = result.modelId,
                        latencyMs = result.totalLatencyMs,
                        tokensUsed = result.totalTokens,
                        createdAt = System.currentTimeMillis()
                    )
                )
                container.database.conversationDao().getById(convId)?.let {
                    container.database.conversationDao().upsert(it.copy(updatedAt = System.currentTimeMillis()))
                }
                streamingText = ""
                agentPhase = null
            }.onFailure { err ->
                if (err !is kotlinx.coroutines.CancellationException) {
                    errorBanner = err.message ?: "Request failed"
                }
                streamingText = ""
                agentPhase = null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Top session & model/provider switcher bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = { showHistoryDialog = true }) {
                Icon(Icons.Default.History, contentDescription = "Threads", tint = JarvisColors.CyanGlow)
            }
            IconButton(onClick = { startNewConversation() }) {
                Icon(Icons.Default.Add, contentDescription = "New Chat", tint = JarvisColors.CyanGlow)
            }

            // Provider dropdown
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = JarvisColors.CharcoalElevated,
                border = BorderStroke(1.dp, JarvisColors.PanelBorder),
                modifier = Modifier.weight(1f).clickable { showProviderMenu = true }
            ) {
                Text(
                    text = selectedProvider?.displayName ?: "Add Provider",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = JarvisColors.TextPrimary
                )
                DropdownMenu(expanded = showProviderMenu, onDismissRequest = { showProviderMenu = false }) {
                    if (providers.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Configure in Gateway") },
                            onClick = { showProviderMenu = false; onNavigate(JarvisRoute.MODELS.route) }
                        )
                    } else {
                        providers.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.displayName) },
                                onClick = {
                                    selectedProviderId = p.id
                                    selectedModelId = p.defaultModelId
                                    showProviderMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Model dropdown
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = JarvisColors.CharcoalElevated,
                border = BorderStroke(1.dp, JarvisColors.PanelBorder),
                modifier = Modifier.weight(1f).clickable { showModelMenu = true }
            ) {
                Text(
                    text = (selectedModelId ?: "Model").take(20),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    color = JarvisColors.CyanGlow
                )
                DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                    availableModels.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                            onClick = { selectedModelId = m; showModelMenu = false }
                        )
                    }
                }
            }
        }

        if (errorBanner != null) {
            HudPanel(accentColor = JarvisColors.CrimsonAccent, modifier = Modifier.padding(vertical = 6.dp)) {
                Text(errorBanner.orEmpty(), color = JarvisColors.CrimsonAccent, fontSize = 12.sp)
            }
        }

        // Messages + live tool cards + streaming preview
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                HudPanel(
                    accentColor = if (isUser) JarvisColors.CyanDim else JarvisColors.PanelBorder
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isUser) "YOU" else "JARVIS • ${msg.modelId ?: ""}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (isUser) JarvisColors.CyanGlow else JarvisColors.TextSecondary
                        )
                        Row {
                            IconButton(onClick = { clipboard.setText(AnnotatedString(msg.content)) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = JarvisColors.TextSecondary)
                            }
                            if (isUser) {
                                IconButton(onClick = {
                                    scope.launch {
                                        container.taskEngine.enqueueTask(
                                            title = msg.content.take(44),
                                            objective = msg.content,
                                            conversationId = activeConversationId
                                        )
                                        onNavigate(JarvisRoute.TASKS.route)
                                    }
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Run as Background Task", tint = JarvisColors.CyanGlow)
                                }
                            } else if (msg == messages.lastOrNull()) {
                                val lastUser = messages.lastOrNull { it.role == "user" }
                                if (lastUser != null) {
                                    IconButton(onClick = { sendPrompt(lastUser.content) }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = JarvisColors.CyanGlow)
                                    }
                                }
                            }
                        }
                    }
                    MarkdownTextBlock(msg.content)
                    if (!isUser && msg.latencyMs > 0) {
                        Text(
                            text = "${msg.latencyMs}ms • ${msg.tokensUsed} tokens",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = JarvisColors.TextSecondary
                        )
                    }
                }
            }

            if (liveTools.isNotEmpty()) {
                items(liveTools) { card ->
                    var expanded by remember { mutableStateOf(false) }
                    HudPanel(
                        accentColor = if (card.success) JarvisColors.CyanDim else JarvisColors.CrimsonAccent,
                        modifier = Modifier.clickable { expanded = !expanded }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOOL STEP ${card.step}: ${card.toolName}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = JarvisColors.CyanGlow)
                            StatusPill(
                                label = when {
                                    !card.finished -> "RUNNING"
                                    card.success -> "OK"
                                    else -> "FAILED"
                                },
                                color = when {
                                    !card.finished -> JarvisColors.AmberWarn
                                    card.success -> JarvisColors.EmeraldOk
                                    else -> JarvisColors.CrimsonAccent
                                }
                            )
                        }
                        if (expanded) {
                            Text("Args: ${card.arguments}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = JarvisColors.TextSecondary)
                            if (card.outputPreview.isNotBlank()) {
                                Text("Output: ${card.outputPreview}", fontSize = 12.sp, color = JarvisColors.TextPrimary)
                            }
                        } else {
                            Text("Tap to inspect arguments & observation", fontSize = 11.sp, color = JarvisColors.TextSecondary)
                        }
                    }
                }
            }

            if (agentPhase != null || streamingText.isNotBlank()) {
                item {
                    HudPanel(accentColor = JarvisColors.CyanGlow) {
                        if (agentPhase != null) {
                            Text(agentPhase.orEmpty(), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = JarvisColors.CyanGlow)
                        }
                        if (streamingText.isNotBlank()) {
                            MarkdownTextBlock(streamingText)
                        }
                    }
                }
            }
        }

        // Composer row
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Command JARVIS...") },
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            if (activeJob?.isActive == true) {
                Button(
                    onClick = { activeJob?.cancel(); agentPhase = null },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CrimsonAccent)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
            } else {
                Button(
                    onClick = {
                        val toSend = inputText
                        inputText = ""
                        sendPrompt(toSend)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CyanGlow, contentColor = JarvisColors.OledBlack)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Conversation Threads") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (conversations.isEmpty()) {
                        Text("No conversations yet.")
                    } else {
                        conversations.take(12).forEach { c ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    activeConversationId = c.id
                                    showHistoryDialog = false
                                }.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(c.title.take(28), color = if (c.id == activeConversationId) JarvisColors.CyanGlow else JarvisColors.TextPrimary)
                                Text(c.channelSource, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = JarvisColors.TextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { startNewConversation(); showHistoryDialog = false }) { Text("New Thread") }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryDialog = false }) { Text("Close") }
            }
        )
    }

    pendingApproval?.let { req ->
        AlertDialog(
            onDismissRequest = { req.deferred.complete(false) },
            title = { Text("Approve Sensitive Tool Action") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("JARVIS wants to run tool: ${req.toolName}", fontWeight = FontWeight.SemiBold)
                    Text("Arguments: ${req.argumentsJson}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = { req.deferred.complete(true) }) { Text("Approve") }
            },
            dismissButton = {
                OutlinedButton(onClick = { req.deferred.complete(false) }) { Text("Deny") }
            }
        )
    }
}
