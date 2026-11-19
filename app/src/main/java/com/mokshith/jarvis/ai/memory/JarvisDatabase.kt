package com.mokshith.jarvis.ai.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        MemoryFtsEntity::class,
        TaskEntity::class,
        TaskStepEntity::class,
        ToolExecutionEntity::class,
        ProviderConfigEntity::class,
        ProviderUsageEntity::class,
        SkillEntity::class,
        RoutineEntity::class,
        ChannelConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao
    abstract fun taskDao(): TaskDao
    abstract fun toolExecutionDao(): ToolExecutionDao
    abstract fun providerDao(): ProviderDao
    abstract fun skillDao(): SkillDao
    abstract fun channelDao(): ChannelDao

    companion object {
        fun build(context: Context): JarvisDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                JarvisDatabase::class.java,
                "jarvis_alpha_v1.db"
            ).build()
        }
    }
}
