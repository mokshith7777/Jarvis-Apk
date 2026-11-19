package com.mokshith.jarvis.ai.memory

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val providerId: String?,
    val modelId: String?,
    val channelSource: String = "APP",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId"), Index("createdAt")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant", "tool", "system"
    val content: String,
    val providerId: String? = null,
    val modelId: String? = null,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val latencyMs: Long = 0L,
    val tokensUsed: Int = 0,
    val createdAt: Long
)

@Entity(
    tableName = "memories",
    indices = [Index("category"), Index("expiresAt")]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val content: String,
    val category: String, // "PREFERENCE", "USER_FACT", "PROJECT", "CONVERSATION", "CHECKPOINT"
    val tags: String = "",
    val source: String = "manual",
    val importance: Int = 5, // 1..10
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long? = null
)

@Fts4(contentEntity = MemoryEntity::class)
@Entity(tableName = "memories_fts")
data class MemoryFtsEntity(
    val title: String,
    val content: String,
    val category: String,
    val tags: String
)

enum class TaskStatus {
    QUEUED,
    RUNNING,
    WAITING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(tableName = "tasks", indices = [Index("status"), Index("priority")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val objective: String,
    val status: String = TaskStatus.QUEUED.name,
    val priority: Int = 5,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val progressPercent: Int = 0,
    val checkpointJson: String? = null,
    val completionSummary: String? = null,
    val failureReason: String? = null,
    val conversationId: String? = null,
    val sourceChannel: String = "APP",
    val telegramChatId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "task_steps",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class TaskStepEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val stepIndex: Int,
    val description: String,
    val toolName: String? = null,
    val argumentsJson: String? = null,
    val observation: String? = null,
    val status: String = "PENDING", // PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
    val startedAt: Long? = null,
    val finishedAt: Long? = null
)

@Entity(tableName = "tool_executions", indices = [Index("createdAt"), Index("toolId")])
data class ToolExecutionEntity(
    @PrimaryKey val id: String,
    val toolId: String,
    val toolName: String,
    val argumentsSummary: String,
    val resultSummary: String,
    val success: Boolean,
    val approvedByUser: Boolean = true,
    val durationMs: Long,
    val taskId: String? = null,
    val conversationId: String? = null,
    val createdAt: Long
)

@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey val id: String,
    val type: String, // GROQ, OPENROUTER, GOOGLE_AI, MISTRAL, DEEPSEEK, CUSTOM_OPENAI
    val displayName: String,
    val baseUrl: String,
    val defaultModelId: String,
    val cachedModelsJson: String = "[]",
    val priority: Int = 10,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val lastHealthStatus: String = "UNKNOWN", // UNKNOWN, HEALTHY, DEGRADED, ERROR
    val lastLatencyMs: Long = 0L,
    val lastError: String? = null,
    val lastCheckedAt: Long = 0L
)

@Entity(tableName = "provider_usage", indices = [Index("providerId"), Index("createdAt")])
data class ProviderUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val providerId: String,
    val modelId: String,
    val success: Boolean,
    val latencyMs: Long,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val errorCode: String? = null,
    val createdAt: Long
)

@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val triggerKeywords: String = "",
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val objectiveTemplate: String,
    val skillId: String? = null,
    val enabled: Boolean = true,
    val lastRunAt: Long = 0L,
    val createdAt: Long
)

@Entity(tableName = "channel_configs")
data class ChannelConfigEntity(
    @PrimaryKey val channelId: String, // e.g. "telegram"
    val displayName: String,
    val enabled: Boolean = false,
    val authorizedUserIdsCsv: String = "",
    val lastUpdateOffset: Long = 0L,
    val lastSuccessfulPollAt: Long = 0L,
    val lastError: String? = null,
    val status: String = "STOPPED" // STOPPED, RUNNING, ERROR
)
