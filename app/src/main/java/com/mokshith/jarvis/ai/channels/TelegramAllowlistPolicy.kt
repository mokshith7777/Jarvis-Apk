package com.mokshith.jarvis.ai.channels

/**
 * Enforces strict Telegram sender authorization.
 * Empty allowlist rejects ALL senders by default (fail-closed security).
 */
object TelegramAllowlistPolicy {
    fun parseAuthorizedIds(csv: String): Set<Long> {
        return csv.split(",", ";", " ", "\n")
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
    }

    fun isAuthorized(senderId: Long?, allowedIdsCsv: String): Boolean {
        if (senderId == null) return false
        val allowed = parseAuthorizedIds(allowedIdsCsv)
        if (allowed.isEmpty()) return false
        return senderId in allowed
    }
}
