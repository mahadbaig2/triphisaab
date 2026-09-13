package com.example.data.repository

import com.example.ai.KeystoreSecretManager
import com.example.data.db.PairedConversationDao
import com.example.data.db.SettingsDao
import com.example.data.model.AppSettings
import com.example.data.model.PairedConversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val settingsDao: SettingsDao,
    private val pairedConversationDao: PairedConversationDao,
    private val keystoreSecretManager: KeystoreSecretManager
) {
    val settingsFlow: Flow<AppSettings> = settingsDao.getSettings().map { it ?: AppSettings(id = 1) }
    val pairedConversationFlow: Flow<PairedConversation?> = pairedConversationDao.getPairedConversation()

    suspend fun getSettingsOnce(): AppSettings {
        return settingsDao.getSettingsOnce() ?: AppSettings(id = 1).also {
            settingsDao.insertOrUpdate(it)
        }
    }

    suspend fun setApiKey(rawApiKey: String) {
        if (rawApiKey.isBlank()) {
            val current = getSettingsOnce()
            settingsDao.insertOrUpdate(current.copy(apiKeyCiphertext = null, apiKeyIv = null))
            keystoreSecretManager.deleteKey()
            return
        }
        val encrypted = keystoreSecretManager.encrypt(rawApiKey.trim())
        val current = getSettingsOnce()
        settingsDao.insertOrUpdate(
            current.copy(
                apiKeyCiphertext = encrypted.ciphertextBase64,
                apiKeyIv = encrypted.ivBase64
            )
        )
    }

    suspend fun getApiKey(): String? {
        val keys = getApiKeysList()
        return keys.firstOrNull()
    }

    suspend fun getApiKeysList(): List<String> {
        val current = getSettingsOnce()
        val ciphertext = current.apiKeyCiphertext ?: return emptyList()
        val iv = current.apiKeyIv ?: return emptyList()
        val raw = try {
            keystoreSecretManager.decrypt(ciphertext, iv)
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        return raw.split(Regex("[\n,;]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    suspend fun setAiConsent(granted: Boolean) {
        val current = getSettingsOnce()
        settingsDao.insertOrUpdate(current.copy(aiConsentGranted = granted))
    }

    suspend fun setGroqModel(model: String) {
        val current = getSettingsOnce()
        settingsDao.insertOrUpdate(current.copy(groqModel = model))
    }

    suspend fun setStrictMode(strict: Boolean) {
        val current = getSettingsOnce()
        settingsDao.insertOrUpdate(current.copy(strictMode = strict))
    }

    suspend fun setTrackingEnabled(enabled: Boolean) {
        val current = getSettingsOnce()
        settingsDao.insertOrUpdate(current.copy(trackingEnabled = enabled))
    }

    suspend fun clearPairedConversation() {
        pairedConversationDao.clearAll()
        val current = getSettingsOnce()
        settingsDao.insertOrUpdate(current.copy(pairedConversationId = null))
    }
}
