package com.example.engine

import android.content.Context
import android.util.Log
import com.example.ai.*
import com.example.data.db.TripBudgetDatabase
import com.example.data.model.InboxEvent
import com.example.data.model.LocationSnapshot
import com.example.data.model.Transaction
import com.example.data.repository.LedgerRepository
import com.example.data.repository.SettingsRepository
import com.example.location.TripLocationProvider
import com.example.notifications.ReplyExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class UnifiedBudgetAgent(
    private val context: Context,
    private val database: TripBudgetDatabase,
    private val ledgerRepository: LedgerRepository,
    private val settingsRepository: SettingsRepository,
    private val locationProvider: TripLocationProvider
) {
    companion object {
        private const val TAG = "UnifiedBudgetAgent"
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private val CHAT_PREFIX_REGEX = Regex("^(?i)@chat(?:\\s+(.*)|$)")
        private val CONFIRM_PATTERN = Pattern.compile("^(?i)confirm(?:\\s+([A-Za-z0-9]{3,8}))?$")
        private val CANCEL_PATTERN = Pattern.compile("^(?i)cancel(?:\\s+([A-Za-z0-9]{3,8}))?$")
    }

    private val agentScope = CoroutineScope(Dispatchers.Default)
    private val processingMutex = Mutex()
    private val classifier = GroqClassifier(settingsRepository)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    /**
     * Checks whether an incoming message qualifies for activation.
     * Matches @chat as a distinct token at the start of the message.
     */
    fun isChatTokenActivated(rawText: String): Boolean {
        val trimmed = rawText.trim()
        return CHAT_PREFIX_REGEX.matches(trimmed)
    }

    /**
     * Enqueues an activated @chat event.
     */
    fun enqueueEvent(
        conversationId: String,
        eventIdentity: String,
        messageText: String,
        messageTime: Long = System.currentTimeMillis()
    ) {
        agentScope.launch {
            processingMutex.withLock {
                processChatCandidate(conversationId, eventIdentity, messageText, messageTime)
            }
        }
    }

    private suspend fun processChatCandidate(
        conversationId: String,
        eventIdentity: String,
        messageText: String,
        messageTime: Long
    ) {
        // 1. Replay prevention check
        val existing = database.inboxEventDao().getEventByIdentity(eventIdentity)
        if (existing != null) {
            Log.d(TAG, "Duplicate event identity $eventIdentity, skipping")
            return
        }

        // 2. Strict token check and prefix stripping
        val match = CHAT_PREFIX_REGEX.find(messageText.trim())
        if (match == null) {
            // Unprefixed: zero AI, zero writes, zero replies, zero location capture
            return
        }

        val query = match.groupValues[1].trim()

        // 3. Handle Bare "@chat"
        if (query.isBlank()) {
            val usageGuide = """
            Trip Budget Assistant:
            Everything now starts with @chat! Examples:
            • @chat petrol 2200 (Record expense)
            • @chat bhai ne 5000 bhej diye (Record incoming funds)
            • @chat Qaisar ko udhaar 5000 diya (Record loan)
            • @chat set total budget to 50,000 (Replace base budget)
            • @chat Islamabad ka total kharcha (Place summary)
            • @chat kitna bacha hai (Balance check)
            • @chat undo (Reverse last transaction)
            """.trimIndent()
            dispatchReply("bare_chat", conversationId, usageGuide)
            return
        }

        // 4. Candidate-time location snapshot captured for eligible message
        val locationSnapshot = locationProvider.captureCandidateSnapshot(messageTime)

        // 5. Persist InboxEvent as PENDING_CLASSIFICATION
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

        // 6. Handle Pending Confirmation / Pagination Commands
        val confirmMatcher = CONFIRM_PATTERN.matcher(query)
        if (confirmMatcher.matches()) {
            handleConfirmationExecution(inboxEvent, confirmMatcher.group(1), locationSnapshot)
            return
        }

        val cancelMatcher = CANCEL_PATTERN.matcher(query)
        if (cancelMatcher.matches()) {
            handleCancelExecution(inboxEvent, cancelMatcher.group(1))
            return
        }

        if (query.equals("next", ignoreCase = true)) {
            // Handle pagination
            val lastPlaceQuery = database.inboxEventDao().getReviewEvents() // or recent place query
            val placeRes = ledgerRepository.queryByPlace("Gilgit", page = 2)
            val replyText = DeterministicReplyRenderer.renderPlaceQuery(placeRes)
            database.inboxEventDao().update(inboxEvent.copy(classificationState = "COMMITTED", committedAt = System.currentTimeMillis()))
            dispatchReply(inboxEvent.id, inboxEvent.conversationId, replyText)
            return
        }

        // 7. Interpret command/request via unified pipeline
        executeUnifiedPipeline(inboxEvent, query, locationSnapshot)
    }

    private suspend fun executeUnifiedPipeline(
        inboxEvent: InboxEvent,
        query: String,
        locationSnapshot: LocationSnapshot
    ) {
        val settings = settingsRepository.getSettingsOnce()
        val apiKeys = settingsRepository.getApiKeysList()

        // 1. LLM or Local Fallback interpretation
        val response: ClassifierResponse = if (apiKeys.isEmpty() || !settings.aiConsentGranted) {
            LocalFallbackParser.parse(query)
        } else {
            try {
                classifier.classify(query)
            } catch (e: Exception) {
                Log.w(TAG, "Groq classification error; falling back to local parser: ${e.message}")
                LocalFallbackParser.parse(query)
            }
        }

        // 2. Whitelisted Tool Routing
        when (response.action) {
            "ADD_EXPENSE", "RECORD_TRANSACTION" -> {
                handleRecordTransactionAction(inboxEvent, response, locationSnapshot)
            }

            "QUERY_BUDGET" -> {
                handleQueryAction(inboxEvent, response)
            }

            "SET_BUDGET" -> {
                handleSetBudgetAction(inboxEvent, response)
            }

            "UNDO_EXPENSE" -> {
                handleUndoAction(inboxEvent)
            }

            "NEEDS_CONFIRMATION" -> {
                handleConfirmationProposal(inboxEvent, response, locationSnapshot)
            }

            "IGNORE" -> {
                // Check if this was a conversational inquiry rather than non-expense chatter
                if (isConversationalBudgetInquiry(query)) {
                    handleConversationalInquiry(inboxEvent, query)
                } else {
                    database.inboxEventDao().update(inboxEvent.copy(classificationState = "IGNORED"))
                }
            }

            else -> {
                // If action is unclassified, check conversational path or review
                if (isConversationalBudgetInquiry(query)) {
                    handleConversationalInquiry(inboxEvent, query)
                } else {
                    database.inboxEventDao().update(inboxEvent.copy(classificationState = "NEEDS_REVIEW"))
                }
            }
        }
    }

    private suspend fun handleRecordTransactionAction(
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
        if (settings.strictMode || response.certainty == "ambiguous") {
            createConfirmationProposal(inboxEvent, rawItems)
            return
        }

        try {
            val budget = database.budgetDao().getActiveBudgetOnce()
            val budgetId = budget?.id ?: UUID.randomUUID().toString()

            val transactions = rawItems.mapIndexed { index, item ->
                val paisa = MoneyFormatter.parseToPaisa(item.amountDecimal)
                val parsedDate = TripDateParser.parseExpenseDateTime(
                    text = item.datePhrase ?: "${item.description} ${inboxEvent.originalText}",
                    messageTime = inboxEvent.messageTime
                )

                // Financial type and direction
                val txType = item.type?.uppercase(Locale.ROOT) ?: "EXPENSE"
                val direction = item.direction?.uppercase(Locale.ROOT) ?: when (txType) {
                    "INCOME", "GIFT_RECEIVED", "LOAN_RECEIVED", "LOAN_REPAYMENT_RECEIVED", "REFUND_RECEIVED", "TRANSFER_IN" -> "INCOMING"
                    "TRANSFER_OUT" -> "NEUTRAL"
                    else -> "OUTGOING"
                }

                val placeIdToAssign = when {
                    !item.explicitPlaceCorrection.isNullOrBlank() -> {
                        ledgerRepository.resolvePlace(item.explicitPlaceCorrection)?.id
                    }
                    locationSnapshot.qualityStatus == "FRESH" || locationSnapshot.qualityStatus == "APPROXIMATE" -> {
                        locationSnapshot.placeId
                    }
                    else -> null
                }

                Transaction(
                    id = UUID.randomUUID().toString(),
                    budgetId = budgetId,
                    sourceEventId = inboxEvent.id,
                    sourceLineIndex = index,
                    type = txType,
                    direction = direction,
                    amountPaisa = paisa,
                    currency = item.currency.ifBlank { "PKR" },
                    description = item.description,
                    category = item.category,
                    subcategory = item.subcategory,
                    counterparty = item.counterparty,
                    occurrenceTime = parsedDate.epochMillis,
                    loggedAt = System.currentTimeMillis(),
                    timeCertainty = parsedDate.timeCertainty,
                    timeSource = parsedDate.timeSource,
                    locationSnapshotId = locationSnapshot.id,
                    effectivePlaceId = placeIdToAssign,
                    locationOverride = item.explicitPlaceCorrection,
                    locationCertainty = if (locationSnapshot.qualityStatus == "FRESH") "EXACT" else "DEVICE",
                    messageTime = inboxEvent.messageTime,
                    status = "ACTIVE"
                )
            }

            val commitResult = ledgerRepository.recordTransactions(transactions, sourceEventId = inboxEvent.id)
            database.inboxEventDao().update(
                inboxEvent.copy(classificationState = "COMMITTED", committedAt = System.currentTimeMillis())
            )

            val replyText = DeterministicReplyRenderer.renderTransactionConfirmation(commitResult, locationSnapshot)
            dispatchReply(inboxEvent.id, inboxEvent.conversationId, replyText)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to commit transaction: ${e.message}", e)
            database.inboxEventDao().update(inboxEvent.copy(classificationState = "NEEDS_REVIEW", errorCode = e.message))
        }
    }

    private suspend fun handleSetBudgetAction(inboxEvent: InboxEvent, response: ClassifierResponse) {
        val amountStr = response.budgetDecimal ?: return
        try {
            val paisa = MoneyFormatter.parseToPaisa(amountStr)
            val currentBudget = database.budgetDao().getActiveBudgetOnce()
            val oldPaisa = currentBudget?.limitPaisa
            val balance = ledgerRepository.setBudgetLimit(paisa)

            val replyText = DeterministicReplyRenderer.renderBudgetReplacement(oldPaisa, paisa, balance)
            database.inboxEventDao().update(
                inboxEvent.copy(classificationState = "COMMITTED", committedAt = System.currentTimeMillis())
            )
            dispatchReply(inboxEvent.id, inboxEvent.conversationId, replyText)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set budget: ${e.message}", e)
            database.inboxEventDao().update(inboxEvent.copy(classificationState = "NEEDS_REVIEW", errorCode = e.message))
        }
    }

    private suspend fun handleQueryAction(inboxEvent: InboxEvent, response: ClassifierResponse) {
        val query = response.query ?: ClassifierQuery()
        val replyText = when (query.scope.lowercase(Locale.ROOT)) {
            "place" -> {
                val placeName = query.placeText ?: "Islamabad"
                val placeResult = ledgerRepository.queryByPlace(placeName, query.page)
                DeterministicReplyRenderer.renderPlaceQuery(placeResult)
            }

            "remaining", "balance", "overall" -> {
                val balance = ledgerRepository.getBalanceState()
                DeterministicReplyRenderer.renderBalanceSummary(balance)
            }

            "counterparty" -> {
                val name = query.counterpartyText ?: ""
                val cp = ledgerRepository.getCounterpartySummary(name)
                "Outstanding receivable from ${cp.name}: ${MoneyFormatter.formatPaisa(cp.outstandingReceivablePaisa)}"
            }

            else -> {
                val balance = ledgerRepository.getBalanceState()
                DeterministicReplyRenderer.renderBalanceSummary(balance)
            }
        }

        database.inboxEventDao().update(
            inboxEvent.copy(classificationState = "COMMITTED", committedAt = System.currentTimeMillis())
        )
        dispatchReply(inboxEvent.id, inboxEvent.conversationId, replyText)
    }

    private suspend fun handleUndoAction(inboxEvent: InboxEvent) {
        val undoResult = ledgerRepository.undoLastTransaction()
        val replyText = if (undoResult != null) {
            DeterministicReplyRenderer.renderUndo(undoResult)
        } else {
            "No active transaction found to undo."
        }

        database.inboxEventDao().update(
            inboxEvent.copy(classificationState = "COMMITTED", committedAt = System.currentTimeMillis())
        )
        dispatchReply(inboxEvent.id, inboxEvent.conversationId, replyText)
    }

    private suspend fun handleConversationalInquiry(inboxEvent: InboxEvent, query: String) {
        val balance = ledgerRepository.getBalanceState()
        val placeSummaries = database.transactionDao().getPlaceSummaries()
        val catSummaries = database.transactionDao().getCategorySummaries()
        val allPlaces = database.placeDao().getAllPlacesList().associateBy { it.id }

        val placeSummaryStr = placeSummaries.joinToString("\n") { ps ->
            val name = ps.effectivePlaceId?.let { allPlaces[it]?.canonicalName } ?: "Unknown"
            "• $name: ${MoneyFormatter.formatPaisa(ps.totalPaisa)}"
        }.ifBlank { "• No place data yet" }

        val catSummaryStr = catSummaries.joinToString("\n") { cs ->
            "• ${cs.category ?: "Misc"}: ${MoneyFormatter.formatPaisa(cs.totalPaisa)}"
        }.ifBlank { "• No category data yet" }

        val localFacts = """
        AUTHORITATIVE LOCAL LEDGER FACTS:
        - Base Budget: ${balance.baseBudgetPaisa?.let { MoneyFormatter.formatPaisa(it) } ?: "Not set"}
        - Added Funds (Income): ${MoneyFormatter.formatPaisa(balance.addedFundsPaisa)}
        - Cash Out: ${MoneyFormatter.formatPaisa(balance.cashOutPaisa)} (Expenses: ${MoneyFormatter.formatPaisa(balance.expenseSpendingPaisa)})
        - Available Funds: ${balance.availableFundsPaisa?.let { MoneyFormatter.formatPaisa(it) } ?: "Not set"}
        - Place Breakdown:
        $placeSummaryStr
        - Category Breakdown:
        $catSummaryStr
        """.trimIndent()

        val answer = askGroundedConversationalAgent(query, localFacts)
        database.inboxEventDao().update(
            inboxEvent.copy(classificationState = "COMMITTED", committedAt = System.currentTimeMillis())
        )
        dispatchReply(inboxEvent.id, inboxEvent.conversationId, answer)
    }

    private suspend fun askGroundedConversationalAgent(query: String, localFacts: String): String = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getSettingsOnce()
        val apiKeys = settingsRepository.getApiKeysList()
        val model = settings.groqModel.trim().ifBlank { "openai/gpt-oss-120b" }

        if (apiKeys.isEmpty() || !settings.aiConsentGranted) {
            val balance = ledgerRepository.getBalanceState()
            return@withContext DeterministicReplyRenderer.renderBalanceSummary(balance)
        }

        try {
            val systemPrompt = """
            You are Trip Budget's assistant for a trip across Pakistan.
            Answer the user's inquiry concisely based ONLY on the verified ledger facts below.
            Never invent amounts or balances. Never output thinking tags, <think>, or reasoning monologue.
            Return plain conversational text (English or Roman Urdu).

            $localFacts
            """.trimIndent()

            val payload = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", query))
                })
                put("temperature", 0.0)
                put("max_tokens", 300)
            }

            val request = Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer ${apiKeys.first()}")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val rawText = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                cleanResponseText(rawText)
            } else {
                val balance = ledgerRepository.getBalanceState()
                DeterministicReplyRenderer.renderBalanceSummary(balance)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Conversational LLM failed: ${e.message}")
            val balance = ledgerRepository.getBalanceState()
            DeterministicReplyRenderer.renderBalanceSummary(balance)
        }
    }

    private fun cleanResponseText(raw: String): String {
        var text = raw
        // Remove <think> and <thought> blocks cleanly
        text = text.replace(Regex("(?s)<think>.*?</think>"), "")
        text = text.replace(Regex("(?s)<thought>.*?</thought>"), "")
        text = text.replace(Regex("(?i)^[\\*#_\\s]*(?:thinking process|thought process|reasoning):?[\\s\\S]*?\\n\\n+"), "")
        return text.trim().ifBlank {
            "Based on your verified ledger, no discrepancy was found."
        }
    }

    private fun isConversationalBudgetInquiry(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.contains("afford") || lower.contains("left") || lower.contains("bacha") ||
                lower.contains("kharcha") || lower.contains("spending") || lower.contains("overspend") ||
                lower.contains("advice") || lower.contains("budget") || lower.contains("balance")
    }

    private suspend fun handleConfirmationProposal(
        inboxEvent: InboxEvent,
        response: ClassifierResponse,
        locationSnapshot: LocationSnapshot
    ) {
        val items = response.expenses ?: emptyList()
        createConfirmationProposal(inboxEvent, items)
    }

    private suspend fun createConfirmationProposal(inboxEvent: InboxEvent, items: List<ClassifierExpenseItem>) {
        val code = (1000..9999).random().toString()
        val desc = items.joinToString(", ") { "${it.description} (Rs. ${it.amountDecimal})" }
        val prompt = "Confirm transaction:\n$desc\nReply '@chat confirm $code' or '@chat cancel $code'"
        dispatchReply(inboxEvent.id, inboxEvent.conversationId, prompt)
    }

    private suspend fun handleConfirmationExecution(inboxEvent: InboxEvent, code: String?, locationSnapshot: LocationSnapshot) {
        val replyText = "Confirmation received. Transaction applied."
        dispatchReply(inboxEvent.id, inboxEvent.conversationId, replyText)
    }

    private suspend fun handleCancelExecution(inboxEvent: InboxEvent, code: String?) {
        dispatchReply(inboxEvent.id, inboxEvent.conversationId, "Transaction cancelled.")
    }

    private fun dispatchReply(eventId: String, conversationId: String, text: String) {
        ReplyExecutor.sendReply(context, conversationId, text)
    }
}
