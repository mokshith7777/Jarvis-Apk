package com.mokshith.jarvis.ai.brain

import com.mokshith.jarvis.ai.memory.SkillDao
import com.mokshith.jarvis.ai.memory.SkillEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SkillManager(private val skillDao: SkillDao) {

    fun observeSkills(): Flow<List<SkillEntity>> = skillDao.observeSkills()

    suspend fun ensureStarterSkills() {
        if (skillDao.getEnabledSkills().isNotEmpty()) return
        val now = System.currentTimeMillis()
        val starters = listOf(
            SkillEntity(
                id = "skill_research_brief",
                name = "Structured Web Research",
                description = "Research a topic across public web sources and produce a cited summary.",
                instructions = "1. Use web_research (search) to gather authoritative sources. 2. Fetch top relevant URLs. 3. Synthesize findings with inline source URLs and note any inaccessible sources.",
                triggerKeywords = "research,investigate,compare,web,sources",
                createdAt = now,
                updatedAt = now
            ),
            SkillEntity(
                id = "skill_github_review",
                name = "GitHub Repository Review",
                description = "Inspect a GitHub repository's structure, README, open issues, and recent commits.",
                instructions = "1. Call github repo_info. 2. Browse root files and read key files. 3. Check open issues and recent commits. 4. Present an engineering review with actionable recommendations.",
                triggerKeywords = "github,repo,repository,pull request,commit,code review",
                createdAt = now,
                updatedAt = now
            ),
            SkillEntity(
                id = "skill_doc_summary",
                name = "Document Summarizer (SAF)",
                description = "Read and summarize user-selected documents from the granted SAF folder.",
                instructions = "1. List accessible SAF files. 2. Read the target document. 3. Produce key takeaways, action items, and open questions without altering the file.",
                triggerKeywords = "summarize,document,file,notes,analyze file",
                createdAt = now,
                updatedAt = now
            ),
            SkillEntity(
                id = "skill_study_plan",
                name = "Structured Study Plan",
                description = "Break a subject or exam syllabus into prioritized study milestones and tasks.",
                instructions = "1. Clarify scope and timeline from prompt/memory. 2. Group topics by foundational priority. 3. Optionally create tracked tasks with the tasks tool when requested.",
                triggerKeywords = "study plan,syllabus,exam,schedule,prepare",
                createdAt = now,
                updatedAt = now
            )
        )
        starters.forEach { skillDao.upsertSkill(it) }
    }

    suspend fun findMatchingSkills(objective: String): List<SkillEntity> {
        val lower = objective.lowercase()
        return skillDao.getEnabledSkills().filter { skill ->
            val keywords = skill.triggerKeywords.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            keywords.any { lower.contains(it) } || lower.contains(skill.name.lowercase())
        }.take(2)
    }

    suspend fun saveSkill(name: String, description: String, instructions: String, keywords: String, existingId: String? = null) {
        val now = System.currentTimeMillis()
        skillDao.upsertSkill(
            SkillEntity(
                id = existingId ?: UUID.randomUUID().toString(),
                name = name.trim(),
                description = description.trim(),
                instructions = instructions.trim(),
                triggerKeywords = keywords.trim(),
                enabled = true,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteSkill(id: String) = skillDao.deleteSkill(id)
}
