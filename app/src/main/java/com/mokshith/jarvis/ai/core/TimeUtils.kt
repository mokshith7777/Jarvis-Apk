package com.mokshith.jarvis.ai.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

    fun formatTimestamp(epochMillis: Long): String {
        if (epochMillis <= 0L) return "Never"
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(dateTimeFormatter)
    }

    fun formatShortTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "--:--:--"
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)
    }
}
