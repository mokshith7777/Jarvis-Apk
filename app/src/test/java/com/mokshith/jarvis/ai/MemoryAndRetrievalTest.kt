package com.mokshith.jarvis.ai

import com.mokshith.jarvis.ai.memory.MemoryEntity
import com.mokshith.jarvis.ai.memory.MemoryQuerySanitizer
import com.mokshith.jarvis.ai.memory.MemoryRetrievalPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAndRetrievalTest {

    @Test
    fun ftsQuerySanitizerStripsOperatorsAndBuildsPrefixMatch() {
        val fts = MemoryQuerySanitizer.buildFtsQuery("Android: \"Room\" FTS4; query! (test)")
        assertEquals("android* OR room* OR fts4* OR query* OR test*", fts)
    }

    @Test
    fun relevanceScoringPrioritizesKeywordMatchAndImportance() {
        val now = System.currentTimeMillis()
        val m1 = MemoryEntity(1, "Groq API Setup", "User prefers Groq llama-3.3-70b as primary model", "PREFERENCE", "groq,ai", "manual", 9, now, now)
        val m2 = MemoryEntity(2, "Grocery List", "Buy apples and milk", "USER_FACT", "home", "manual", 3, now - 10_000_000L, now - 10_000_000L)

        val scored = MemoryRetrievalPipeline.scoreMemories(listOf(m2, m1), setOf("groq", "model"), now)
        assertEquals(1L, scored.first().memory.id)
        assertTrue(scored.first().score > scored.last().score)
    }
}
