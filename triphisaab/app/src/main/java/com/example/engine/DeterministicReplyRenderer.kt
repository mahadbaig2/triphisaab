package com.example.engine

import com.example.data.model.Expense
import com.example.data.model.LocationSnapshot
import com.example.data.repository.LedgerRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeterministicReplyRenderer {

    fun renderExpenseConfirmation(
        commitResult: LedgerRepository.CommitResult,
        locationSnapshot: LocationSnapshot?
    ): String {
        val expenses = commitResult.committedExpenses

        val totalSpentStr = MoneyFormatter.formatPaisa(commitResult.overallTotalSpentPaisa)
        val remainingStr = when {
            commitResult.budgetLimitPaisa == null -> "Budget not set"
            commitResult.isOverBudget -> "Over budget: ${MoneyFormatter.formatPaisa(Math.abs(commitResult.remainingPaisa ?: 0L))}"
            else -> MoneyFormatter.formatPaisa(commitResult.remainingPaisa ?: 0L)
        }

        val sb = StringBuilder()
        if (expenses.size == 1) {
            val exp = expenses.first()
            val shortId = exp.id.take(4).uppercase(Locale.ROOT)
            val amtStr = MoneyFormatter.formatPaisa(exp.amountPaisa)
            val locText = formatLocationText(exp, locationSnapshot)
            val catText = formatCategoryText(exp)
            val dateText = TripDateParser.formatDisplayDate(exp.expenseTime, exp.timeCertainty)

            sb.append("✅ Recorded: ${exp.description} — $amtStr\n")
            sb.append("Category: $catText\n")
            sb.append("Date: $dateText\n")
            sb.append("Location: $locText\n")
            sb.append("Total spent: $totalSpentStr\n")
            sb.append("Remaining: $remainingStr\n")
            sb.append("ID: E$shortId")
        } else {
            val batchTotalStr = MoneyFormatter.formatPaisa(commitResult.batchTotalPaisa)
            val firstExp = expenses.first()
            val locText = formatLocationText(firstExp, locationSnapshot)
            val dateText = TripDateParser.formatDisplayDate(firstExp.expenseTime, firstExp.timeCertainty)

            sb.append("✅ Recorded ${expenses.size} expenses ($batchTotalStr):\n")
            expenses.forEach { exp ->
                val amtStr = MoneyFormatter.formatPaisa(exp.amountPaisa)
                val shortId = exp.id.take(4).uppercase(Locale.ROOT)
                val catText = formatCategoryText(exp)
                sb.append("• ${exp.description}: $amtStr ($catText) — E$shortId\n")
            }
            sb.append("Date: $dateText\n")
            sb.append("Location: $locText\n")
            sb.append("Total spent: $totalSpentStr\n")
            sb.append("Remaining: $remainingStr")
        }

        return sb.toString().trim()
    }

    fun renderPlaceQuery(result: LedgerRepository.PlaceQueryResult): String {
        val totalStr = MoneyFormatter.formatPaisa(result.totalSpentPaisa)
        val sb = StringBuilder()
        sb.append("${result.placeName} Spending:\n")
        sb.append("Total: $totalStr (${result.count} ${if (result.count == 1) "expense" else "expenses"})\n")

        if (result.expenses.isEmpty()) {
            sb.append("No recorded expenses found for this location.")
        } else {
            result.expenses.forEach { exp ->
                val dateStr = TripDateParser.formatDisplayDate(exp.expenseTime, exp.timeCertainty)
                val amtStr = MoneyFormatter.formatPaisa(exp.amountPaisa)
                val catStr = exp.category?.let { " ($it)" } ?: ""
                sb.append("• $dateStr: ${exp.description}$catStr — $amtStr\n")
            }
            if (result.totalPages > 1) {
                sb.append("Page ${result.page} of ${result.totalPages}")
            }
        }
        return sb.toString().trim()
    }

    fun renderGeneralQuery(result: LedgerRepository.GeneralQueryResult): String {
        val totalStr = MoneyFormatter.formatPaisa(result.totalSpentPaisa)
        val remainingStr = when {
            result.budgetLimitPaisa == null -> "Budget not set"
            result.isOverBudget -> "Over budget: ${MoneyFormatter.formatPaisa(Math.abs(result.remainingPaisa ?: 0L))}"
            else -> MoneyFormatter.formatPaisa(result.remainingPaisa ?: 0L)
        }

        val sb = StringBuilder()
        val title = when (result.scope.lowercase()) {
            "today" -> "Today's Spending:"
            "remaining", "balance" -> "Trip Budget Balance:"
            else -> "Total Trip Spending:"
        }
        sb.append("$title\n")
        sb.append("Total spent: $totalStr (${result.count} expenses)\n")
        sb.append("Remaining: $remainingStr\n")

        if (result.expenses.isNotEmpty()) {
            sb.append("\nRecent:\n")
            result.expenses.take(5).forEach { exp ->
                val dateStr = TripDateParser.formatDisplayDate(exp.expenseTime, exp.timeCertainty)
                val amtStr = MoneyFormatter.formatPaisa(exp.amountPaisa)
                sb.append("• $dateStr: ${exp.description} — $amtStr\n")
            }
        }
        return sb.toString().trim()
    }

    fun renderUndo(undoResult: LedgerRepository.UndoResult): String {
        val reversed = undoResult.reversedExpenses
        val newTotalStr = MoneyFormatter.formatPaisa(undoResult.newOverallTotalSpentPaisa)
        val remainingStr = when {
            undoResult.budgetLimitPaisa == null -> "Budget not set"
            undoResult.remainingPaisa != null && undoResult.remainingPaisa < 0 ->
                "Over budget: ${MoneyFormatter.formatPaisa(Math.abs(undoResult.remainingPaisa))}"
            else -> MoneyFormatter.formatPaisa(undoResult.remainingPaisa ?: 0L)
        }

        val sb = StringBuilder()
        if (reversed.size == 1) {
            val exp = reversed.first()
            val amtStr = MoneyFormatter.formatPaisa(exp.amountPaisa)
            sb.append("Reversed: ${exp.description} — $amtStr\n")
        } else {
            val revTotalStr = MoneyFormatter.formatPaisa(undoResult.reversedTotalPaisa)
            sb.append("Reversed ${reversed.size} expenses (Total $revTotalStr)\n")
        }
        sb.append("New total spent: $newTotalStr\n")
        sb.append("Remaining: $remainingStr")
        return sb.toString().trim()
    }

    fun renderConfirmationProposal(code: String, summary: String): String {
        return "Proposal: $summary\nReply 'confirm $code' to record, or 'cancel' to discard. (Expires in 10m)"
    }

    private fun formatCategoryText(exp: Expense): String {
        val cat = exp.category ?: "Miscellaneous"
        val sub = exp.subcategory
        return if (!sub.isNullOrBlank()) "$cat / $sub" else cat
    }

    private fun formatLocationText(exp: Expense, snapshot: LocationSnapshot?): String {
        if (exp.locationCertainty == "UNCERTAIN") {
            return "Uncertain (retrospective entry)"
        }
        if (snapshot == null) return "Unavailable"
        return when (snapshot.qualityStatus) {
            "DENIED" -> "Location permission denied"
            "UNAVAILABLE" -> "Unavailable"
            "STALE" -> "Stale; city unassigned"
            "APPROXIMATE", "FRESH" -> {
                when {
                    !snapshot.locality.isNullOrBlank() -> "${snapshot.locality} · Device location"
                    !snapshot.district.isNullOrBlank() -> "${snapshot.district} · Device location"
                    snapshot.geocodeState == "PENDING" -> "Captured; place name pending"
                    else -> "Coordinates captured; offline"
                }
            }
            else -> "Unavailable"
        }
    }
}
