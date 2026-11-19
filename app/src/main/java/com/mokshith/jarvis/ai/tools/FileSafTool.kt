package com.mokshith.jarvis.ai.tools

import com.mokshith.jarvis.ai.context.SafFileAccessManager
import com.mokshith.jarvis.ai.memory.AppSettingsStore
import com.mokshith.jarvis.ai.security.ToolSensitivity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class FileSafTool(
    private val safManager: SafFileAccessManager,
    private val settingsStore: AppSettingsStore
) : JarvisTool {

    override val id = "files_saf"
    override val name = "Local Files (SAF)"
    override val description = "List, read, or search text documents inside the folder explicitly granted by the user via Android Storage Access Framework."
    override val requiredPermissions = listOf("Android Storage Access Framework folder grant")
    override val sensitivity = ToolSensitivity.MEDIUM
    override val parametersSchemaJson = """
        {
          "type": "object",
          "properties": {
            "action": { "type": "string", "enum": ["list", "read", "search"] },
            "filename": { "type": "string", "description": "File name to read when action='read'." },
            "query": { "type": "string", "description": "Keyword to search across text files when action='search'." }
          },
          "required": ["action"]
        }
    """.trimIndent()

    override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
        val treeUri = settingsStore.settingsFlow.first().selectedSafTreeUri
            ?: return ToolExecutionResult(false, "No folder has been granted via Storage Access Framework. Ask the user to grant a folder in Settings > Local Files (SAF).")

        val action = arguments["action"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "list"
        val entries = safManager.listDocuments(treeUri)
        return when (action) {
            "list" -> {
                if (entries.isEmpty()) ToolExecutionResult(true, "The granted folder is empty.")
                else ToolExecutionResult(
                    true,
                    "Accessible SAF files (${entries.size}):\n" + entries.joinToString("\n") { "- ${it.displayName} (${it.mimeType})" }
                )
            }
            "read" -> {
                val filename = arguments["filename"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return ToolExecutionResult(false, "Missing 'filename' parameter.")
                val match = entries.firstOrNull { it.displayName.equals(filename, ignoreCase = true) }
                    ?: entries.firstOrNull { it.displayName.contains(filename, ignoreCase = true) }
                    ?: return ToolExecutionResult(false, "File '$filename' not found in granted folder.")
                val text = safManager.readTextFile(match.uri)
                ToolExecutionResult(
                    success = true,
                    output = "File: ${match.displayName}\n\n$text",
                    isUntrustedExternalData = true,
                    sourceLabel = "saf:${match.displayName}"
                )
            }
            "search" -> {
                val query = arguments["query"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (query.isBlank()) return ToolExecutionResult(false, "Missing 'query' parameter.")
                val hits = mutableListOf<String>()
                entries.filter { it.mimeType.startsWith("text/") || it.displayName.endsWith(".md") || it.displayName.endsWith(".kt") || it.displayName.endsWith(".json") || it.displayName.endsWith(".txt") }
                    .take(20)
                    .forEach { entry ->
                        val content = runCatching { safManager.readTextFile(entry.uri, maxChars = 8000) }.getOrNull() ?: return@forEach
                        val idx = content.indexOf(query, ignoreCase = true)
                        if (idx >= 0) {
                            val start = (idx - 80).coerceAtLeast(0)
                            val end = (idx + 160).coerceAtMost(content.length)
                            hits.add("• ${entry.displayName}: ...${content.substring(start, end).replace("\n", " ")}...")
                        }
                    }
                if (hits.isEmpty()) ToolExecutionResult(true, "No matches for '$query' in granted text files.")
                else ToolExecutionResult(true, hits.joinToString("\n"), isUntrustedExternalData = true, sourceLabel = "saf_search:$query")
            }
            else -> ToolExecutionResult(false, "Unsupported file action: $action")
        }
    }
}
