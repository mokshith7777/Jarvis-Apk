package com.mokshith.jarvis.ai.memory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun searchMessages(query: String, limit: Int = 20): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET content = :content, latencyMs = :latencyMs, tokensUsed = :tokensUsed WHERE id = :id")
    suspend fun updateMessageContent(id: String, content: String, latencyMs: Long, tokensUsed: Int)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE expiresAt IS NULL OR expiresAt > :now ORDER BY updatedAt DESC")
    fun observeActive(now: Long = System.currentTimeMillis()): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE (expiresAt IS NULL OR expiresAt > :now) ORDER BY importance DESC, updatedAt DESC LIMIT :limit")
    suspend fun getTopActive(limit: Int, now: Long = System.currentTimeMillis()): List<MemoryEntity>

    @Query(
        "SELECT memories.* FROM memories " +
            "JOIN memories_fts ON memories.id = memories_fts.rowid " +
            "WHERE memories_fts MATCH :ftsQuery AND (memories.expiresAt IS NULL OR memories.expiresAt > :now) " +
            "ORDER BY memories.importance DESC, memories.updatedAt DESC LIMIT :limit"
    )
    suspend fun searchFts(ftsQuery: String, limit: Int = 20, now: Long = System.currentTimeMillis()): List<MemoryEntity>

    @Query(
        "SELECT * FROM memories WHERE (title LIKE '%' || :keyword || '%' OR content LIKE '%' || :keyword || '%' OR tags LIKE '%' || :keyword || '%') " +
            "AND (expiresAt IS NULL OR expiresAt > :now) ORDER BY importance DESC, updatedAt DESC LIMIT :limit"
    )
    suspend fun searchLike(keyword: String, limit: Int = 20, now: Long = System.currentTimeMillis()): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Delete
    suspend fun delete(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memories WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun purgeExpired(now: Long = System.currentTimeMillis()): Int
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE status IN ('QUEUED', 'RUNNING', 'WAITING', 'PAUSED') ORDER BY priority DESC, createdAt ASC")
    suspend fun getPendingOrActive(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY stepIndex ASC")
    fun observeSteps(taskId: String): Flow<List<TaskStepEntity>>

    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY stepIndex ASC")
    suspend fun getSteps(taskId: String): List<TaskStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStep(step: TaskStepEntity)
}

@Dao
interface ToolExecutionDao {
    @Query("SELECT * FROM tool_executions ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<ToolExecutionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ToolExecutionEntity)

    @Query("DELETE FROM tool_executions")
    suspend fun clearAll()
}

@Dao
interface ProviderDao {
    @Query("SELECT * FROM provider_configs ORDER BY priority ASC, displayName ASC")
    fun observeProviders(): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_configs ORDER BY priority ASC, displayName ASC")
    suspend fun getAllProviders(): List<ProviderConfigEntity>

    @Query("SELECT * FROM provider_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvider(config: ProviderConfigEntity)

    @Query("UPDATE provider_configs SET isDefault = 0")
    suspend fun clearDefaults()

    @Query("DELETE FROM provider_configs WHERE id = :id")
    suspend fun deleteProvider(id: String)

    @Insert
    suspend fun insertUsage(usage: ProviderUsageEntity)

    @Query("SELECT * FROM provider_usage ORDER BY createdAt DESC LIMIT :limit")
    fun observeUsage(limit: Int = 200): Flow<List<ProviderUsageEntity>>

    @Query("SELECT * FROM provider_usage WHERE providerId = :providerId ORDER BY createdAt DESC LIMIT 100")
    suspend fun getUsageForProvider(providerId: String): List<ProviderUsageEntity>
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY updatedAt DESC")
    fun observeSkills(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE enabled = 1 ORDER BY updatedAt DESC")
    suspend fun getEnabledSkills(): List<SkillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkill(skill: SkillEntity)

    @Query("DELETE FROM skills WHERE id = :id")
    suspend fun deleteSkill(id: String)

    @Query("SELECT * FROM routines ORDER BY createdAt DESC")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channel_configs")
    fun observeAll(): Flow<List<ChannelConfigEntity>>

    @Query("SELECT * FROM channel_configs WHERE channelId = :channelId LIMIT 1")
    fun observeChannel(channelId: String): Flow<ChannelConfigEntity?>

    @Query("SELECT * FROM channel_configs WHERE channelId = :channelId LIMIT 1")
    suspend fun getChannel(channelId: String): ChannelConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ChannelConfigEntity)
}
