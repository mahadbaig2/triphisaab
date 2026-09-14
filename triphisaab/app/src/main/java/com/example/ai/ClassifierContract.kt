package com.example.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClassifierExpenseItem(
    val description: String,
    @Json(name = "amount_decimal") val amountDecimal: String,
    val currency: String = "PKR",
    val type: String? = "EXPENSE",
    val direction: String? = "OUTGOING",
    val counterparty: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    @Json(name = "date_phrase") val datePhrase: String? = null,
    @Json(name = "expense_date") val expenseDate: String? = null,
    @Json(name = "explicit_place_correction") val explicitPlaceCorrection: String? = null
)

@JsonClass(generateAdapter = true)
data class ClassifierQuery(
    val scope: String = "overall", // "overall", "today", "recent", "place", "remaining", "category", "counterparty"
    @Json(name = "place_text") val placeText: String? = null,
    @Json(name = "category_text") val categoryText: String? = null,
    @Json(name = "counterparty_text") val counterpartyText: String? = null,
    @Json(name = "date_from") val dateFrom: String? = null,
    @Json(name = "date_to") val dateTo: String? = null,
    val page: Int = 1
)

@JsonClass(generateAdapter = true)
data class ClassifierCorrection(
    val description: String? = null,
    @Json(name = "amount_decimal") val amountDecimal: String? = null,
    @Json(name = "place_text") val placeText: String? = null
)

@JsonClass(generateAdapter = true)
data class ClassifierResponse(
    @Json(name = "schema_version") val schemaVersion: Int = 1,
    val action: String, // IGNORE, ADD_EXPENSE, RECORD_TRANSACTION, QUERY_BUDGET, SET_BUDGET, UNDO_EXPENSE, CORRECT_EXPENSE, NEEDS_CONFIRMATION
    val certainty: String = "clear", // "clear", "ambiguous"
    val expenses: List<ClassifierExpenseItem>? = null,
    val query: ClassifierQuery? = null,
    @Json(name = "budget_decimal") val budgetDecimal: String? = null,
    @Json(name = "target_expense_id") val targetExpenseId: String? = null,
    val correction: ClassifierCorrection? = null,
    val clarification: String? = null,
    val evidence: String? = null
)

sealed class ProposedCommand {
    data class AddExpenses(val items: List<ClassifierExpenseItem>) : ProposedCommand()
    data class QueryBudget(val query: ClassifierQuery) : ProposedCommand()
    data class SetBudget(val budgetDecimal: String) : ProposedCommand()
    object UndoExpense : ProposedCommand()
    data class CorrectExpense(val targetId: String?, val correction: ClassifierCorrection) : ProposedCommand()
    data class NeedsConfirmation(val explanation: String, val proposedAction: ProposedCommand?) : ProposedCommand()
    object Ignore : ProposedCommand()
}
