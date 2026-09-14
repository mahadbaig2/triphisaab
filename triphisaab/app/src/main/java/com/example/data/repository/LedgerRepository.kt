package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.TripBudgetDatabase
import com.example.data.model.*
import com.example.engine.MoneyFormatter
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class LedgerRepository(
    val database: TripBudgetDatabase,
    private val settingsRepository: SettingsRepository
) {
    val categoryTaxonomyManager = CategoryTaxonomyManager(database.categoryDao(), database.subcategoryDao())

    val activeBudgetFlow: Flow<Budget?> = database.budgetDao().getActiveBudget()
    val allActiveTransactionsFlow: Flow<List<Transaction>> = database.transactionDao().getAllActiveTransactions()
    val recentTransactionsFlow: Flow<List<Transaction>> = database.transactionDao().getRecentTransactions(10)
    val addedFundsFlow: Flow<Long?> = database.transactionDao().getAddedFundsActive()
    val cashOutFlow: Flow<Long?> = database.transactionDao().getCashOutActive()
    val expenseSpendingFlow: Flow<Long?> = database.transactionDao().getExpenseSpendingActive()
    val allPlacesFlow: Flow<List<Place>> = database.placeDao().getAllPlaces()
    val allCategoriesFlow: Flow<List<Category>> = database.categoryDao().getAllCategories()
    val reviewEventsFlow: Flow<List<InboxEvent>> = database.inboxEventDao().getReviewEvents()
    val pendingReviewCountFlow: Flow<Int> = database.inboxEventDao().countPendingReview()

    // Backwards compatibility flows
    val allActiveExpensesFlow: Flow<List<Expense>> = database.expenseDao().getAllActiveExpenses()
    val recentExpensesFlow: Flow<List<Expense>> = database.expenseDao().getRecentExpenses(10)
    val totalSpentFlow: Flow<Long?> = database.expenseDao().getTotalSpentActive()

    data class BalanceState(
        val baseBudgetPaisa: Long?,
        val addedFundsPaisa: Long,
        val cashOutPaisa: Long,
        val availableFundsPaisa: Long?,
        val expenseSpendingPaisa: Long
    )

    data class CommitTransactionResult(
        val committedTransactions: List<Transaction>,
        val batchTotalPaisa: Long,
        val balanceState: BalanceState
    )

    data class UndoTransactionResult(
        val reversedTransactions: List<Transaction>,
        val reversedTotalPaisa: Long,
        val balanceState: BalanceState
    )

    data class PlaceQueryResult(
        val placeName: String,
        val isDistrict: Boolean,
        val totalSpentPaisa: Long,
        val incomingPaisa: Long,
        val count: Int,
        val page: Int,
        val totalPages: Int,
        val transactions: List<Transaction>,
        val categoryBreakdown: Map<String, Long>,
        val dateRange: String?,
        val hasRecords: Boolean
    )

    data class CounterpartySummary(
        val name: String,
        val outstandingReceivablePaisa: Long,
        val transactions: List<Transaction>
    )

    suspend fun getBalanceState(): BalanceState {
        val budget = database.budgetDao().getActiveBudgetOnce()
        val baseBudget = budget?.limitPaisa
        val added = database.transactionDao().getAddedFundsActiveOnce() ?: 0L
        val cashOut = database.transactionDao().getCashOutActiveOnce() ?: 0L
        val available = baseBudget?.let { it + added - cashOut }
        val expenseSpending = database.transactionDao().getExpenseSpendingActiveOnce() ?: 0L

        return BalanceState(
            baseBudgetPaisa = baseBudget,
            addedFundsPaisa = added,
            cashOutPaisa = cashOut,
            availableFundsPaisa = available,
            expenseSpendingPaisa = expenseSpending
        )
    }

    suspend fun recordTransactions(
        transactions: List<Transaction>,
        sourceEventId: String? = null,
        origin: String = "VALIDATED_AI"
    ): CommitTransactionResult {
        require(transactions.isNotEmpty()) { "Cannot commit empty transaction list" }

        // Resolve category taxonomy for each transaction
        val resolvedTransactions = transactions.map { tx ->
            val resolved = categoryTaxonomyManager.resolve(tx.description, tx.category, tx.subcategory)
            tx.copy(
                categoryId = resolved.categoryId,
                category = resolved.categoryName,
                subcategoryId = resolved.subcategoryId,
                subcategory = resolved.subcategoryName
            )
        }

        return database.withTransaction {
            val budget = database.budgetDao().getActiveBudgetOnce()
                ?: Budget(name = "Motorcycle Trip", currency = "PKR").also {
                    database.budgetDao().insertBudget(it)
                }

            val budgetId = budget.id
            val prepared = resolvedTransactions.mapIndexed { index, tx ->
                tx.copy(
                    budgetId = budgetId,
                    sourceEventId = sourceEventId,
                    sourceLineIndex = index,
                    status = "ACTIVE"
                )
            }

            database.transactionDao().insertTransactions(prepared)

            // Also mirror EXPENSE records into expenses table for backward-compatible views
            val expenseMirror = prepared.filter { it.type == "EXPENSE" }.map { tx ->
                Expense(
                    id = tx.id,
                    budgetId = tx.budgetId,
                    sourceEventId = tx.sourceEventId,
                    sourceLineIndex = tx.sourceLineIndex,
                    amountPaisa = tx.amountPaisa,
                    currency = tx.currency,
                    description = tx.description,
                    category = tx.category,
                    subcategory = tx.subcategory,
                    categoryId = tx.categoryId,
                    subcategoryId = tx.subcategoryId,
                    expenseTime = tx.occurrenceTime,
                    loggedAt = tx.loggedAt,
                    timeCertainty = tx.timeCertainty,
                    timeSource = tx.timeSource,
                    locationSnapshotId = tx.locationSnapshotId,
                    effectivePlaceId = tx.effectivePlaceId,
                    locationOverride = tx.locationOverride,
                    locationCertainty = tx.locationCertainty,
                    messageTime = tx.messageTime,
                    status = tx.status,
                    reversedAt = tx.reversedAt
                )
            }
            if (expenseMirror.isNotEmpty()) {
                database.expenseDao().insertExpenses(expenseMirror)
            }

            val batchTotal = prepared.sumOf { it.amountPaisa }
            val balance = getBalanceState()

            // Audit
            database.auditDao().insert(
                AuditEntry(
                    actionType = "RECORD_TRANSACTIONS",
                    entityId = sourceEventId ?: prepared.first().id,
                    afterJson = "count=${prepared.size}, batchTotal=$batchTotal, types=${prepared.map { it.type }}",
                    origin = origin
                )
            )

            CommitTransactionResult(
                committedTransactions = prepared,
                batchTotalPaisa = batchTotal,
                balanceState = balance
            )
        }
    }

    suspend fun undoLastTransaction(): UndoTransactionResult? {
        return database.withTransaction {
            val lastTx = database.transactionDao().getLastActiveTransaction() ?: return@withTransaction null

            val affected = if (lastTx.sourceEventId != null) {
                database.transactionDao().getTransactionsForEvent(lastTx.sourceEventId)
                    .filter { it.status == "ACTIVE" }
            } else {
                listOf(lastTx)
            }

            val timestamp = System.currentTimeMillis()
            affected.forEach {
                database.transactionDao().reverseTransaction(it.id, timestamp)
                database.expenseDao().reverseExpense(it.id, timestamp)
            }

            val reversedTotal = affected.sumOf { it.amountPaisa }
            val balance = getBalanceState()

            database.auditDao().insert(
                AuditEntry(
                    actionType = "UNDO_TRANSACTION",
                    entityId = lastTx.sourceEventId ?: lastTx.id,
                    beforeJson = "reversedCount=${affected.size}, amount=$reversedTotal",
                    afterJson = "availableFunds=${balance.availableFundsPaisa}",
                    origin = "USER"
                )
            )

            UndoTransactionResult(
                reversedTransactions = affected,
                reversedTotalPaisa = reversedTotal,
                balanceState = balance
            )
        }
    }

    suspend fun resolvePlace(placeText: String): Place? {
        val clean = placeText.trim().lowercase(Locale.ROOT)
        val allPlaces = database.placeDao().getAllPlacesList()

        // 1. Direct canonical match
        val direct = allPlaces.firstOrNull { it.canonicalName.lowercase(Locale.ROOT) == clean }
        if (direct != null) return direct

        // 2. Alias match
        for (place in allPlaces) {
            val aliases = place.aliasesJson.lowercase(Locale.ROOT)
            if (aliases.contains("\"$clean\"") || (clean == "isb" && place.canonicalName.equals("Islamabad", true)) || (clean == "pindi" && place.canonicalName.equals("Rawalpindi", true))) {
                return place
            }
        }

        // 3. Locality vs District distinction
        if (clean == "gilgit" || clean == "gilgit city") {
            return allPlaces.firstOrNull { it.canonicalName == "Gilgit" && it.type == "LOCALITY" }
        }
        if (clean.contains("district gilgit") || clean.contains("gilgit district")) {
            return allPlaces.firstOrNull { it.canonicalName == "Gilgit District" && it.type == "DISTRICT" }
        }

        return null
    }

    suspend fun queryByPlace(placeText: String, page: Int = 1, pageSize: Int = 15): PlaceQueryResult {
        val resolvedPlace = resolvePlace(placeText)
        val placeName = resolvedPlace?.canonicalName ?: placeText.trim().replaceFirstChar { it.uppercase() }
        val placeId = resolvedPlace?.id ?: ""
        val isDistrict = resolvedPlace?.type == "DISTRICT"

        val totalSpent = database.transactionDao().getTotalSpentActiveByPlace(placeId, placeName) ?: 0L
        val incoming = database.transactionDao().getIncomingActiveByPlace(placeId, placeName) ?: 0L
        val count = database.transactionDao().countActiveTransactionsByPlace(placeId, placeName)

        if (count == 0 && totalSpent == 0L && incoming == 0L) {
            return PlaceQueryResult(
                placeName = placeName,
                isDistrict = isDistrict,
                totalSpentPaisa = 0L,
                incomingPaisa = 0L,
                count = 0,
                page = 1,
                totalPages = 1,
                transactions = emptyList(),
                categoryBreakdown = emptyMap(),
                dateRange = null,
                hasRecords = false
            )
        }

        val offset = (page - 1) * pageSize
        val pagedTransactions = database.transactionDao().getTransactionsByPlacePaged(placeId, placeName, pageSize, offset)
        val allPlaceTransactions = database.transactionDao().getTransactionsByPlaceAll(placeId, placeName)
        val totalPages = if (count == 0) 1 else ((count + pageSize - 1) / pageSize)

        // Category breakdown
        val catBreakdown = mutableMapOf<String, Long>()
        allPlaceTransactions.filter { it.direction == "OUTGOING" }.forEach { tx ->
            val cat = tx.category ?: "Miscellaneous"
            catBreakdown[cat] = (catBreakdown[cat] ?: 0L) + tx.amountPaisa
        }

        // Date range
        val dateRange = if (allPlaceTransactions.isNotEmpty()) {
            val minTime = allPlaceTransactions.minOf { it.occurrenceTime }
            val maxTime = allPlaceTransactions.maxOf { it.occurrenceTime }
            val sdf = SimpleDateFormat("d MMM", Locale.getDefault())
            if (minTime == maxTime) sdf.format(Date(minTime))
            else "${sdf.format(Date(minTime))} – ${sdf.format(Date(maxTime))}"
        } else null

        return PlaceQueryResult(
            placeName = placeName,
            isDistrict = isDistrict,
            totalSpentPaisa = totalSpent,
            incomingPaisa = incoming,
            count = count,
            page = page,
            totalPages = totalPages,
            transactions = pagedTransactions,
            categoryBreakdown = catBreakdown,
            dateRange = dateRange,
            hasRecords = true
        )
    }

    suspend fun getCounterpartySummary(name: String): CounterpartySummary {
        val clean = name.trim()
        val outstanding = database.transactionDao().getOutstandingLoanReceivable(clean) ?: 0L
        val txs = database.transactionDao().getTransactionsByCounterparty(clean)
        return CounterpartySummary(
            name = clean,
            outstandingReceivablePaisa = outstanding,
            transactions = txs
        )
    }

    suspend fun setBudgetLimit(limitPaisa: Long?): BalanceState {
        val budget = database.budgetDao().getActiveBudgetOnce()
            ?: Budget(name = "Motorcycle Trip", currency = "PKR").also {
                database.budgetDao().insertBudget(it)
            }
        database.budgetDao().updateLimit(budget.id, limitPaisa, System.currentTimeMillis())
        database.auditDao().insert(
            AuditEntry(
                actionType = "SET_BUDGET",
                entityId = budget.id,
                afterJson = "limitPaisa=$limitPaisa",
                origin = "USER"
            )
        )
        return getBalanceState()
    }
}
