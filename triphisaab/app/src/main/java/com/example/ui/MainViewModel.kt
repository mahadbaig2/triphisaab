package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TripBudgetApplication
import com.example.data.db.TripBudgetDatabase
import com.example.data.model.*
import com.example.engine.MoneyFormatter
import com.example.location.LocationTrackingService
import com.example.notifications.ListenerDiagnostics
import com.example.notifications.PairingManager
import com.example.notifications.ReplyExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TripBudgetApplication
    val ledgerRepository = app.ledgerRepository
    val settingsRepository = app.settingsRepository
    val pairingManager = app.pairingManager

    // UI state flows
    val activeBudget = ledgerRepository.activeBudgetFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val totalSpent = ledgerRepository.totalSpentFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val addedFunds = ledgerRepository.addedFundsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val cashOut = ledgerRepository.cashOutFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val expenseSpending = ledgerRepository.expenseSpendingFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val recentExpenses = ledgerRepository.recentExpensesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allActiveExpenses = ledgerRepository.allActiveExpensesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allPlaces = ledgerRepository.allPlacesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allCategories = ledgerRepository.allCategoriesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reviewEvents = ledgerRepository.reviewEventsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pendingReviewCount = ledgerRepository.pendingReviewCountFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val appSettings = settingsRepository.settingsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val pairedConversation = settingsRepository.pairedConversationFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isTrackingActive = LocationTrackingService.isSessionActive
    val diagnosticsState = ListenerDiagnostics.state

    // Location Dashboard Snapshot
    private val _latestLocationSnapshot = MutableStateFlow<LocationSnapshot?>(null)
    val latestLocationSnapshot: StateFlow<LocationSnapshot?> = _latestLocationSnapshot.asStateFlow()
    val isRefreshingLocation = MutableStateFlow(false)

    fun refreshLocation() {
        viewModelScope.launch {
            isRefreshingLocation.value = true
            try {
                val snapshot = app.locationProvider.captureCandidateSnapshot(System.currentTimeMillis())
                _latestLocationSnapshot.value = snapshot
                showMessage("Location updated: ${snapshot.locality ?: "Captured"}")
            } catch (e: Exception) {
                showMessage("Failed to refresh location: ${e.message}")
            } finally {
                isRefreshingLocation.value = false
            }
        }
    }

    private val groqClassifier = com.example.ai.GroqClassifier(settingsRepository)
    val modelTestState = MutableStateFlow<com.example.ai.TestModelResult?>(null)
    val isTestingModel = MutableStateFlow(false)

    // Pairing Wizard states
    val activePairingCode = pairingManager.activePairingCode
    val codeExpiresAt = pairingManager.codeExpiresAt
    val discoveredMetadata = pairingManager.discoveredMetadata

    // UI Feedback state
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    fun showMessage(msg: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(msg)
        }
    }

    fun startTrackingSession(context: Context) {
        LocationTrackingService.startService(context)
        showMessage("Location tracking session started")
    }

    fun stopTrackingSession(context: Context) {
        LocationTrackingService.stopService(context)
        showMessage("Location tracking session stopped")
    }

    fun generatePairingCode(): String {
        return pairingManager.generateNewPairingCode()
    }

    fun approvePairing() {
        viewModelScope.launch {
            val approved = pairingManager.approvePairing()
            if (approved) {
                showMessage("Paired successfully! WhatsApp direct conversation isolated.")
            } else {
                showMessage("Pairing failed: Metadata insufficient to isolate chat.")
            }
        }
    }

    fun cancelPairing() {
        pairingManager.cancelPairing()
    }

    fun testSendReply(context: Context, customText: String? = null) {
        viewModelScope.launch {
            val paired = pairedConversation.value
            val convId = paired?.conversationId ?: discoveredMetadata.value?.shortcutId ?: discoveredMetadata.value?.rawKey ?: "demo_conv"
            val text = customText ?: "Trip Budget connection test verified ✓"
            val success = ReplyExecutor.sendReply(context, convId, text)
            if (success) {
                showMessage("Test reply action invoked on active notification!")
            } else {
                showMessage("RemoteInput reply action not active in memory. Send a WhatsApp message first.")
            }
        }
    }

    fun unpairConversation() {
        viewModelScope.launch {
            settingsRepository.clearPairedConversation()
            showMessage("Personal number unpaired.")
        }
    }

    fun saveApiKey(key: String) {
        saveApiKeys(listOf(key).filter { it.isNotBlank() })
    }

    fun saveApiKeys(keys: List<String>) {
        viewModelScope.launch {
            val combined = keys.map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")
            settingsRepository.setApiKey(combined)
            val count = keys.count { it.isNotBlank() }
            showMessage(if (count == 0) "API keys cleared" else "$count Groq API key(s) encrypted & saved")
        }
    }

    suspend fun getDecryptedApiKey(): String? {
        return settingsRepository.getApiKey()
    }

    suspend fun getDecryptedApiKeys(): List<String> {
        return settingsRepository.getApiKeysList()
    }

    fun setAiConsent(granted: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAiConsent(granted)
        }
    }

    fun setGroqModel(model: String) {
        viewModelScope.launch {
            settingsRepository.setGroqModel(model)
            showMessage("Active model: $model")
        }
    }

    fun testModel(modelId: String, apiKey: String?) {
        viewModelScope.launch {
            isTestingModel.value = true
            modelTestState.value = null
            try {
                val res = groqClassifier.testModel(modelId, apiKey)
                modelTestState.value = res
            } catch (e: Exception) {
                modelTestState.value = com.example.ai.TestModelResult.Error(e.message ?: "Unknown test error")
            } finally {
                isTestingModel.value = false
            }
        }
    }

    fun clearModelTestState() {
        modelTestState.value = null
    }

    fun updateExpenseCategory(expenseId: String, newCategory: String, newSubcategory: String?) {
        viewModelScope.launch {
            ledgerRepository.correctExpense(
                expenseId = expenseId,
                newCategory = newCategory,
                newSubcategory = newSubcategory
            )
            showMessage("Category updated to $newCategory${newSubcategory?.let { " / $it" } ?: ""}")
        }
    }

    fun setStrictMode(strict: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStrictMode(strict)
            showMessage(if (strict) "Strict confirmation mode enabled" else "Default automatic logging enabled")
        }
    }

    fun setBudgetLimit(amountPaisa: Long?) {
        viewModelScope.launch {
            ledgerRepository.setBudgetLimit(amountPaisa)
            showMessage("Budget limit updated to ${amountPaisa?.let { MoneyFormatter.formatPaisa(it) } ?: "None"}")
        }
    }

    fun recordManualExpense(description: String, amountDecimal: String, placeId: String?) {
        viewModelScope.launch {
            try {
                val paisa = MoneyFormatter.parseToPaisa(amountDecimal)
                val budget = activeBudget.value ?: Budget()
                val exp = Expense(
                    budgetId = budget.id,
                    amountPaisa = paisa,
                    description = description.trim(),
                    effectivePlaceId = placeId,
                    status = "ACTIVE"
                )
                val res = ledgerRepository.recordExpenses(listOf(exp), origin = "USER")
                showMessage("Recorded: ${exp.description} — ${MoneyFormatter.formatPaisa(paisa)}")
            } catch (e: Exception) {
                showMessage("Error: ${e.message}")
            }
        }
    }

    fun undoLastExpense() {
        viewModelScope.launch {
            val res = ledgerRepository.undoLastExpense()
            if (res != null) {
                showMessage("Reversed ${res.reversedExpenses.size} item(s) (${MoneyFormatter.formatPaisa(res.reversedTotalPaisa)})")
            } else {
                showMessage("No active expense found to undo")
            }
        }
    }

    fun reverseExpense(id: String) {
        viewModelScope.launch {
            app.database.expenseDao().reverseExpense(id)
            showMessage("Expense reversed")
        }
    }

    fun correctExpense(
        id: String,
        desc: String?,
        amtPaisa: Long?,
        placeId: String?,
        category: String? = null,
        subcategory: String? = null
    ) {
        viewModelScope.launch {
            val ok = ledgerRepository.correctExpense(
                expenseId = id,
                newDescription = desc,
                newAmountPaisa = amtPaisa,
                newPlaceId = placeId,
                newCategory = category,
                newSubcategory = subcategory
            )
            if (ok) showMessage("Expense updated") else showMessage("Expense not found")
        }
    }

    fun resolveReviewItem(event: InboxEvent, approveAsExpense: Boolean, description: String? = null, amountDecimal: String? = null) {
        viewModelScope.launch {
            if (approveAsExpense && !description.isNullOrBlank() && !amountDecimal.isNullOrBlank()) {
                try {
                    val paisa = MoneyFormatter.parseToPaisa(amountDecimal)
                    val budget = activeBudget.value ?: Budget()
                    val exp = Expense(
                        budgetId = budget.id,
                        sourceEventId = event.id,
                        amountPaisa = paisa,
                        description = description,
                        expenseTime = event.messageTime ?: System.currentTimeMillis()
                    )
                    ledgerRepository.recordExpenses(listOf(exp), sourceEventId = event.id, origin = "USER")
                    app.database.inboxEventDao().update(event.copy(classificationState = "COMMITTED", committedAt = System.currentTimeMillis()))
                    showMessage("Expense recorded from review")
                } catch (e: Exception) {
                    showMessage("Error: ${e.message}")
                }
            } else {
                // Ignore item
                app.database.inboxEventDao().update(event.copy(classificationState = "IGNORED"))
                showMessage("Item marked as ignored")
            }
        }
    }

    /**
     * Injects a synthetic candidate notification event for testing in emulator.
     * Clearly records in diagnostics that it was generated by a demo fixture.
     */
    fun injectSyntheticCandidate(text: String, simulatePairing: Boolean = false) {
        viewModelScope.launch {
            val pkg = "com.whatsapp.w4b"
            val key = "demo_notif_${System.currentTimeMillis()}"
            val shortcutId = "demo_personal_shortcut_492"
            val senderName = "Personal Number"

            ListenerDiagnostics.recordNotification(
                pkg = pkg,
                hasShortcutId = true,
                hasPersonMetadata = true,
                hasRemoteInputReply = false,
                hasMessagingStyle = true,
                metadataDump = "[SYNTHETIC DEMO FIXTURE] text='$text', shortcutId='$shortcutId'",
                isDemoFixture = true
            )

            if (simulatePairing) {
                pairingManager.checkCandidateForPairing(
                    pkg = pkg,
                    text = text,
                    rawKey = key,
                    conversationTitle = "Personal (Self)",
                    senderName = senderName,
                    shortcutId = shortcutId,
                    senderPersonKey = "person_key_92300",
                    subText = null,
                    isGroup = false
                )
                showMessage("[Demo Fixture] Pairing candidate detected!")
            } else {
                val paired = pairedConversation.value
                val convId = paired?.conversationId ?: shortcutId
                app.unifiedAgent.enqueueEvent(
                    conversationId = convId,
                    eventIdentity = "demo_event_${System.currentTimeMillis()}",
                    messageText = text,
                    messageTime = System.currentTimeMillis()
                )
                showMessage("[Demo Fixture] Processed via UnifiedAgent: '$text'")
            }
        }
    }

    fun exportExpensesCsv(context: Context): String {
        val expenses = allActiveExpenses.value
        val placesMap = allPlaces.value.associateBy { it.id }
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val sb = StringBuilder()
        sb.append("ID,Date,Description,Amount_PKR,Amount_Paisa,Location,Status\n")
        expenses.forEach { exp ->
            val placeName = placesMap[exp.effectivePlaceId]?.canonicalName ?: "Unassigned"
            val dateStr = sdf.format(Date(exp.expenseTime))
            val amountPkr = MoneyFormatter.toDecimalString(exp.amountPaisa)
            sb.append("\"${exp.id}\",\"$dateStr\",\"${exp.description.replace("\"", "\"\"")}\",$amountPkr,${exp.amountPaisa},\"$placeName\",${exp.status}\n")
        }
        return sb.toString()
    }

    fun resetAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            app.database.clearAllTables()
            TripBudgetDatabase.seedInitialData(app.database)
            showMessage("All data reset to initial state")
        }
    }
}
