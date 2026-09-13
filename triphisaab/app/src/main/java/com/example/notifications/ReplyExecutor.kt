package com.example.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

data class CachedReplyAction(
    val conversationId: String,
    val actionIntent: PendingIntent,
    val remoteInput: RemoteInput,
    val timestamp: Long = System.currentTimeMillis()
)

object ReplyExecutor {
    private const val TAG = "ReplyExecutor"

    // In-memory cache of active reply actions; never persisted to DB per PRD spec
    private val activeReplyActions = mutableMapOf<String, CachedReplyAction>()

    fun storeReplyAction(conversationId: String, actionIntent: PendingIntent, remoteInput: RemoteInput) {
        synchronized(activeReplyActions) {
            activeReplyActions[conversationId] = CachedReplyAction(
                conversationId = conversationId,
                actionIntent = actionIntent,
                remoteInput = remoteInput
            )
        }
    }

    fun hasReplyAction(conversationId: String): Boolean {
        synchronized(activeReplyActions) {
            val cached = activeReplyActions[conversationId] ?: return false
            // Expire action after 15 minutes of notification posting
            return (System.currentTimeMillis() - cached.timestamp) < 15 * 60 * 1000L
        }
    }

    fun clearReplyAction(conversationId: String) {
        synchronized(activeReplyActions) {
            activeReplyActions.remove(conversationId)
        }
    }

    /**
     * Executes the RemoteInput direct reply.
     * Returns true if the PendingIntent was successfully invoked.
     * Note: As PRD specifies, invocation is not guaranteed delivery.
     */
    fun sendReply(context: Context, conversationId: String, text: String): Boolean {
        val cached = synchronized(activeReplyActions) {
            activeReplyActions[conversationId]
        } ?: run {
            Log.w(TAG, "No active reply action found in memory for conversation: $conversationId")
            return false
        }

        return try {
            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(cached.remoteInput.resultKey, text)
            RemoteInput.addResultsToIntent(arrayOf(cached.remoteInput), intent, bundle)

            cached.actionIntent.send(context, 0, intent)
            Log.i(TAG, "RemoteInput direct reply invoked successfully for $conversationId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to invoke RemoteInput direct reply: ${e.message}", e)
            false
        }
    }
}
