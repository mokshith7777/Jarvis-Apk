package com.mokshith.jarvis.ai.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.mokshith.jarvis.ai.ui.theme.JarvisColors
import kotlinx.coroutines.launch

@Composable
fun TasksScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val tasks by container.taskEngine.observeAllTasks().collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var objective by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HudPanel(accentColor = JarvisColors.CyanDim) {
                SectionHeader("Queue Autonomous Task", "Persisted in Room & executed via WorkManager")
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = objective,
                    onValueChange = { objective = it },
                    label = { Text("Objective (JARVIS will plan, call tools & checkpoint)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Button(
                    onClick = {
                        if (objective.isNotBlank()) {
                            scope.launch {
                                container.taskEngine.enqueueTask(title, objective)
                                title = ""
                                objective = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CyanGlow, contentColor = JarvisColors.OledBlack)
                ) { Text("Queue Task") }
            }
        }

        if (tasks.isEmpty()) {
            item {
                HudPanel {
                    Text("No tasks yet. Queue an objective above or promote a chat prompt into a background task.", color = JarvisColors.TextSecondary)
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                val statusColor = when (task.status) {
                    TaskStatus.COMPLETED.name -> JarvisColors.EmeraldOk
                    TaskStatus.RUNNING.name -> JarvisColors.CyanGlow
                    TaskStatus.FAILED.name -> JarvisColors.CrimsonAccent
                    TaskStatus.CANCELLED.name -> JarvisColors.TextSecondary
                    else -> JarvisColors.AmberWarn
                }
                HudPanel(accentColor = statusColor.copy(alpha = 0.5f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(task.title, fontWeight = FontWeight.Bold, color = JarvisColors.TextPrimary, modifier = Modifier.weight(1f))
                        StatusPill(task.status, statusColor)
                    }
                    Text(task.objective, fontSize = 13.sp, color = JarvisColors.TextSecondary)
                    LinearProgressIndicator(
                        progress = { (task.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = statusColor
                    )
                    Text(
                        text = "Steps: ${task.currentStepIndex} • Channel: ${task.sourceChannel} • Updated ${TimeUtils.formatTimestamp(task.updatedAt)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = JarvisColors.TextSecondary
                    )
                    if (!task.checkpointJson.isNullOrBlank()) {
                        Text(
                            text = "Execution Log:\n${task.checkpointJson}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = JarvisColors.TextSecondary
                        )
                    }
                    if (!task.completionSummary.isNullOrBlank()) {
                        Text("Result: ${task.completionSummary}", fontSize = 13.sp, color = JarvisColors.TextPrimary)
                    }
                    if (!task.failureReason.isNullOrBlank()) {
                        Text("Failure: ${task.failureReason}", fontSize = 12.sp, color = JarvisColors.CrimsonAccent)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (task.status == TaskStatus.FAILED.name || task.status == TaskStatus.PAUSED.name || task.status == TaskStatus.CANCELLED.name) {
                            OutlinedButton(onClick = { scope.launch { container.taskEngine.retryOrResumeTask(task.id) } }) {
                                Text(if (task.status == TaskStatus.PAUSED.name) "Resume" else "Retry")
                            }
                        }
                        if (task.status == TaskStatus.RUNNING.name || task.status == TaskStatus.QUEUED.name) {
                            OutlinedButton(onClick = { scope.launch { container.taskEngine.pauseTask(task.id) } }) {
                                Text("Pause")
                            }
                            OutlinedButton(onClick = { scope.launch { container.taskEngine.cancelTask(task.id) } }) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}
