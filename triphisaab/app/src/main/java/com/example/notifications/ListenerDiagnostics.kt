package com.example.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiagnosticsState(
    val isListenerConnected: Boolean = false,
    val isWhatsAppBusinessInstalled: Boolean = false,
    val lastEventTime: Long? = null,
    val lastPackageReceived: String? = null,
    val hasShortcutId: Boolean = false,
    val hasPersonMetadata: Boolean = false,
    val hasRemoteInputReply: Boolean = false,
    val hasMessagingStyle: Boolean = false,
    val lastMetadataDump: String? = null,
    val lastError: String? = null,
    val isDemoFixture: Boolean = false
)

object ListenerDiagnostics {
    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    fun updateConnected(connected: Boolean) {
        _state.value = _state.value.copy(isListenerConnected = connected)
    }

    fun updateW4bInstalled(installed: Boolean) {
        _state.value = _state.value.copy(isWhatsAppBusinessInstalled = installed)
    }

    fun recordNotification(
        pkg: String,
        hasShortcutId: Boolean,
        hasPersonMetadata: Boolean,
        hasRemoteInputReply: Boolean,
        hasMessagingStyle: Boolean,
        metadataDump: String,
        isDemoFixture: Boolean = false
    ) {
        _state.value = _state.value.copy(
            lastEventTime = System.currentTimeMillis(),
            lastPackageReceived = pkg,
            hasShortcutId = hasShortcutId,
            hasPersonMetadata = hasPersonMetadata,
            hasRemoteInputReply = hasRemoteInputReply,
            hasMessagingStyle = hasMessagingStyle,
            lastMetadataDump = metadataDump,
            isDemoFixture = isDemoFixture
        )
    }

    fun recordError(error: String) {
        _state.value = _state.value.copy(lastError = error)
    }
}
