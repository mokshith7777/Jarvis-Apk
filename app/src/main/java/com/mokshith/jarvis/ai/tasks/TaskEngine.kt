package com.mokshith.jarvis.ai.tasks

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mokshith.jarvis.ai.brain.AgentEngine
import com.mokshith.jarvis.ai.brain.AgentEvent
import com.mokshith.jarvis.ai.memory.AppSettingsStore
import com.mokshith.jarvis.ai.memory.TaskDao
import com.mokshith.jarvis.ai.memory.TaskEntity
import com.mokshith.jarvis.ai.memory.TaskStatus
import com.mokshith.jarvis.ai.memory.TaskStepEntity
import com.mokshith.jarvis.ai.workers.TaskWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class TaskEngine(
    private val context: Context,
    private val taskDao: TaskDao,
    private val agentEngine: AgentEngine,
    private val settingsStore: AppSettingsStore
) {
    fun observeAllTasks(): Flow<List<TaskEntity>> = taskDao.observeAll()
    fun observeSteps(taskId: String): Flow<List<TaskStepEntity>> = taskDao.observeSteps(taskId)

    suspend fun enqueueTask(
        title: String,
        objective: String,
        priority: Int = 5,
        conversationId: String? = null,
        sourceChannel: String = "APP",
        telegramChatId: Long? = null
    ): TaskEntity {
        val now = System.currentTimeMillis()
        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { objective.take(48) },
            objective = objective.trim(),
            status = TaskStatus.QUEUED.name,
            priority = priority.coerceIn(1, 10),
            conversationId = conversationId,
            sourceChannel = sourceChannel,
            telegramChatId = telegramChatId,
            createdAt = now,
            updatedAt = now
        )
        taskDao.upsert(task)
        scheduleWork(task.id)
        return task
    }

    suspend fun retryOrResumeTask(taskId: String) {
        val existing = taskDao.getById(taskId) ?: return
        taskDao.upsert(
            existing.copy(
                status = TaskStatus.QUEUED.name,
                failureReason = null,
                updatedAt = System.currentTimeMillis()
            )
        )
        scheduleWork(taskId)
    }

    suspend fun pauseTask(taskId: String) {
        val existing = taskDao.getById(taskId) ?: return
        WorkManager.getInstance(context).cancelUniqueWork(workName(taskId))
        taskDao.upsert(existing.copy(status = TaskStatus.PAUSED.name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun cancelTask(taskId: String) {
        val existing = taskDao.getById(taskId) ?: return
        WorkManager.getInstance(context).cancelUniqueWork(workName(taskId))
        taskDao.upsert(existing.copy(status = TaskStatus.CANCELLED.name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun executeTaskNow(taskId: String): Result<String> {
        val task = taskDao.getById(taskId) ?: return Result.failure(IllegalArgumentException("Task not found"))
        if (task.status == TaskStatus.CANCELLED.name) return Result.success("Cancelled")

        val settings = settingsStore.settingsFlow.first()
        var current = task.copy(
            status = TaskStatus.RUNNING.name,
            progressPercent = 10,
            failureReason = null,
            updatedAt = System.currentTimeMillis()
        )
        taskDao.upsert(current)

        val stepLog = mutableListOf<String>()
        var stepCounter = current.currentStepIndex

        return try {
            val objectiveWithCheckpoint = if (!current.checkpointJson.isNullOrBlank()) {
                "${current.objective}\n\nResuming from saved checkpoint:\n${current.checkpointJson}"
            } else {
                current.objective
            }

            val result = agentEngine.run(
                objective = objectiveWithCheckpoint,
                conversationId = current.conversationId,
                taskId = current.id,
                maxSteps = settings.maxAgentSteps,
                injectMemory = settings.memoryAutoInjectionEnabled,
                onEvent = { event ->
                    when (event) {
                        is AgentEvent.ToolStarted -> {
                            stepCounter++
                            stepLog.add("Step $stepCounter: ${event.toolName}(${event.argumentsJson.take(120)})")
                        }
                        is AgentEvent.ToolFinished -> {
                            stepLog.add("Observed ${event.toolName}: ${if (event.success) "OK" else "ERR"} - ${event.outputPreview.take(160)}")
                        }
                        else -> Unit
                    }
                }
            )

            stepLog.forEachIndexed { idx, desc ->
                taskDao.upsertStep(
                    TaskStepEntity(
                        id = "${current.id}_step_$idx",
                        taskId = current.id,
                        stepIndex = idx + 1,
                        description = desc,
                        status = "COMPLETED",
                        startedAt = current.updatedAt,
                        finishedAt = System.currentTimeMillis()
                    )
                )
            }

            val completed = current.copy(
                status = TaskStatus.COMPLETED.name,
                currentStepIndex = result.stepsExecuted,
                totalSteps = result.stepsExecuted,
                progressPercent = 100,
                checkpointJson = stepLog.joinToString("\n").take(2000),
                completionSummary = result.finalText,
                updatedAt = System.currentTimeMillis()
            )
            taskDao.upsert(completed)
            Result.success(result.finalText)
        } catch (ce: CancellationException) {
            taskDao.upsert(
                current.copy(
                    status = TaskStatus.PAUSED.name,
                    checkpointJson = stepLog.joinToString("\n").take(2000),
                    updatedAt = System.currentTimeMillis()
                )
            )
            throw ce
        } catch (t: Throwable) {
            taskDao.upsert(
                current.copy(
                    status = TaskStatus.FAILED.name,
                    checkpointJson = stepLog.joinToString("\n").take(2000),
                    failureReason = t.message ?: "Task execution error",
                    updatedAt = System.currentTimeMillis()
                )
            )
            Result.failure(t)
        }
    }

    private suspend fun scheduleWork(taskId: String) {
        val settings = settingsStore.settingsFlow.first()
        val netType = if (settings.wifiOnlyBackgroundTasks) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(netType)
            .setRequiresBatteryNotLow(settings.adaptiveBatteryMode)
            .build()

        val req = OneTimeWorkRequestBuilder<TaskWorker>()
            .setConstraints(constraints)
            .setInputData(Data.Builder().putString(TaskWorker.KEY_TASK_ID, taskId).build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(taskId),
            ExistingWorkPolicy.REPLACE,
            req
        )
    }

    private fun workName(taskId: String) = "jarvis_task_$taskId"
}
