package com.example.notifications

import android.app.Notification
import android.app.RemoteInput
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.TripBudgetApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TripBudgetNotificationListener : NotificationListenerService() {
    companion object {
        private const val TAG = "TBNotifListener"
        const val W4B_PACKAGE = "com.whatsapp.w4b"
        const val WHATSAPP_PACKAGE = "com.whatsapp"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationListenerService connected")
        ListenerDiagnostics.updateConnected(true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "NotificationListenerService disconnected")
        ListenerDiagnostics.updateConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName
        // Strictly filter by WhatsApp Business package (or personal WhatsApp if in pairing/testing mode)
        if (pkg != W4B_PACKAGE && pkg != WHATSAPP_PACKAGE) {
            return
        }

        val notification = sbn.notification ?: return

        // Drop summary group notifications per PRD section 5
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return
        }

        val extras = notification.extras ?: return

        // Drop group conversations per PRD section 4.1 & 5
        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        if (isGroup) {
            return
        }

        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val shortcutId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            notification.shortcutId
        } else null

        // Inspect RemoteInput reply actions
        val replyAction = findRemoteInputAction(notification)

        // Extract latest message text and sender
        val extracted = extractLatestMessage(notification, extras)
        val text = extracted.text
        val senderName = extracted.senderName
        val senderKey = extracted.senderKey
        val isSelf = extracted.isSelf
        val messageTime = extracted.timestamp ?: sbn.postTime

        // If it's outgoing / self echo, ignore to prevent bot reply loops (PRD section 5 & acceptance test 9)
        if (isSelf) {
            return
        }

        if (text.isNullOrBlank()) {
            return
        }

        val hasMessagingStyle = extras.containsKey(Notification.EXTRA_MESSAGES)
        val hasPerson = senderKey != null || !senderName.isNullOrBlank()

        val metadataDump = "pkg=$pkg, sbnKey=${sbn.key}, shortcut=$shortcutId, convTitle=$conversationTitle, sender=$senderName, hasReply=${replyAction != null}"
        ListenerDiagnostics.recordNotification(
            pkg = pkg,
            hasShortcutId = !shortcutId.isNullOrBlank(),
            hasPersonMetadata = hasPerson,
            hasRemoteInputReply = replyAction != null,
            hasMessagingStyle = hasMessagingStyle,
            metadataDump = metadataDump
        )

        val app = TripBudgetApplication.instance

        // 1. Check if pairing mode is active
        if (app.pairingManager.isPairingActive()) {
            val matched = app.pairingManager.checkCandidateForPairing(
                pkg = pkg,
                text = text,
                rawKey = sbn.key,
                conversationTitle = conversationTitle,
                senderName = senderName,
                shortcutId = shortcutId,
                senderPersonKey = senderKey,
                subText = subText,
                isGroup = isGroup
            )
            if (matched) {
                // Store reply action for the pairing test reply
                if (replyAction != null) {
                    val conversationId = shortcutId ?: senderKey ?: (conversationTitle ?: sbn.key)
                    ReplyExecutor.storeReplyAction(conversationId, replyAction.first, replyAction.second)
                }
                return
            }
        }

        // 2. Verified paired-chat check
        serviceScope.launch {
            val paired = app.settingsRepository.pairedConversationFlow
            val activePaired = app.database.pairedConversationDao().getPairedConversationOnce()

            if (activePaired == null || !activePaired.enabled) {
                // No paired conversation; ignore without retaining text or AI calls (PRD Section 1)
                return@launch
            }

            // Verify isolated identity
            val candidateId = shortcutId ?: senderKey ?: (conversationTitle ?: "")
            val isMatch = (candidateId.isNotBlank() && candidateId == activePaired.conversationId) ||
                    (shortcutId != null && shortcutId == activePaired.conversationId)

            if (!isMatch) {
                // Other contact: zero AI calls, retained message text, location requests, ledger writes or bot replies (PRD P0)
                return@launch
            }

            // Section 1: Activation rule - Everything requires @chat token
            if (!app.unifiedAgent.isChatTokenActivated(text)) {
                // Unprefixed message: zero AI calls, zero financial writes, zero replies, zero location capture, zero retained bodies
                return@launch
            }

            // Cache reply action for paired conversation
            if (replyAction != null) {
                ReplyExecutor.storeReplyAction(activePaired.conversationId, replyAction.first, replyAction.second)
            }

            // Enqueue into unified budget agent
            val eventIdentity = "${sbn.key}_${messageTime}_${text.hashCode()}"
            app.unifiedAgent.enqueueEvent(
                conversationId = activePaired.conversationId,
                eventIdentity = eventIdentity,
                messageText = text,
                messageTime = messageTime
            )
        }
    }

    private data class ExtractedMessage(
        val text: String?,
        val senderName: String?,
        val senderKey: String?,
        val isSelf: Boolean,
        val timestamp: Long?
    )

    private fun extractLatestMessage(notification: Notification, extras: Bundle): ExtractedMessage {
        // Try MessagingStyle bundles
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (messages != null && messages.isNotEmpty()) {
            val lastBundle = messages.lastOrNull() as? Bundle
            if (lastBundle != null) {
                val text = lastBundle.getCharSequence("text")?.toString()
                val sender = lastBundle.getCharSequence("sender")?.toString()
                val time = lastBundle.getLong("time", System.currentTimeMillis())
                val senderPerson = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    lastBundle.getParcelable<android.app.Person>("sender_person")
                } else null

                val personKey = senderPerson?.key ?: senderPerson?.uri
                val personName = senderPerson?.name?.toString() ?: sender
                val isSelf = extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)?.toString() == personName

                return ExtractedMessage(
                    text = text,
                    senderName = personName,
                    senderKey = personKey,
                    isSelf = isSelf,
                    timestamp = time
                )
            }
        }

        // Fallback to simple bigText or text
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val simpleText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

        return ExtractedMessage(
            text = bigText ?: simpleText,
            senderName = title,
            senderKey = null,
            isSelf = false,
            timestamp = null
        )
    }

    private fun findRemoteInputAction(notification: Notification): Pair<android.app.PendingIntent, RemoteInput>? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (ri in remoteInputs) {
                if (ri.resultKey != null && action.actionIntent != null) {
                    return Pair(action.actionIntent, ri)
                }
            }
        }
        return null
    }
}
