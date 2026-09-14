package com.example.engine

import com.example.data.model.LocationSnapshot
import com.example.data.model.Transaction
import com.example.data.repository.LedgerRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeterministicReplyRenderer {

    fun renderTransactionConfirmation(
        result: LedgerRepository.CommitTransactionResult,
        locationSnapshot: LocationSnapshot?
    ): String {
        val txs = result.committedTransactions
        val balance = result.balanceState
        val availStr = balance.availableFundsPaisa?.let { MoneyFormatter.formatPaisa(it) } ?: "Not set"

        if (txs.size == 1) {
            val tx = txs.first()
            val shortId = tx.id.take(4).uppercase(Locale.ROOT)
            val amtStr = MoneyFormatter.formatPaisa(tx.amountPaisa)
            val locText = formatLocationText(tx, locationSnapshot)
            val dateText = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(tx.occurrenceTime))

            return when (tx.type) {
                "INCOME", "GIFT_RECEIVED" -> {
                    val fromStr = if (!tx.counterparty.isNullOrBlank()) "\nFrom: ${tx.counterparty}" else ""
                    """
                    ✅ Funds added: ${tx.description} — $amtStr$fromStr
                    Available funds: $availStr
                    ID: T$shortId
                    """.trimIndent()
                }

                "LOAN_GIVEN" -> {
                    val person = tx.counterparty ?: tx.description
                    """
                    ✅ Lent to $person: $amtStr
                    Category: Lending & Borrowing / Loan Given
                    Available funds: $availStr
                    Outstanding from $person: $amtStr
                    ID: T$shortId
                    """.trimIndent()
                }

                "LOAN_RECEIVED" -> {
                    val person = tx.counterparty ?: tx.description
                    """
                    ✅ Borrowed from $person: $amtStr
                    Category: Lending & Borrowing / Loan Received
                    Available funds: $availStr
                    ID: T$shortId
                    """.trimIndent()
                }

                "LOAN_REPAYMENT_RECEIVED" -> {
                    val person = tx.counterparty ?: tx.description
                    """
                    ✅ Loan repayment received from $person: $amtStr
                    Available funds: $availStr
                    ID: T$shortId
                    """.trimIndent()
                }

                "REFUND_RECEIVED" -> {
                    """
                    ✅ Refund received: ${tx.description} — $amtStr
                    Available funds: $availStr
                    ID: T$shortId
                    """.trimIndent()
                }

                "TRANSFER_OUT", "TRANSFER_IN" -> {
                    """
                    ℹ️ Own-account transfer noted: ${tx.description} ($amtStr)
                    Note: Own-account movement does not affect available trip funds.
                    Available funds: $availStr
                    ID: T$shortId
                    """.trimIndent()
                }

                else -> {
                    // Standard EXPENSE
                    val catText = formatCategoryText(tx)
                    """
                    ✅ Expense recorded: ${tx.description} — $amtStr
                    Category: $catText
                    Location: $locText
                    Date: $dateText
                    Available funds: $availStr
                    ID: T$shortId
                    """.trimIndent()
                }
            }
        } else {
            // Multi-item batch
            val batchTotalStr = MoneyFormatter.formatPaisa(result.batchTotalPaisa)
            val firstTx = txs.first()
            val locText = formatLocationText(firstTx, locationSnapshot)
            val dateText = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(firstTx.occurrenceTime))

            val sb = StringBuilder()
            sb.append("✅ Recorded ${txs.size} transactions ($batchTotalStr):\n")
            txs.forEach { tx ->
                val amtStr = MoneyFormatter.formatPaisa(tx.amountPaisa)
                val shortId = tx.id.take(4).uppercase(Locale.ROOT)
                val catText = formatCategoryText(tx)
                sb.append("• ${tx.description}: $amtStr ($catText) — T$shortId\n")
            }
            sb.append("Date: $dateText\n")
            sb.append("Location: $locText\n")
            sb.append("Available funds: $availStr")
            return sb.toString().trim()
        }
    }

    fun renderBudgetReplacement(
        oldBasePaisa: Long?,
        newBasePaisa: Long?,
        balance: LedgerRepository.BalanceState
    ): String {
        val oldStr = oldBasePaisa?.let { MoneyFormatter.formatPaisa(it) } ?: "Not set"
        val newStr = newBasePaisa?.let { MoneyFormatter.formatPaisa(it) } ?: "Not set"
        val addedStr = MoneyFormatter.formatPaisa(balance.addedFundsPaisa)
        val cashOutStr = MoneyFormatter.formatPaisa(balance.cashOutPaisa)
        val availStr = balance.availableFundsPaisa?.let { MoneyFormatter.formatPaisa(it) } ?: "Not set"

        return """
        ✅ Base budget updated: $oldStr → $newStr
        Added funds: $addedStr
        Cash out: $cashOutStr
        Available funds: $availStr
        """.trimIndent()
    }

    fun renderPlaceQuery(result: LedgerRepository.PlaceQueryResult): String {
        if (!result.hasRecords) {
            return "${result.placeName} mein abhi koi recorded expense nahi hai."
        }

        val totalSpentStr = MoneyFormatter.formatPaisa(result.totalSpentPaisa)
        val sb = StringBuilder()
        sb.append("📍 ${result.placeName} Spending:\n")
        if (!result.dateRange.isNullOrBlank()) {
            sb.append("Date: ${result.dateRange}\n")
        }
        sb.append("Total spent: $totalSpentStr (${result.count} ${if (result.count == 1) "transaction" else "transactions"})\n")

        if (result.incomingPaisa > 0) {
            sb.append("Incoming funds: ${MoneyFormatter.formatPaisa(result.incomingPaisa)}\n")
        }

        if (result.categoryBreakdown.isNotEmpty()) {
            sb.append("\nCategory Breakdown:\n")
            result.categoryBreakdown.entries.sortedByDescending { it.value }.take(5).forEach { (cat, amount) ->
                sb.append("• $cat: ${MoneyFormatter.formatPaisa(amount)}\n")
            }
        }

        if (result.transactions.isNotEmpty()) {
            sb.append("\nItemized Entries:\n")
            result.transactions.take(10).forEach { tx ->
                val dateStr = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(tx.occurrenceTime))
                val amtStr = MoneyFormatter.formatPaisa(tx.amountPaisa)
                val sign = if (tx.direction == "INCOMING") "+" else "-"
                sb.append("• $dateStr: ${tx.description} — $sign$amtStr\n")
            }
            if (result.totalPages > 1) {
                sb.append("Page ${result.page} of ${result.totalPages} (Type '@chat next' for more)")
            }
        }

        return sb.toString().trim()
    }

    fun renderUndo(undoResult: LedgerRepository.UndoTransactionResult): String {
        val count = undoResult.reversedTransactions.size
        val reversedTotalStr = MoneyFormatter.formatPaisa(undoResult.reversedTotalPaisa)
        val availStr = undoResult.balanceState.availableFundsPaisa?.let { MoneyFormatter.formatPaisa(it) } ?: "Not set"

        val sb = StringBuilder()
        sb.append("↩️ Reversed $count transaction(s) ($reversedTotalStr)\n")
        undoResult.reversedTransactions.forEach { tx ->
            val amtStr = MoneyFormatter.formatPaisa(tx.amountPaisa)
            sb.append("• Replaced: ${tx.description} ($amtStr)\n")
        }
        sb.append("Available funds: $availStr")
        return sb.toString().trim()
    }

    fun renderBalanceSummary(balance: LedgerRepository.BalanceState): String {
        val baseStr = balance.baseBudgetPaisa?.let { MoneyFormatter.formatPaisa(it) } ?: "Not set"
        val addedStr = MoneyFormatter.formatPaisa(balance.addedFundsPaisa)
        val cashOutStr = MoneyFormatter.formatPaisa(balance.cashOutPaisa)
        val expenseStr = MoneyFormatter.formatPaisa(balance.expenseSpendingPaisa)
        val availStr = balance.availableFundsPaisa?.let {
            if (it < 0) "Over budget by ${MoneyFormatter.formatPaisa(Math.abs(it))}"
            else MoneyFormatter.formatPaisa(it)
        } ?: "Not set"

        return """
        📊 Trip Budget Status:
        • Base Budget: $baseStr
        • Added Funds (Income/Repayments): $addedStr
        • Cash Out: $cashOutStr (Expenses: $expenseStr)
        • Available Funds: $availStr
        """.trimIndent()
    }

    private fun formatCategoryText(tx: Transaction): String {
        val cat = tx.category?.takeIf { it.isNotBlank() } ?: "Miscellaneous"
        val sub = tx.subcategory?.takeIf { it.isNotBlank() }
        return if (sub != null && !sub.equals(cat, ignoreCase = true)) "$cat / $sub" else cat
    }

    private fun formatLocationText(tx: Transaction, locationSnapshot: LocationSnapshot?): String {
        if (!tx.locationOverride.isNullOrBlank()) {
            return "${tx.locationOverride} · User override"
        }
        val locality = locationSnapshot?.locality
        return when (locationSnapshot?.qualityStatus) {
            "FRESH" -> "${locality ?: "Islamabad"} · Device location"
            "APPROXIMATE" -> "${locality ?: "Device position"} (approximate)"
            "STALE" -> "${locality ?: "Device position"} · Location stale"
            "UNAVAILABLE" -> "Location unavailable"
            "DENIED" -> "Location permission denied"
            else -> locality?.let { "$it · Device location" } ?: "Location pending"
        }
    }
}
