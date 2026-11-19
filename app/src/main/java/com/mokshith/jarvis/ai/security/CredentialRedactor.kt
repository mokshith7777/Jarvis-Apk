package com.mokshith.jarvis.ai.security

object CredentialRedactor {
    private val secretPatterns = listOf(
        Regex("""gsk_[A-Za-z0-9_-]{10,}"""),
        Regex("""sk-or-v1-[A-Za-z0-9_-]{10,}"""),
        Regex("""sk-[A-Za-z0-9_-]{16,}"""),
        Regex("""AIza[0-9A-Za-z_-]{20,}"""),
        Regex("""ghp_[A-Za-z0-9]{20,}"""),
        Regex("""github_pat_[A-Za-z0-9_]{20,}"""),
        Regex("""\b\d{7,12}:[A-Za-z0-9_-]{25,}\b"""),
        Regex("""(?i)(bearer\s+)[A-Za-z0-9._-]{12,}"""),
        Regex("""(?i)(api[_-]?key|token|authorization|password|secret)\s*[:=]\s*['"]?([^\s,'"]{8,})['"]?""")
    )

    fun redact(input: String, knownSecrets: Collection<String> = emptyList()): String {
        var sanitized = input
        knownSecrets.filter { it.length >= 6 }.forEach { secret ->
            sanitized = sanitized.replace(secret, "[REDACTED]")
        }
        secretPatterns.forEach { pattern ->
            sanitized = pattern.replace(sanitized) { match ->
                if (match.groupValues.size >= 3) {
                    "${match.groupValues[1]}=[REDACTED]"
                } else if (match.groupValues.size == 2) {
                    "${match.groupValues[1]}[REDACTED]"
                } else {
                    "[REDACTED]"
                }
            }
        }
        return sanitized
    }

    fun maskForDisplay(secret: String?): String {
        if (secret.isNullOrBlank()) return "Not configured"
        if (secret.length <= 8) return "••••••••"
        return "${secret.take(4)}••••${secret.takeLast(4)}"
    }
}
