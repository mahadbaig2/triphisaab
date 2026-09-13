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
    val allActiveExpensesFlow: Flow<List<Expense>> = database.expenseDao().getAllActiveExpenses()
    val recentExpensesFlow: Flow<List<Expense>> = database.expenseDao().getRecentExpenses(10)
    val totalSpentFlow: Flow<Long?> = database.expenseDao().getTotalSpentActive()
    val allPlacesFlow: Flow<List<Place>> = database.placeDao().getAllPlaces()
    val allCategoriesFlow: Flow<List<Category>> = database.categoryDao().getAllCategories()
    val reviewEventsFlow: Flow<List<InboxEvent>> = database.inboxEventDao().getReviewEvents()
    val pendingReviewCountFlow: Flow<Int> = database.inboxEventDao().countPendingReview()

    data class CommitResult(
        val committedExpenses: List<Expense>,
        val batchTotalPaisa: Long,
        val overallTotalSpentPaisa: Long,
        val budgetLimitPaisa: Long?,
        val remainingPaisa: Long?,
        val isOverBudget: Boolean
    )

    data class UndoResult(
        val reversedExpenses: List<Expense>,
        val reversedTotalPaisa: Long,
        val newOverallTotalSpentPaisa: Long,
        val budgetLimitPaisa: Long?,
        val remainingPaisa: Long?
    )

    data class PlaceQueryResult(
        val placeName: String,
        val isDistrict: Boolean,
        val totalSpentPaisa: Long,
        val count: Int,
        val page: Int,
        val totalPages: Int,
        val expenses: List<Expense>,
        val hasMore: Boolean
    )

    data class GeneralQueryResult(
        val scope: String, // "overall", "today", "recent", "remaining", "category"
        val totalSpentPaisa: Long,
        val count: Int,
        val budgetLimitPaisa: Long?,
        val remainingPaisa: Long?,
        val isOverBudget: Boolean,
        val expenses: List<Expense>
    )

    suspend fun recordExpenses(
        expenses: List<Expense>,
        sourceEventId: String? = null,
        origin: String = "VALIDATED_AI"
    ): CommitResult {
        require(expenses.isNotEmpty()) { "Cannot commit empty expense list" }

        // Resolve taxonomy for each expense
        val resolvedExpenses = expenses.map { exp ->
            val resolved = categoryTaxonomyManager.resolve(exp.description, exp.category, exp.subcategory)
            exp.copy(
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
            val preparedExpenses = resolvedExpenses.mapIndexed { index, exp ->
                exp.copy(
                    budgetId = budgetId,
                    sourceEventId = sourceEventId,
                    sourceLineIndex = index,
                    status = "ACTIVE"
                )
            }

            database.expenseDao().insertExpenses(preparedExpenses)

            val batchTotalPaisa = preparedExpenses.sumOf { it.amountPaisa }
            val overallTotal = database.expenseDao().getTotalSpentActiveOnce() ?: batchTotalPaisa
            val limit = budget.limitPaisa
            val remaining = limit?.let { it - overallTotal }
            val isOverBudget = remaining != null && remaining < 0

            // Audit
            database.auditDao().insert(
                AuditEntry(
                    actionType = "RECORD_EXPENSES",
                    entityId = sourceEventId ?: preparedExpenses.first().id,
                    afterJson = "count=${preparedExpenses.size}, batchTotal=$batchTotalPaisa, overall=$overallTotal",
                    origin = origin
                )
            )

            CommitResult(
                committedExpenses = preparedExpenses,
                batchTotalPaisa = batchTotalPaisa,
                overallTotalSpentPaisa = overallTotal,
                budgetLimitPaisa = limit,
                remainingPaisa = remaining,
                isOverBudget = isOverBudget
            )
        }
    }

    suspend fun undoLastExpense(): UndoResult? {
        return database.withTransaction {
            val lastExpense = database.expenseDao().getLastActiveExpense() ?: return@withTransaction null

            val affectedExpenses = if (lastExpense.sourceEventId != null) {
                database.expenseDao().getExpensesForEvent(lastExpense.sourceEventId)
                    .filter { it.status == "ACTIVE" }
            } else {
                listOf(lastExpense)
            }

            val timestamp = System.currentTimeMillis()
            affectedExpenses.forEach {
                database.expenseDao().reverseExpense(it.id, timestamp)
            }

            val reversedTotal = affectedExpenses.sumOf { it.amountPaisa }
            val newOverallTotal = database.expenseDao().getTotalSpentActiveOnce() ?: 0L
            val budget = database.budgetDao().getActiveBudgetOnce()
            val limit = budget?.limitPaisa
            val remaining = limit?.let { it - newOverallTotal }

            database.auditDao().insert(
                AuditEntry(
                    actionType = "UNDO_EXPENSE",
                    entityId = lastExpense.sourceEventId ?: lastExpense.id,
                    beforeJson = "reversedCount=${affectedExpenses.size}, amount=$reversedTotal",
                    afterJson = "newOverallTotal=$newOverallTotal",
                    origin = "USER"
                )
            )

            UndoResult(
                reversedExpenses = affectedExpenses,
                reversedTotalPaisa = reversedTotal,
                newOverallTotalSpentPaisa = newOverallTotal,
                budgetLimitPaisa = limit,
                remainingPaisa = remaining
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
            if (aliases.contains("\"$clean\"")) {
                return place
            }
        }

        // 3. Fallback check: if user asked "Gilgit" specifically, never conflate with "Gilgit District"
        if (clean == "gilgit" || clean == "gilgit city") {
            return allPlaces.firstOrNull { it.canonicalName == "Gilgit" && it.type == "LOCALITY" }
        }
        if (clean.contains("district gilgit") || clean.contains("gilgit district")) {
            return allPlaces.firstOrNull { it.canonicalName == "Gilgit District" && it.type == "DISTRICT" }
        }

        return null
    }

    suspend fun queryByPlace(placeText: String, page: Int = 1, pageSize: Int = 15): PlaceQueryResult? {
        val resolvedPlace = resolvePlace(placeText) ?: return null
        val placeId = resolvedPlace.id
        val placeName = resolvedPlace.canonicalName
        val totalSpent = database.expenseDao().getTotalSpentActiveByPlace(placeId, placeName) ?: 0L
        val count = database.expenseDao().countActiveExpensesByPlace(placeId, placeName)
        val offset = (page - 1) * pageSize
        val pagedExpenses = database.expenseDao().getExpensesByPlacePaged(placeId, placeName, pageSize, offset)
        val totalPages = if (count == 0) 1 else ((count + pageSize - 1) / pageSize)

        return PlaceQueryResult(
            placeName = resolvedPlace.canonicalName,
            isDistrict = resolvedPlace.type == "DISTRICT",
            totalSpentPaisa = totalSpent,
            count = count,
            page = page,
            totalPages = totalPages,
            expenses = pagedExpenses,
            hasMore = page < totalPages
        )
    }

    suspend fun queryGeneral(scope: String, categoryName: String? = null): GeneralQueryResult {
        val budget = database.budgetDao().getActiveBudgetOnce()
        val limit = budget?.limitPaisa

        val tz = TimeZone.getTimeZone("Asia/Karachi")
        val cal = Calendar.getInstance(tz)

        val expenses: List<Expense>
        val totalSpent: Long

        when (scope.lowercase(Locale.ROOT)) {
            "today" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfDay = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val endOfDay = cal.timeInMillis

                val list = database.expenseDao().getExpensesBetween(startOfDay, endOfDay)
                if (categoryName != null) {
                    val filtered = list.filter { it.category?.contains(categoryName, ignoreCase = true) == true }
                    totalSpent = filtered.sumOf { it.amountPaisa }
                    expenses = filtered
                } else {
                    totalSpent = database.expenseDao().getTotalSpentActiveBetween(startOfDay, endOfDay) ?: 0L
                    expenses = list
                }
            }
            "category" -> {
                val targetCat = categoryName ?: "Miscellaneous"
                val list = database.expenseDao().getAllExpensesList().filter {
                    it.status == "ACTIVE" && (it.category?.contains(targetCat, ignoreCase = true) == true || targetCat.contains(it.category ?: "", ignoreCase = true))
                }
                totalSpent = list.sumOf { it.amountPaisa }
                expenses = list
            }
            else -> { // overall, recent, remaining
                totalSpent = database.expenseDao().getTotalSpentActiveOnce() ?: 0L
                expenses = database.expenseDao().getAllExpensesList().filter { it.status == "ACTIVE" }.take(15)
            }
        }

        val remaining = limit?.let { it - totalSpent }
        val isOverBudget = remaining != null && remaining < 0

        return GeneralQueryResult(
            scope = scope,
            totalSpentPaisa = totalSpent,
            count = expenses.size,
            budgetLimitPaisa = limit,
            remainingPaisa = remaining,
            isOverBudget = isOverBudget,
            expenses = expenses
        )
    }

    suspend fun setBudgetLimit(limitPaisa: Long?) {
        val budget = database.budgetDao().getActiveBudgetOnce()
            ?: Budget(name = "Motorcycle Trip", currency = "PKR").also {
                database.budgetDao().insertBudget(it)
            }
        database.budgetDao().updateLimit(budget.id, limitPaisa)
        database.auditDao().insert(
            AuditEntry(
                actionType = "SET_BUDGET",
                entityId = budget.id,
                afterJson = "limitPaisa=$limitPaisa",
                origin = "USER"
            )
        )
    }

    suspend fun correctExpense(
        expenseId: String,
        newDescription: String? = null,
        newAmountPaisa: Long? = null,
        newPlaceId: String? = null,
        newCategory: String? = null,
        newSubcategory: String? = null
    ): Boolean {
        val current = database.expenseDao().getExpenseById(expenseId) ?: return false
        val resolvedCat = if (newCategory != null) {
            categoryTaxonomyManager.resolve(newDescription ?: current.description, newCategory, newSubcategory ?: current.subcategory)
        } else null

        val updated = current.copy(
            description = newDescription ?: current.description,
            amountPaisa = newAmountPaisa ?: current.amountPaisa,
            effectivePlaceId = newPlaceId ?: current.effectivePlaceId,
            categoryId = resolvedCat?.categoryId ?: current.categoryId,
            category = resolvedCat?.categoryName ?: current.category,
            subcategoryId = resolvedCat?.subcategoryId ?: current.subcategoryId,
            subcategory = resolvedCat?.subcategoryName ?: current.subcategory
        )
        database.expenseDao().updateExpense(updated)
        database.auditDao().insert(
            AuditEntry(
                actionType = "CORRECT_EXPENSE",
                entityId = expenseId,
                beforeJson = "desc=${current.description}, amount=${current.amountPaisa}, cat=${current.category}",
                afterJson = "desc=${updated.description}, amount=${updated.amountPaisa}, cat=${updated.category}",
                origin = "USER"
            )
        )
        return true
    }
}
