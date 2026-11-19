package com.mokshith.jarvis.ai.tools

import com.mokshith.jarvis.ai.memory.TaskDao
import com.mokshith.jarvis.ai.memory.TaskEntity
import com.mokshith.jarvis.ai.memory.TaskStatus
import com.mokshith.jarvis.ai.security.ToolSensitivity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class TaskTool(
    private val taskDao: TaskDao
) : JarvisTool {

    override val id = "tasks"
    override val name = "Task Manager"
    override val description = "Create, list, update, pause, cancel, or inspect persistent JARVIS autonomous tasks."
    override val requiredPermissions = emptyList<String>()
    override val sensitivity = ToolSensitivity.LOW
    override val parametersSchemaJson = """
        {
          "type": "object",
          "properties": {
            "action": { "type": "string", "enum": ["create", "list", "update_status"] },
            "title": { "type": "string" },
            "objective": { "type": "string" },
            "priority": { "type": "integer", "minimum": 1, "maximum": 10 },
            "task_id": { "type": "string" },
            "status": { "type": "string", "enum": ["QUEUED", "PAUSED", "CANCELLED", "COMPLETED"] }
          },
          "required": ["action"]
        }
    """.trimIndent()

    override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
        val action = arguments["action"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "list"
        val now = System.currentTimeMillis()
        return when (action) {
            "create" -> {
                val title = arguments["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val objective = arguments["objective"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (title.isBlank() || objective.isBlank()) {
                    return ToolExecutionResult(false, "Both 'title' and 'objective' are required.")
                }
                val id = UUID.randomUUID().toString()
                val priority = arguments["priority"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 10) ?: 5
                taskDao.upsert(
                    TaskEntity(
                        id = id,
                        title = title,
                        objective = objective,
                        status = TaskStatus.QUEUED.name,
                        priority = priority,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                ToolExecutionResult(true, "Created task '$title' (ID: $id, status=QUEUED, priority=$priority).")
            }
            "list" -> {
                val tasks = taskDao.getPendingOrActive()
                if (tasks.isEmpty()) ToolExecutionResult(true, "No active or queued tasks.")
                else ToolExecutionResult(
                    true,
                    tasks.joinToString("\n") { "[${it.status} | ${it.progressPercent}%] ${it.title} (id=${it.id})" }
                )
            }
            "update_status" -> {
                val taskId = arguments["task_id"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolExecutionResult(false, "Missing 'task_id'.")
                val status = arguments["status"]?.jsonPrimitive?.contentOrNull?.uppercase()
                    ?: return ToolExecutionResult(false, "Missing 'status'.")
                val task = taskDao.getById(taskId) ?: return ToolExecutionResult(false, "Task not found: $taskId")
                taskDao.upsert(task.copy(status = status, updatedAt = now))
                ToolExecutionResult(true, "Updated task '${task.title}' to $status.")
            }
            else -> ToolExecutionResult(false, "Unsupported task action: $action")
        }
    }
}
