package com.example.notifications

import android.content.Context
import com.example.data.db.PairedConversationDao
import com.example.data.model.PairedConversation
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.Locale

data class DiscoveredPairingMetadata(
    val pkg: String,
    val rawKey: String,
    val conversationTitle: String?,
    val senderName: String?,
    val shortcutId: String?,
    val senderPersonKey: String?,
    val subText: String?,
    val isGroup: Boolean,
    val hasIsolatingKey: Boolean,
    val fingerprint: String,
    val timestamp: Long = System.currentTimeMillis()
)

class PairingManager(
    private val context: Context,
    private val pairedConversationDao: PairedConversationDao,
    private val settingsRepository: SettingsRepository
) {
    private val secureRandom = SecureRandom()

    private val _activePairingCode = MutableStateFlow<String?>(null)
    val activePairingCode: StateFlow<String?> = _activePairingCode.asStateFlow()

    private val _codeExpiresAt = MutableStateFlow<Long>(0L)
    val codeExpiresAt: StateFlow<Long> = _codeExpiresAt.asStateFlow()

    private val _discoveredMetadata = MutableStateFlow<DiscoveredPairingMetadata?>(null)
    val discoveredMetadata: StateFlow<DiscoveredPairingMetadata?> = _discoveredMetadata.asStateFlow()

    fun generateNewPairingCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..6)
            .map { chars[secureRandom.nextInt(chars.length)] }
            .joinToString("")
        _activePairingCode.value = code
        _codeExpiresAt.value = System.currentTimeMillis() + (5 * 60 * 1000L) // 5 mins
        _discoveredMetadata.value = null
        return code
    }

    fun isPairingActive(): Boolean {
        val code = _activePairingCode.value ?: return false
        return System.currentTimeMillis() <= _codeExpiresAt.value
    }

    /**
     * Inspects candidate notification payload during pairing.
     * Returns true if candidate text matched the active pairing code.
     */
    fun checkCandidateForPairing(
        pkg: String,
        text: String,
        rawKey: String,
        conversationTitle: String?,
        senderName: String?,
        shortcutId: String?,
        senderPersonKey: String?,
        subText: String?,
        isGroup: Boolean
    ): Boolean {
        val activeCode = _activePairingCode.value ?: return false
        if (System.currentTimeMillis() > _codeExpiresAt.value) {
            _activePairingCode.value = null
            return false
        }

        if (!text.contains(activeCode, ignoreCase = true)) {
            return false
        }

        // WhatsApp Business package check
        val isW4b = pkg == "com.whatsapp.w4b" || pkg == "com.whatsapp"

        // Group chats are strictly ignored per PRD section 4.1 & 5
        if (isGroup) {
            return false
        }

        // Check if there is an isolating conversation or shortcut identifier
        val hasIsolatingKey = !shortcutId.isNullOrBlank() || !senderPersonKey.isNullOrBlank() || (conversationTitle != null && conversationTitle.isNotBlank())

        val rawFingerprint = "$pkg:$shortcutId:$senderPersonKey:$senderName:$conversationTitle"
        val fingerprint = Integer.toHexString(rawFingerprint.hashCode())

        _discoveredMetadata.value = DiscoveredPairingMetadata(
            pkg = pkg,
            rawKey = rawKey,
            conversationTitle = conversationTitle,
            senderName = senderName,
            shortcutId = shortcutId,
            senderPersonKey = senderPersonKey,
            subText = subText,
            isGroup = isGroup,
            hasIsolatingKey = hasIsolatingKey,
            fingerprint = fingerprint
        )

        return true
    }

    suspend fun approvePairing(): Boolean {
        val meta = _discoveredMetadata.value ?: return false
        if (!meta.hasIsolatingKey) {
            // Cannot isolate; fail closed per PRD section 4.1
            return false
        }

        val conversationId = meta.shortcutId ?: meta.senderPersonKey ?: (meta.conversationTitle ?: meta.rawKey)
        val paired = PairedConversation(
            pkg = meta.pkg,
            conversationId = conversationId,
            senderMetadataFingerprint = meta.fingerprint,
            displayLabel = meta.conversationTitle ?: meta.senderName ?: "Personal Number",
            adapterCapabilityVersion = 1,
            pairedAt = System.currentTimeMillis(),
            verifiedAt = System.currentTimeMillis(),
            enabled = true
        )

        pairedConversationDao.insertOrReplace(paired)
        val settings = settingsRepository.getSettingsOnce()
        settingsRepository.setTrackingEnabled(settings.trackingEnabled) // preserve

        _activePairingCode.value = null
        _discoveredMetadata.value = null
        return true
    }

    fun cancelPairing() {
        _activePairingCode.value = null
        _codeExpiresAt.value = 0L
        _discoveredMetadata.value = null
    }
}
