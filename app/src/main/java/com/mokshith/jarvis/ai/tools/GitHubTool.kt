package com.mokshith.jarvis.ai.tools

import android.util.Base64
import com.mokshith.jarvis.ai.core.JarvisJson
import com.mokshith.jarvis.ai.security.CredentialVault
import com.mokshith.jarvis.ai.security.KeystoreCredentialVault
import com.mokshith.jarvis.ai.security.ToolSensitivity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * Real GitHub REST API v3 tool with optional encrypted Personal Access Token support
 * and explicit rate-limit handling.
 */
class GitHubTool(
    private val client: OkHttpClient,
    private val credentialVault: CredentialVault
) : JarvisTool {

    private val json = JarvisJson.instance

    override val id = "github"
    override val name = "GitHub Inspector"
    override val description = "Inspect GitHub repositories, browse directories, read files, list issues, pull requests, commits, or search repositories."
    override val requiredPermissions = listOf("android.permission.INTERNET")
    override val sensitivity = ToolSensitivity.LOW
    override val parametersSchemaJson = """
        {
          "type": "object",
          "properties": {
            "action": { "type": "string", "enum": ["repo_info", "browse", "file", "issues", "pulls", "commits", "search_repos"] },
            "owner": { "type": "string" },
            "repo": { "type": "string" },
            "path": { "type": "string", "description": "Path inside repo for browse/file." },
            "query": { "type": "string", "description": "Search query for search_repos." }
          },
          "required": ["action"]
        }
    """.trimIndent()

    override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
        val action = arguments["action"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: return ToolExecutionResult(false, "Missing action")
        val owner = arguments["owner"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val repo = arguments["repo"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val path = arguments["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().trimStart('/')
        val query = arguments["query"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

        val url = when (action) {
            "search_repos" -> {
                if (query.isBlank()) return ToolExecutionResult(false, "Missing 'query' for search_repos.")
                "https://api.github.com/search/repositories?q=${URLEncoder.encode(query, "UTF-8")}&per_page=8"
            }
            "repo_info" -> {
                if (owner.isBlank() || repo.isBlank()) return ToolExecutionResult(false, "Requires 'owner' and 'repo'.")
                "https://api.github.com/repos/$owner/$repo"
            }
            "browse", "file" -> {
                if (owner.isBlank() || repo.isBlank()) return ToolExecutionResult(false, "Requires 'owner' and 'repo'.")
                "https://api.github.com/repos/$owner/$repo/contents/$path"
            }
            "issues" -> {
                if (owner.isBlank() || repo.isBlank()) return ToolExecutionResult(false, "Requires 'owner' and 'repo'.")
                "https://api.github.com/repos/$owner/$repo/issues?per_page=10&state=open"
            }
            "pulls" -> {
                if (owner.isBlank() || repo.isBlank()) return ToolExecutionResult(false, "Requires 'owner' and 'repo'.")
                "https://api.github.com/repos/$owner/$repo/pulls?per_page=10&state=open"
            }
            "commits" -> {
                if (owner.isBlank() || repo.isBlank()) return ToolExecutionResult(false, "Requires 'owner' and 'repo'.")
                "https://api.github.com/repos/$owner/$repo/commits?per_page=10"
            }
            else -> return ToolExecutionResult(false, "Unsupported GitHub action: $action")
        }

        val token = credentialVault.getSecret(KeystoreCredentialVault.KEY_GITHUB_PAT)
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "JARVIS-Android/1.0")
        if (!token.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $token")
        }

        client.newCall(reqBuilder.build()).execute().use { resp ->
            val remaining = resp.header("X-RateLimit-Remaining")
            if (resp.code == 403 && remaining == "0") {
                return ToolExecutionResult(false, "GitHub API rate limit exceeded. Configure a Personal Access Token in Settings > GitHub.")
            }
            if (!resp.isSuccessful) {
                return ToolExecutionResult(false, "GitHub API error HTTP ${resp.code}: ${resp.body?.string().orEmpty().take(300)}")
            }
            val raw = resp.body?.string().orEmpty()
            val formatted = formatResponse(action, raw)
            return ToolExecutionResult(true, formatted, isUntrustedExternalData = true, sourceLabel = url)
        }
    }

    private fun formatResponse(action: String, raw: String): String {
        val el = json.parseToJsonElement(raw)
        return when (action) {
            "repo_info" -> {
                val obj = el.jsonObject
                buildString {
                    appendLine("Repo: ${obj["full_name"]?.jsonPrimitive?.contentOrNull}")
                    appendLine("Description: ${obj["description"]?.jsonPrimitive?.contentOrNull ?: "None"}")
                    appendLine("Language: ${obj["language"]?.jsonPrimitive?.contentOrNull ?: "Unknown"}")
                    appendLine("Stars: ${obj["stargazers_count"]?.jsonPrimitive?.intOrNull ?: 0} | Forks: ${obj["forks_count"]?.jsonPrimitive?.intOrNull ?: 0} | Open Issues: ${obj["open_issues_count"]?.jsonPrimitive?.intOrNull ?: 0}")
                    appendLine("Default Branch: ${obj["default_branch"]?.jsonPrimitive?.contentOrNull}")
                    appendLine("URL: ${obj["html_url"]?.jsonPrimitive?.contentOrNull}")
                }
            }
            "browse" -> {
                el.jsonArray.joinToString("\n") { item ->
                    val o = item.jsonObject
                    "[${o["type"]?.jsonPrimitive?.contentOrNull}] ${o["path"]?.jsonPrimitive?.contentOrNull}"
                }
            }
            "file" -> {
                val obj = el.jsonObject
                val encoded = obj["content"]?.jsonPrimitive?.contentOrNull?.replace("\n", "") ?: ""
                val decoded = runCatching { String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(encoded)
                "Path: ${obj["path"]?.jsonPrimitive?.contentOrNull}\n\n${decoded.take(6000)}"
            }
            "issues", "pulls" -> {
                el.jsonArray.joinToString("\n") { item ->
                    val o = item.jsonObject
                    "#${o["number"]?.jsonPrimitive?.intOrNull} ${o["title"]?.jsonPrimitive?.contentOrNull} (${o["state"]?.jsonPrimitive?.contentOrNull}) - ${o["html_url"]?.jsonPrimitive?.contentOrNull}"
                }.ifBlank { "No open items found." }
            }
            "commits" -> {
                el.jsonArray.joinToString("\n") { item ->
                    val o = item.jsonObject
                    val sha = o["sha"]?.jsonPrimitive?.contentOrNull?.take(7) ?: ""
                    val msg = o["commit"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull?.lineSequence()?.firstOrNull().orEmpty()
                    "$sha - $msg"
                }
            }
            "search_repos" -> {
                el.jsonObject["items"]?.jsonArray?.joinToString("\n") { item ->
                    val o = item.jsonObject
                    "${o["full_name"]?.jsonPrimitive?.contentOrNull} (★${o["stargazers_count"]?.jsonPrimitive?.intOrNull ?: 0}) - ${o["description"]?.jsonPrimitive?.contentOrNull ?: ""}"
                }.orEmpty()
            }
            else -> raw.take(4000)
        }
    }
}
