package com.mokshith.jarvis.ai.security

import android.content.Context
import android.content.SharedPreferences

interface CredentialVault {
    fun putSecret(key: String, value: String)
    fun getSecret(key: String): String?
    fun removeSecret(key: String)
    fun hasSecret(key: String): Boolean
    fun getMasked(key: String): String
    fun clearAll()
    fun allSecretValues(): List<String>
}

class KeystoreCredentialVault(
    context: Context,
    private val crypto: CryptoEngine = AndroidKeystoreCryptoEngine()
) : CredentialVault {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun putSecret(key: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            removeSecret(key)
            return
        }
        val encrypted = crypto.encrypt(trimmed)
        prefs.edit().putString(key, encrypted).apply()
    }

    @Synchronized
    override fun getSecret(key: String): String? {
        val encrypted = prefs.getString(key, null) ?: return null
        return runCatching { crypto.decrypt(encrypted) }.getOrNull()
    }

    @Synchronized
    override fun removeSecret(key: String) {
        prefs.edit().remove(key).apply()
    }

    @Synchronized
    override fun hasSecret(key: String): Boolean = prefs.contains(key)

    override fun getMasked(key: String): String = CredentialRedactor.maskForDisplay(getSecret(key))

    @Synchronized
    override fun clearAll() {
        prefs.edit().clear().apply()
    }

    @Synchronized
    override fun allSecretValues(): List<String> {
        return prefs.all.keys.mapNotNull { getSecret(it) }.filter { it.isNotBlank() }
    }

    companion object {
        private const val PREFS_NAME = "jarvis_encrypted_vault_v1"
        const val KEY_TELEGRAM_BOT_TOKEN = "channel_telegram_bot_token"
        const val KEY_GITHUB_PAT = "tool_github_pat"
        fun providerKey(providerId: String): String = "provider_api_key_$providerId"
    }
}
