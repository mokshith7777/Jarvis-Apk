package com.mokshith.jarvis.ai.tools

import com.mokshith.jarvis.ai.memory.MemoryDao
import com.mokshith.jarvis.ai.memory.MemoryEntity
import com.mokshith.jarvis.ai.memory.MemoryRetrievalPipeline
import com.mokshith.jarvis.ai.security.ToolSensitivity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class MemoryTool(
    private val memoryDao: MemoryDao,
    private val pipeline: MemoryRetrievalPipeline
) : JarvisTool {

    override val id = "memory"
    override val name = "Persistent Memory"
    override val description = "Search, save, update, or delete explicit long-term memories in the local Room FTS4 database."
    override val requiredPermissions = emptyList<String>()
    override val sensitivity = ToolSensitivity.LOW
    override val parametersSchemaJson = """
        {
          "type": "object",
          "properties": {
            "action": { "type": "string", "enum": ["search", "save", "update", "delete"] },
            "query": { "type": "string", "description": "Search query for action='search'." },
            "id": { "type": "integer", "description": "Memory ID for update/delete." },
            "title": { "type": "string" },
            "content": { "type": "string" },
            "category": { "type": "string", "enum": ["PREFERENCE", "USER_FACT", "PROJECT", "CONVERSATION", "CHECKPOINT"] },
            "tags": { "type": "string" },
            "importance": { "type": "integer", "minimum": 1, "maximum": 10 }
          },
          "required": ["action"]
        }
    """.trimIndent()

    override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
        val action = arguments["action"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "search"
        val now = System.currentTimeMillis()
        return when (action) {
            "search" -> {
                val query = arguments["query"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val matches = pipeline.searchRelevant(query, maxResults = 8)
                if (matches.isEmpty()) {
                    ToolExecutionResult(true, "No matching memories found.")
                } else {
                    val out = matches.joinToString("\n") { (m, score) ->
                        "[#${m.id} | ${m.category} | score=${"%.2f".format(score)}] ${m.title}: ${m.content}"
                    }
                    ToolExecutionResult(true, out)
                }
            }
            "save" -> {
                val title = arguments["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val content = arguments["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (title.isBlank() || content.isBlank()) {
                    return ToolExecutionResult(false, "Both 'title' and 'content' are required to save a memory.")
                }
                val category = arguments["category"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "USER_FACT"
                val tags = arguments["tags"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val importance = arguments["importance"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 10) ?: 6
                val id = memoryDao.insert(
                    MemoryEntity(
                        title = title,
                        content = content,
                        category = category,
                        tags = tags,
                        source = "agent_tool",
                        importance = importance,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                ToolExecutionResult(true, "Saved memory #$id: $title [$category]")
            }
            "update" -> {
                val id = arguments["id"]?.jsonPrimitive?.longOrNull
                    ?: return ToolExecutionResult(false, "Missing 'id' for update.")
                val existing = memoryDao.getById(id)
                    ?: return ToolExecutionResult(false, "Memory #$id not found.")
                val updated = existing.copy(
                    title = arguments["title"]?.jsonPrimitive?.contentOrNull ?: existing.title,
                    content = arguments["content"]?.jsonPrimitive?.contentOrNull ?: existing.content,
                    category = arguments["category"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: existing.category,
                    tags = arguments["tags"]?.jsonPrimitive?.contentOrNull ?: existing.tags,
                    importance = arguments["importance"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 10) ?: existing.importance,
                    updatedAt = now
                )
                memoryDao.update(updated)
                ToolExecutionResult(true, "Updated memory #$id.")
            }
            "delete" -> {
                val id = arguments["id"]?.jsonPrimitive?.longOrNull
                    ?: return ToolExecutionResult(false, "Missing 'id' for delete.")
                memoryDao.deleteById(id)
                ToolExecutionResult(true, "Deleted memory #$id.")
            }
            else -> ToolExecutionResult(false, "Unsupported memory action: $action")
        }
    }
}
