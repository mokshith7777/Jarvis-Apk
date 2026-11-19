package com.mokshith.jarvis.ai.tools

import com.mokshith.jarvis.ai.security.ToolSensitivity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Fetches publicly accessible web pages or performs DuckDuckGo HTML search,
 * strips scripts/styles/boilerplate with Jsoup, and returns clean readable text + source URLs.
 */
class WebResearchTool(
    private val client: OkHttpClient
) : JarvisTool {

    override val id = "web_research"
    override val name = "Web Research"
    override val description = "Search the web or fetch and extract readable text, title, metadata, and links from a public URL."
    override val requiredPermissions = listOf("android.permission.INTERNET")
    override val sensitivity = ToolSensitivity.LOW
    override val parametersSchemaJson = """
        {
          "type": "object",
          "properties": {
            "mode": { "type": "string", "enum": ["search", "fetch"], "description": "Use 'search' for web search or 'fetch' to read a specific URL." },
            "query": { "type": "string", "description": "Search query when mode is 'search'." },
            "url": { "type": "string", "description": "Public HTTP/HTTPS URL when mode is 'fetch'." }
          },
          "required": ["mode"]
        }
    """.trimIndent()

    override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
        val mode = arguments["mode"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "search"
        return when (mode) {
            "fetch" -> {
                val url = arguments["url"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return ToolExecutionResult(false, "Missing 'url' parameter for fetch mode.")
                fetchPage(url)
            }
            else -> {
                val query = arguments["query"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return ToolExecutionResult(false, "Missing 'query' parameter for search mode.")
                searchWeb(query)
            }
        }
    }

    private fun fetchPage(url: String): ToolExecutionResult {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return ToolExecutionResult(false, "Only http:// and https:// URLs are allowed.")
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "JARVIS-Android/1.0 (+https://jarvis.local)")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                return ToolExecutionResult(
                    false,
                    "Unable to access $url (HTTP ${resp.code}). Respect site restrictions; content not retrieved."
                )
            }
            val html = resp.body?.string().orEmpty()
            val doc = Jsoup.parse(html, url)
            doc.select("script, style, noscript, iframe, svg, nav, footer").remove()
            val title = doc.title().ifBlank { "Untitled" }
            val description = doc.selectFirst("meta[name=description]")?.attr("content").orEmpty()
            val text = doc.body()?.text().orEmpty().take(5500)
            val links = doc.select("a[href]").mapNotNull { el ->
                val href = el.absUrl("href")
                val label = el.text().trim()
                if (href.startsWith("http") && label.isNotBlank()) "- $label: $href" else null
            }.distinct().take(10)

            val formatted = buildString {
                appendLine("Source URL: $url")
                appendLine("Title: $title")
                if (description.isNotBlank()) appendLine("Description: $description")
                appendLine("Extracted Content:")
                appendLine(text)
                if (links.isNotEmpty()) {
                    appendLine("Top Links:")
                    links.forEach { appendLine(it) }
                }
            }
            return ToolExecutionResult(
                success = true,
                output = formatted,
                isUntrustedExternalData = true,
                sourceLabel = url
            )
        }
    }

    private fun searchWeb(query: String): ToolExecutionResult {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://html.duckduckgo.com/html/?q=$encoded"
        val request = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) JARVIS/1.0")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                return ToolExecutionResult(false, "Web search returned HTTP ${resp.code}.")
            }
            val doc = Jsoup.parse(resp.body?.string().orEmpty(), searchUrl)
            val results = doc.select(".result").take(6).mapNotNull { item ->
                val linkEl = item.selectFirst("a.result__a") ?: return@mapNotNull null
                val title = linkEl.text().trim()
                val href = linkEl.absUrl("href").ifBlank { linkEl.attr("href") }
                val snippet = item.selectFirst(".result__snippet")?.text()?.trim().orEmpty()
                if (title.isBlank()) null else "• $title\n  URL: $href\n  Snippet: $snippet"
            }
            if (results.isEmpty()) {
                return ToolExecutionResult(true, "No public web results returned for query: $query")
            }
            return ToolExecutionResult(
                success = true,
                output = "Web search results for '$query':\n\n" + results.joinToString("\n\n"),
                isUntrustedExternalData = true,
                sourceLabel = "web_search:$query"
            )
        }
    }
}
