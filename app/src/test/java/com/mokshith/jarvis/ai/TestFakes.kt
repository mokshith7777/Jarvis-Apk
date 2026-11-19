package com.mokshith.jarvis.ai

import com.mokshith.jarvis.ai.core.JarvisDispatchers
import com.mokshith.jarvis.ai.memory.MemoryDao
import com.mokshith.jarvis.ai.memory.MemoryEntity
import com.mokshith.jarvis.ai.memory.ProviderConfigEntity
import com.mokshith.jarvis.ai.memory.ProviderDao
import com.mokshith.jarvis.ai.memory.ProviderUsageEntity
import com.mokshith.jarvis.ai.memory.RoutineEntity
import com.mokshith.jarvis.ai.memory.SkillDao
import com.mokshith.jarvis.ai.memory.SkillEntity
import com.mokshith.jarvis.ai.memory.ToolExecutionDao
import com.mokshith.jarvis.ai.memory.ToolExecutionEntity
import com.mokshith.jarvis.ai.security.CredentialRedactor
import com.mokshith.jarvis.ai.security.CredentialVault
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class TestDispatchers(private val d: CoroutineDispatcher = Dispatchers.Unconfined) : JarvisDispatchers {
    override val io: CoroutineDispatcher = d
    override val default: CoroutineDispatcher = d
    override val main: CoroutineDispatcher = d
}

class InMemoryVault : CredentialVault {
    private val map = linkedMapOf<String, String>()
    override fun putSecret(key: String, value: String) { map[key] = value }
    override fun getSecret(key: String): String? = map[key]
    override fun removeSecret(key: String) { map.remove(key) }
    override fun hasSecret(key: String): Boolean = map.containsKey(key)
    override fun getMasked(key: String): String = CredentialRedactor.maskForDisplay(map[key])
    override fun clearAll() { map.clear() }
    override fun allSecretValues(): List<String> = map.values.toList()
}

class InMemoryProviderDao : ProviderDao {
    private val providers = linkedMapOf<String, ProviderConfigEntity>()
    val usageLog = mutableListOf<ProviderUsageEntity>()
    private val providersFlow = MutableStateFlow<List<ProviderConfigEntity>>(emptyList())
    private val usageFlow = MutableStateFlow<List<ProviderUsageEntity>>(emptyList())

    override fun observeProviders(): Flow<List<ProviderConfigEntity>> = providersFlow
    override suspend fun getAllProviders(): List<ProviderConfigEntity> = providers.values.sortedBy { it.priority }
    override suspend fun getById(id: String): ProviderConfigEntity? = providers[id]
    override suspend fun upsertProvider(config: ProviderConfigEntity) {
        providers[config.id] = config
        providersFlow.value = providers.values.sortedBy { it.priority }
    }
    override suspend fun clearDefaults() {
        providers.keys.forEach { k -> providers[k] = providers[k]!!.copy(isDefault = false) }
        providersFlow.value = providers.values.sortedBy { it.priority }
    }
    override suspend fun deleteProvider(id: String) {
        providers.remove(id)
        providersFlow.value = providers.values.sortedBy { it.priority }
    }
    override suspend fun insertUsage(usage: ProviderUsageEntity) {
        usageLog.add(usage)
        usageFlow.value = usageLog.toList()
    }
    override fun observeUsage(limit: Int): Flow<List<ProviderUsageEntity>> = usageFlow
    override suspend fun getUsageForProvider(providerId: String): List<ProviderUsageEntity> =
        usageLog.filter { it.providerId == providerId }
}

class InMemoryMemoryDao : MemoryDao {
    private val items = mutableListOf<MemoryEntity>()
    private var nextId = 1L
    override fun observeActive(now: Long): Flow<List<MemoryEntity>> = MutableStateFlow(items.toList())
    override suspend fun getTopActive(limit: Int, now: Long): List<MemoryEntity> = items.take(limit)
    override suspend fun searchFts(ftsQuery: String, limit: Int, now: Long): List<MemoryEntity> = items.take(limit)
    override suspend fun searchLike(keyword: String, limit: Int, now: Long): List<MemoryEntity> =
        items.filter { it.title.contains(keyword, true) || it.content.contains(keyword, true) }.take(limit)
    override suspend fun getById(id: Long): MemoryEntity? = items.firstOrNull { it.id == id }
    override suspend fun insert(memory: MemoryEntity): Long {
        val id = if (memory.id == 0L) nextId++ else memory.id
        items.add(memory.copy(id = id))
        return id
    }
    override suspend fun update(memory: MemoryEntity) {
        val idx = items.indexOfFirst { it.id == memory.id }
        if (idx >= 0) items[idx] = memory
    }
    override suspend fun delete(memory: MemoryEntity) { items.removeAll { it.id == memory.id } }
    override suspend fun deleteById(id: Long) { items.removeAll { it.id == id } }
    override suspend fun purgeExpired(now: Long): Int {
        val before = items.size
        items.removeAll { it.expiresAt != null && it.expiresAt <= now }
        return before - items.size
    }
}

class InMemorySkillDao : SkillDao {
    private val skills = mutableListOf<SkillEntity>()
    private val routines = mutableListOf<RoutineEntity>()
    override fun observeSkills(): Flow<List<SkillEntity>> = MutableStateFlow(skills.toList())
    override suspend fun getEnabledSkills(): List<SkillEntity> = skills.filter { it.enabled }
    override suspend fun upsertSkill(skill: SkillEntity) {
        skills.removeAll { it.id == skill.id }
        skills.add(skill)
    }
    override suspend fun deleteSkill(id: String) { skills.removeAll { it.id == id } }
    override fun observeRoutines(): Flow<List<RoutineEntity>> = MutableStateFlow(routines.toList())
    override suspend fun upsertRoutine(routine: RoutineEntity) {
        routines.removeAll { it.id == routine.id }
        routines.add(routine)
    }
    override suspend fun deleteRoutine(id: String) { routines.removeAll { it.id == id } }
}

class InMemoryToolExecutionDao : ToolExecutionDao {
    val records = mutableListOf<ToolExecutionEntity>()
    override fun observeRecent(limit: Int): Flow<List<ToolExecutionEntity>> = MutableStateFlow(records.toList())
    override suspend fun insert(record: ToolExecutionEntity) { records.add(record) }
    override suspend fun clearAll() { records.clear() }
}
