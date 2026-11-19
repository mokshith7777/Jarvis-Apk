package com.mokshith.jarvis.ai.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mokshith.jarvis.ai.app.AppContainer
import com.mokshith.jarvis.ai.core.TimeUtils
import com.mokshith.jarvis.ai.memory.TaskStatus
import com.mokshith.jarvis.ai.ui.components.HudPanel
import com.mokshith.jarvis.ai.ui.components.SectionHeader
import com.mokshith.jarvis.ai.ui.components.StatusPill
import com.mokshith.jarvis.ai.ui.navigation.JarvisRoute
import com.mokshith.jarvis.ai.ui.theme.JarvisColors

@Composable
fun HomeScreen(
    container: AppContainer,
    onNavigate: (String) -> Unit
) {
    val providers by container.aiGateway.observeProviders().collectAsState(initial = emptyList())
    val tasks by container.taskEngine.observeAllTasks().collectAsState(initial = emptyList())
    val conversations by container.database.conversationDao().observeAll().collectAsState(initial = emptyList())
    val executions by container.database.toolExecutionDao().observeRecent(5).collectAsState(initial = emptyList())
    val settings by container.settingsStore.settingsFlow.collectAsState(initial = com.mokshith.jarvis.ai.memory.JarvisAppSettings())
    val telegramState by container.telegramConnector.runtimeState.collectAsState()
    val deviceStatus = remember { container.deviceContextProvider.snapshot() }

    val activeProvider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
    val activeTask = tasks.firstOrNull { it.status == TaskStatus.RUNNING.name || it.status == TaskStatus.QUEUED.name }
    val healthyCount = providers.count { it.lastHealthStatus == "HEALTHY" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HudPanel(accentColor = JarvisColors.CyanDim) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "COMMAND CENTER",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = JarvisColors.CyanGlow
                        )
                        Text(
                            text = if (activeProvider != null) "JARVIS Operational" else "Setup Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = JarvisColors.TextPrimary
                        )
                    }
                    StatusPill(
                        label = if (deviceStatus.isOnline) deviceStatus.networkType else "OFFLINE",
                        color = if (deviceStatus.isOnline) JarvisColors.EmeraldOk else JarvisColors.AmberWarn
                    )
                }

                Text(
                    text = if (activeProvider != null) {
                        "Active Route: ${activeProvider.displayName} • ${activeProvider.defaultModelId}"
                    } else {
                        "No AI provider configured yet. Add a Groq, Google AI, OpenRouter, Mistral, DeepSeek, or Custom key in Gateway."
                    },
                    color = JarvisColors.TextSecondary,
                    fontSize = 13.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        label = "BAT ${deviceStatus.batteryPercent}%${if (deviceStatus.isCharging) " CHG" else ""}",
                        color = if (deviceStatus.batteryPercent > 20) JarvisColors.CyanGlow else JarvisColors.CrimsonAccent
                    )
                    StatusPill(
                        label = if (settings.adaptiveBatteryMode) "ADAPTIVE" else "UNLIMITED",
                        color = JarvisColors.CyanGlow
                    )
                    StatusPill(
                        label = if (telegramState.running) "TG LIVE" else "TG OFF",
                        color = if (telegramState.running) JarvisColors.EmeraldOk else JarvisColors.TextSecondary
                    )
                }
            }
        }

        item {
            SectionHeader("Quick Actions")
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onNavigate(JarvisRoute.CHAT.route) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CyanGlow, contentColor = JarvisColors.OledBlack)
                ) { Text("Chat") }
                OutlinedButton(
                    onClick = { onNavigate(JarvisRoute.TASKS.route) },
                    modifier = Modifier.weight(1f)
                ) { Text("New Task") }
                OutlinedButton(
                    onClick = { onNavigate(JarvisRoute.MODELS.route) },
                    modifier = Modifier.weight(1f)
                ) { Text("Gateway") }
            }
        }

        item {
            HudPanel {
                SectionHeader("Gateway Health", "${providers.size} configured • $healthyCount verified healthy")
                if (providers.isEmpty()) {
                    Text("Local memory, tasks, and history remain available offline. Configure a provider for live inference.", color = JarvisColors.TextSecondary, fontSize = 13.sp)
                } else {
                    providers.take(3).forEach { p ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${p.displayName} (${p.defaultModelId})", color = JarvisColors.TextPrimary, fontSize = 13.sp)
                            Text(
                                text = if (p.lastLatencyMs > 0) "${p.lastLatencyMs}ms" else p.lastHealthStatus,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = when (p.lastHealthStatus) {
                                    "HEALTHY" -> JarvisColors.EmeraldOk
                                    "ERROR" -> JarvisColors.CrimsonAccent
                                    else -> JarvisColors.TextSecondary
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            HudPanel(modifier = Modifier.clickable { onNavigate(JarvisRoute.TASKS.route) }) {
                SectionHeader("Active Autonomous Task")
                if (activeTask == null) {
                    Text("No task currently running. Tap to queue a multi-step objective.", color = JarvisColors.TextSecondary, fontSize = 13.sp)
                } else {
                    Text(activeTask.title, fontWeight = FontWeight.SemiBold, color = JarvisColors.TextPrimary)
                    Text("${activeTask.status} • ${activeTask.progressPercent}% • Updated ${TimeUtils.formatShortTime(activeTask.updatedAt)}", color = JarvisColors.CyanGlow, fontSize = 12.sp)
                }
            }
        }

        item {
            HudPanel {
                SectionHeader("Recent Conversations")
                if (conversations.isEmpty()) {
                    Text("No saved conversations yet.", color = JarvisColors.TextSecondary, fontSize = 13.sp)
                } else {
                    conversations.take(4).forEach { conv ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigate(JarvisRoute.CHAT.route) }.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(conv.title.take(32), color = JarvisColors.TextPrimary, fontSize = 13.sp)
                            Text(TimeUtils.formatShortTime(conv.updatedAt), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = JarvisColors.TextSecondary)
                        }
                    }
                }
            }
        }

        item {
            HudPanel(modifier = Modifier.clickable { onNavigate(JarvisRoute.ACTIVITY.route) }) {
                SectionHeader("Recent Agent Tool Activity")
                if (executions.isEmpty()) {
                    Text("No tool executions logged yet.", color = JarvisColors.TextSecondary, fontSize = 13.sp)
                } else {
                    executions.forEach { ex ->
                        Text(
                            text = "[${if (ex.success) "OK" else "ERR"}] ${ex.toolName} (${ex.durationMs}ms) — ${TimeUtils.formatShortTime(ex.createdAt)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = if (ex.success) JarvisColors.TextPrimary else JarvisColors.CrimsonAccent
                        )
                    }
                }
            }
        }
    }
}
