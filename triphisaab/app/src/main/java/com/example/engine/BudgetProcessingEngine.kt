package com.example.engine

import android.content.Context
import android.util.Log
import com.example.ai.ClassifierResponse
import com.example.ai.GroqClassifier
import com.example.data.db.TripBudgetDatabase
import com.example.data.model.*
import com.example.data.repository.LedgerRepository
import com.example.data.repository.SettingsRepository
import com.example.location.TripLocationProvider
import com.example.notifications.ReplyExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

class BudgetProcessingEngine(
    private val context: Context,
    private val database: TripBudgetDatabase,
    private val ledgerRepository: LedgerRepository,
    private val settingsRepository: SettingsRepository,
    private val locationProvider: TripLocationProvider
) {
    companion object {
        private const val TAG = "BudgetProcessingEngine"
    }

    private val engineScope = CoroutineScope(Dispatchers.Default)
    private val processingMutex = Mutex()
    private val classifier = GroqClassifier(settingsRepository)
    private val budgetChatManager = BudgetChatManager(database, settingsRepository)
    private val secureRandom = SecureRandom()

    fun enqueueEvent(
        conversationId: String,
        eventIdentity: String,
        messageText: String,
        messageTime: Long = System.currentTimeMillis()
    ) {
        engineScope.launch {
            processingMutex.withLock {
                processIncomingCandidate(conversationId, eventIdentity, messageText, messageTime)
            }
        }
    }

    private suspend fun processIncomingCandidate(
        conversationId: String,
        eventIdentity: String,
        messageText: String,
        messageTime: Long
    ) {
        // 1. Deduplication check by eventIdentity
        val existing = database.inboxEventDao().getEventByIdentity(eventIdentity)
        if (existing != null) {
            Log.d(TAG, "Duplicate event identity $eventIdentity, skipping")
            return
        }

        // 2. Candidate-time location snapshot captured immediately
        val locationSnapshot = locationProvider.captureCandidateSnapshot(messageTime)

        // 3. Persist InboxEvent as DETECTED
        val eventId = UUID.randomUUID().toString()
        val inboxEvent = InboxEvent(
            id = eventId,
            conversationId = conversationId,
            eventIdentity = eventIdentity,
            messageTime = messageTime,
            detectedAt = System.currentTimeMillis(),
            originalText = messageText,
            payloadKind = "TEXT",
            classificationState = "PENDING_CLASSIFICATION"
        )
        database.inboxEventDao().insert(inboxEvent)

        // 4. Check for explicit @chat conversational mode
        if (messageText.trim().startsWith("@chat", ignoreCase = true)) {
            val chatReply = budgetChatManager.handleChat(messageText)
            database.inboxEventDao().update(
                inboxEvent.copy(
                    classificationState = "COMMITTED",
                    committedAt = System.currentTimeMillis()
                )
            )
            dispatchReply(inboxEvent.id, inboxEvent.conversationId, chatReply)
            return
        }

        // 5. LLM-first classification (with robust local fallback if offline or no consent)
        val response = classifier.classify(messageText)

        // 6. Handle Action
        when (response.action) {
            "IGNORE" -> {
                database.inboxEventDao().update(
                    inboxEvent.copy(
                        classificationState = "IGNORED",
                        proposedCommandJson = null
                    )
                )
                // Zero bot replies for ignored messages
            }

            "ADD_EXPENSE" -> {
                handleExpenseAction(inboxEvent, response, locationSnapshot)
            }

            "QUERY_BUDGET" -> {
                handleQueryAction(inboxEvent, response)
            }

            "UNDO_EXPENSE" -> {
                handleUndoAction(inboxEvent)
            }

            "SET_BUDGET" -> {
                handleSetBudgetAction(inboxEvent, response)
            }

            "NEEDS_CONFIRMATION" -> {
                handleConfirmationAction(inboxEvent, response, locationSnapshot)
            }

            else -> {
                database.inboxEventDao().update(
                    inboxEvent.copy(classificationState = "NEEDS_REVIEW")
                )
            }
        }
    }

    private suspend fun handleExpenseAction(
        inboxEvent: InboxEvent,
        response: ClassifierResponse,
        locationSnapshot: LocationSnapshot
    ) {
        val rawItems = response.expenses
        if (rawItems.isNullOrEmpty()) {
            database.inboxEventDao().update(inboxEvent.copy(classificationState = "NEEDS_REVIEW"))
            return
        }

        val settings = settingsRepository.getSettingsOnce()

        // If strict mode is ON or response is ambiguous, create a pending proposal first
        if (settings.strictMode || response.certainty == "ambiguous") {
            createProposal(inboxEvent, rawItems)
            return
        }

        try {
            val budget = database.budgetDao().getActiveBudgetOnce()
            val budgetId = budget?.id ?: UUID.randomUUID().toString()

            val expenses = rawItems.mapIndexed { index, item ->
                val paisa = MoneyFormatter.parseToPaisa(item.amountDecimal)

                // Parse retrospective or explicit date/time
                val parsedDate = TripDateParser.parseExpenseDateTime(
                    text = item.datePhrase ?: "${item.description} ${inboxEvent.originalText}",
                    messageTime = inboxEvent.messageTime
                )

                // Category resolution: prioritize LLM's classification; only use local keywords if LLM left it blank or Miscellaneous
                val (resolvedCat, resolvedSubcat) = if (item.category.isNullOrBlank() || item.category.equals("Miscellaneous", ignoreCase = true)) {
                    val detected = com.example.data.repository.CategoryTaxonomyManager.detectFromKeywords(item.description)
                    if (detected != null) {
                        Pair(detected.categoryName, detected.subcategoryName ?: item.subcategory)
                    } else {
                        Pair(item.category ?: "Miscellaneous", item.subcategory)
                    }
                } else {
                    Pair(item.category, item.subcategory)
                }

                // Robust location and place ID resolution
                val placeIdToAssign = when {
                    !item.explicitPlaceCorrection.isNullOrBlank() -> {
                        val found = database.placeDao().findByName(item.explicitPlaceCorrection)
                        found?.id ?: run {
                            val newPlace = com.example.data.model.Place(
                                id = UUID.randomUUID().toString(),
                                canonicalName = item.explicitPlaceCorrection,
                                type = "LOCALITY",
                                countryCode = "PK",
                                aliasesJson = "[\"${item.explicitPlaceCorrection.lowercase(Locale.ROOT)}\"]",
                                provenance = "user_explicit"
                            )
                            database.placeDao().insertPlace(newPlace)
                            newPlace.id
                        }
                    }
                    parsedDate.isRetrospective -> null
                    !locationSnapshot.placeId.isNullOrBlank() -> locationSnapshot.placeId
                    !locationSnapshot.locality.isNullOrBlank() -> {
                        val found = database.placeDao().findByName(locationSnapshot.locality)
                        found?.id ?: run {
                            val newPlace = com.example.data.model.Place(
                                id = UUID.randomUUID().toString(),
                                canonicalName = locationSnapshot.locality,
                                type = "LOCALITY",
                                countryCode = "PK",
                                aliasesJson = "[\"${locationSnapshot.locality.lowercase(Locale.ROOT)}\"]",
                                provenance = "device_locality"
                            )
                            database.placeDao().insertPlace(newPlace)
                            newPlace.id
                        }
                    }
                    else -> null
                }

                val locOverride = item.explicitPlaceCorrection ?: if (parsedDate.isRetrospective) null else locationSnapshot.locality

                Expense(
                    id = UUID.randomUUID().toString(),
                    budgetId = budgetId,
                    sourceEventId = inboxEvent.id,
                    sourceLineIndex = index,
                    amountPaisa = paisa,
                    currency = item.currency.ifBlank { "PKR" },
                    description = item.description,
                    category = resolvedCat,
                    subcategory = resolvedSubcat,
                    timeCertainty = parsedDate.timeCertainty,
                    timeSource = parsedDate.timeSource,
                    locationCertainty = when {
                        !item.explicitPlaceCorrection.isNullOrBlank() -> "EXPLICIT"
                        parsedDate.isRetrospective -> "UNCERTAIN"
                        locationSnapshot.qualityStatus == "FRESH" -> "EXACT"
                        else -> "DEVICE"
                    },
                    locationOverride = locOverride,
                    messageTime = inboxEvent.messageTime,
                    expenseTime = parsedDate.epochMillis,
                    loggedAt = System.currentTimeMillis(),
                    locationSnapshotId = locationSnapshot.id,
                    effectivePlaceId = placeIdToAssign,
                    status = "ACTIVE"
                )
            }

            val commitResult = ledgerRepository.recordExpenses(expenses, sourceEventId = inboxEvent.id)
            database.inboxEventDao().update(
                inboxEvent.copy(
                    classificationState = "COMMITTED",
                    committedAt = System.currentTimeMillis()
                )
            )

            // Render deterministic confirmation reply
            val replyText = DeterministicReplyRenderer.renderExpenseConfirmation(commitResult, locationSnapshot)
            dispatchReply(inboxEvent.id, inboxEvent.conversationId, replyText)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to record expense: ${e.message}", e)
            database.inboxEventDao().update(
                inboxEvent.copy(
                    classificationState = "NEEDS_REVIEW",
                    errorCode = e.message
                )
            )
        }
    }

    private suspend fun handleQueryAction(inboxEvent: InboxEvent, response: ClassifierResponse) {
        val query = response.query ?: return
        val replyText = if (query.scope == "place" && !query.placeText.isNullOrBlank()) {
            val placeRes = ledgerRepository.queryByPlace(query.placeText, query.page)
            if (placeRes != null) {
                DeterministicReplyRenderer.renderPlaceQuery(placeRes)
            } else {
                "No records found for '${query.placeText}'. Known trip places include Gilgit, Hunza, Skardu, Islamabad, Chilas."
            }
        } else if (query.scope == "category") {
            val catRes = ledgerRepository.queryGeneral("category", query.categoryText)
            DeterministicReplyRenderer.renderGeneralQuery(catRes)
        } else {
            val genRes = ledgerRepository.queryGeneral(query.scope, query.categoryText)
            DeterministicReplyRenderer.renderGeneralQuery(genRes)
        }

        database.inboxEventDao().update(
            inboxEvent.copy(classificationState = "COMMITTED", committedAt = System.currentTimeMillis())
        )
        dispatchReply(inboxEvent.id, inboxEvent.conversationId, replyText)
    }

    private suspend fun handleUndoAction(inboxEvent: InboxEvent) {
        val undoResult = ledgerRepository.undoLastExpense()
        val replyText = if (undoResult != null) {
            DeterministicReplyRenderer.renderUndo(undoResult)
        } else {
            "No active expense found to undo."
        }

        database.inboxEventDao().update(
            inboxEvent.copy(classificationState = "COMMITTED", committedAt = System.currentTimeMillis())
        )
        dispatchReply(inboxEvent.id, inboxEvent.conversationId, replyText)
    }

    private suspend fun handleSetBudgetAction(inboxEvent: InboxEvent, response: ClassifierResponse) {
        val amountStr = response.budgetDecimal ?: return
        try {
            val paisa = MoneyFormatter.parseToPaisa(amountStr)
            ledgerRepository.setBudgetLimit(paisa)
            val formatted = MoneyFormatter.formatPaisa(paisa)
            val replyText = "Budget limit updated: $formatted"

            database.inboxEventDao().update(
                inboxEvent.copy(classificationState = "COMMITTED", committedAt = System.currentTimeMillis())
            )
            dispatchReply(inboxEvent.id, inboxEvent.conversationId, replyText)
        } catch (e: Exception) {
            database.inboxEventDao().update(inboxEvent.copy(classificationState = "NEEDS_REVIEW"))
        }
    }

    private suspend fun handleConfirmationAction(
        inboxEvent: InboxEvent,
        response: ClassifierResponse,
        locationSnapshot: LocationSnapshot
    ) {
        val clarification = response.clarification ?: ""

        // Check if this is a confirm command for an existing proposal
        if (clarification.startsWith("C") || clarification == "CONFIRM_PLAIN") {
            val proposal = if (clarification == "CONFIRM_PLAIN") {
                database.pendingProposalDao().getLatestActiveProposal()
            } else {
                database.pendingProposalDao().getProposal(clarification)
            }

            if (proposal != null && proposal.state == "PENDING" && proposal.expiresAt > System.currentTimeMillis()) {
                database.pendingProposalDao().updateState(proposal.id, "CONFIRMED")
                val replyText = "Confirmed proposal ${proposal.id}."
                database.inboxEventDao().update(inboxEvent.copy(classificationState = "COMMITTED"))
                dispatchReply(inboxEvent.id, inboxEvent.conversationId, replyText)
                return
            } else {
                dispatchReply(inboxEvent.id, inboxEvent.conversationId, "No active or unexpired proposal found matching '$clarification'.")
                return
            }
        }

        // Otherwise, it needs review
        database.inboxEventDao().update(
            inboxEvent.copy(classificationState = "NEEDS_REVIEW")
        )
    }

    private suspend fun createProposal(inboxEvent: InboxEvent, items: List<com.example.ai.ClassifierExpenseItem>) {
        val code = "C" + (100 + secureRandom.nextInt(900))
        val summary = items.joinToString(", ") { "${it.description} — Rs. ${it.amountDecimal}" }
        val proposal = PendingProposal(
            id = code,
            sourceEventId = inboxEvent.id,
            commandJson = summary
        )
        database.pendingProposalDao().insert(proposal)
        database.inboxEventDao().update(
            inboxEvent.copy(classificationState = "WAITING_CONFIRMATION", proposedCommandJson = summary)
        )

        val reply = DeterministicReplyRenderer.renderConfirmationProposal(code, summary)
        dispatchReply(inboxEvent.id, inboxEvent.conversationId, reply)
    }

    private suspend fun dispatchReply(eventId: String, conversationId: String, payload: String) {
        val replyTask = ReplyTask(
            id = UUID.randomUUID().toString(),
            sourceEventId = eventId,
            deterministicPayload = payload,
            state = "PENDING"
        )
        database.replyTaskDao().insert(replyTask)

        // RemoteInput direct reply
        val success = ReplyExecutor.sendReply(context, conversationId, payload)
        val newState = if (success) "ACTION_INVOKED" else "UNAVAILABLE"
        database.replyTaskDao().update(
            replyTask.copy(
                state = newState,
                attemptCount = 1,
                lastAttemptAt = System.currentTimeMillis()
            )
        )
    }
}
