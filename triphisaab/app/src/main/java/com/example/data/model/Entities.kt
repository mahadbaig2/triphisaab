package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "Motorcycle Trip",
    val currency: String = "PKR",
    val limitPaisa: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "paired_conversations",
    indices = [
        Index(value = ["pkg", "conversationId"], unique = true)
    ]
)
data class PairedConversation(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val pkg: String = "com.whatsapp.w4b",
    val conversationId: String,
    val senderMetadataFingerprint: String,
    val displayLabel: String,
    val adapterCapabilityVersion: Int = 1,
    val pairedAt: Long = System.currentTimeMillis(),
    val verifiedAt: Long = System.currentTimeMillis(),
    val enabled: Boolean = true
)

@Entity(
    tableName = "inbox_events",
    indices = [
        Index(value = ["eventIdentity"], unique = true),
        Index(value = ["conversationId", "classificationState"])
    ]
)
data class InboxEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val eventIdentity: String,
    val messageTime: Long? = null,
    val detectedAt: Long = System.currentTimeMillis(),
    val orderingSequence: Long = 0L,
    val originalText: String,
    val payloadKind: String = "TEXT",
    val snapshotSignature: String = "",
    val classificationState: String = "DETECTED",
    val promptVersion: String = "v1",
    val proposedCommandJson: String? = null,
    val retryCount: Int = 0,
    val nextRetryAt: Long? = null,
    val errorCode: String? = null,
    val committedAt: Long? = null
)

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["budgetId", "status"]),
        Index(value = ["effectivePlaceId"]),
        Index(value = ["expenseTime"]),
        Index(value = ["categoryId"]),
        Index(value = ["sourceEventId", "sourceLineIndex"], unique = true)
    ]
)
data class Expense(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val budgetId: String,
    val sourceEventId: String? = null,
    val sourceLineIndex: Int = 0,
    val amountPaisa: Long,
    val currency: String = "PKR",
    val description: String,
    val category: String? = null,
    val subcategory: String? = null,
    val categoryId: String? = null,
    val subcategoryId: String? = null,
    val expenseTime: Long = System.currentTimeMillis(),
    val loggedAt: Long = System.currentTimeMillis(),
    val timeCertainty: String = "EXACT", // EXACT, DATE_ONLY, UNCERTAIN
    val timeSource: String = "MESSAGE_TIMESTAMP", // MESSAGE_TIMESTAMP, DETECTION_TIME, PARSED_RETROSPECTIVE, MANUAL_INPUT
    val locationSnapshotId: String? = null,
    val effectivePlaceId: String? = null,
    val locationOverride: String? = null,
    val locationCertainty: String = "DEVICE", // DEVICE, MANUAL, UNCERTAIN, UNAVAILABLE
    val messageTime: Long? = null,
    val status: String = "ACTIVE", // ACTIVE or REVERSED
    val reversedAt: Long? = null
)

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["normalizedName"], unique = true)
    ]
)
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val normalizedName: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "subcategories",
    indices = [
        Index(value = ["categoryId", "normalizedName"], unique = true)
    ]
)
data class Subcategory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val name: String,
    val normalizedName: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_turns",
    indices = [
        Index(value = ["createdAt"])
    ]
)
data class ChatTurn(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant"
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class CategorySpendSummary(
    val category: String?,
    val totalPaisa: Long,
    val count: Int
)

data class PlaceSpendSummary(
    val effectivePlaceId: String?,
    val totalPaisa: Long,
    val count: Int
)

@Entity(
    tableName = "location_snapshots",
    indices = [
        Index(value = ["detectedAt"]),
        Index(value = ["geocodeState"])
    ]
)
data class LocationSnapshot(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val capturedAt: Long? = null,
    val capturedElapsedRealtime: Long? = null,
    val deviceBootContext: String? = null,
    val detectedAt: Long = System.currentTimeMillis(),
    val qualityStatus: String = "UNAVAILABLE", // FRESH, STALE, APPROXIMATE, UNAVAILABLE, DENIED, MANUAL
    val source: String = "DEVICE", // DEVICE or MANUAL_OVERRIDE
    val locality: String? = null,
    val district: String? = null,
    val region: String? = null,
    val country: String? = null,
    val placeId: String? = null,
    val geocodeState: String = "PENDING", // RESOLVED, PENDING, FAILED, OFFLINE
    val geocodeProvider: String? = "android_geocoder"
)

@Entity(
    tableName = "places",
    indices = [
        Index(value = ["canonicalName", "type"], unique = true)
    ]
)
data class Place(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val canonicalName: String,
    val type: String = "LOCALITY", // LOCALITY, DISTRICT, REGION
    val parentId: String? = null,
    val countryCode: String = "PK",
    val aliasesJson: String = "[]",
    val provenance: String = "seed_and_geocoder"
)

@Entity(tableName = "pending_proposals")
data class PendingProposal(
    @PrimaryKey val id: String, // Short code like C123
    val sourceEventId: String,
    val commandJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (10 * 60 * 1000L),
    val state: String = "PENDING" // PENDING, CONFIRMED, CANCELED, EXPIRED
)

@Entity(tableName = "reply_tasks")
data class ReplyTask(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceEventId: String,
    val deterministicPayload: String,
    val state: String = "PENDING", // PENDING, ACTION_INVOKED, UNAVAILABLE, FAILED
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val errorCode: String? = null
)

@Entity(tableName = "audit_entries")
data class AuditEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val actionType: String,
    val entityId: String,
    val beforeJson: String? = null,
    val afterJson: String? = null,
    val origin: String = "SYSTEM", // USER, VALIDATED_AI, SYSTEM
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val pairedConversationId: String? = null,
    val trackingEnabled: Boolean = false,
    val aiConsentGranted: Boolean = false,
    val groqModel: String = "llama-3.3-70b-versatile",
    val strictMode: Boolean = false,
    val timezone: String = "Asia/Karachi",
    val themeMode: String = "SYSTEM",
    val apiKeyCiphertext: String? = null,
    val apiKeyIv: String? = null,
    val retentionDays: Int = 30
)
