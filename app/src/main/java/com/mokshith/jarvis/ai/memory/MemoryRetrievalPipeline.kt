package com.mokshith.jarvis.ai.memory

import kotlin.math.min

data class ScoredMemory(
    val memory: MemoryEntity,
    val score: Double
)

object MemoryQuerySanitizer {
    private val tokenRegex = Regex("[A-Za-z0-9_]{2,}")

    fun buildFtsQuery(rawQuery: String): String {
        val tokens = tokenRegex.findAll(rawQuery)
            .map { it.value.lowercase() }
            .distinct()
            .take(8)
            .toList()
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" OR ") { "$it*" }
    }

    fun extractTokens(rawQuery: String): Set<String> {
        return tokenRegex.findAll(rawQuery).map { it.value.lowercase() }.toSet()
    }
}

class MemoryRetrievalPipeline(
    private val memoryDao: MemoryDao
) {
    suspend fun searchRelevant(query: String, maxResults: Int = 6): List<ScoredMemory> {
        val now = System.currentTimeMillis()
        memoryDao.purgeExpired(now)

        val candidates = linkedMapOf<Long, MemoryEntity>()
        val ftsQuery = MemoryQuerySanitizer.buildFtsQuery(query)
        if (ftsQuery.isNotBlank()) {
            runCatching { memoryDao.searchFts(ftsQuery, limit = 25, now = now) }
                .getOrDefault(emptyList())
                .forEach { candidates[it.id] = it }
        }

        if (candidates.size < maxResults) {
            val tokens = MemoryQuerySanitizer.extractTokens(query).take(3)
            for (token in tokens) {
                memoryDao.searchLike(token, limit = 10, now = now).forEach {
                    candidates[it.id] = it
                }
            }
        }

        if (candidates.size < maxResults) {
            memoryDao.getTopActive(limit = maxResults, now = now).forEach {
                candidates.putIfAbsent(it.id, it)
            }
        }

        val queryTokens = MemoryQuerySanitizer.extractTokens(query)
        return scoreMemories(candidates.values.toList(), queryTokens, now)
            .take(maxResults)
    }

    suspend fun buildCompactPromptContext(query: String, maxResults: Int = 6): String {
        val relevant = searchRelevant(query, maxResults)
        if (relevant.isEmpty()) return ""
        return buildString {
            append("Relevant persistent user memories (local Room store):\n")
            relevant.forEach { item ->
                val m = item.memory
                append("- [${m.category}] ${m.title}: ${m.content.take(260)}\n")
            }
        }.trim()
    }

    companion object {
        fun scoreMemories(
            memories: List<MemoryEntity>,
            queryTokens: Set<String>,
            nowMillis: Long = System.currentTimeMillis()
        ): List<ScoredMemory> {
            return memories.map { mem ->
                val haystack = "${mem.title} ${mem.content} ${mem.tags} ${mem.category}".lowercase()
                val matchCount = if (queryTokens.isEmpty()) 0 else queryTokens.count { haystack.contains(it) }
                val keywordRatio = if (queryTokens.isEmpty()) 0.0 else matchCount.toDouble() / queryTokens.size
                val importanceWeight = (mem.importance.coerceIn(1, 10) / 10.0) * 0.35
                val ageDays = ((nowMillis - mem.updatedAt).coerceAtLeast(0L) / 86_400_000.0)
                val recencyBonus = (1.0 / (1.0 + ageDays / 14.0)) * 0.15
                val categoryBoost = if (mem.category == "PREFERENCE" || mem.category == "PROJECT") 0.10 else 0.0
                val score = min(1.0, keywordRatio * 0.55 + importanceWeight + recencyBonus + categoryBoost)
                ScoredMemory(mem, score)
            }.sortedByDescending { it.score }
        }
    }
}
