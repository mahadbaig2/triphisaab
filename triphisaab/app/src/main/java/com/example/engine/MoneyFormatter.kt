package com.example.engine

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object MoneyFormatter {
    const val MAX_EXPENSE_PAISA = 10_000_000_00L // Rs. 10,000,000 in paisa

    /**
     * Parse decimal text like "2200", "2200.50", "2,200" into exact Long paisa.
     * Throws IllegalArgumentException on invalid, negative, zero or out-of-range amounts.
     */
    fun parseToPaisa(amountStr: String): Long {
        val clean = amountStr.trim()
            .replace(",", "")
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .replace("PKR", "", ignoreCase = true)
            .trim()
        val bd = try {
            BigDecimal(clean)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid amount format: '$amountStr'")
        }
        if (bd <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Expense amount must be positive: '$amountStr'")
        }
        // Scale to 2 decimal places (paisa)
        val scaled = bd.setScale(2, RoundingMode.HALF_UP)
        val paisaBd = scaled.multiply(BigDecimal(100))
        val paisa = try {
            paisaBd.longValueExact()
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("Amount exceeds integer limits: '$amountStr'")
        }
        if (paisa > MAX_EXPENSE_PAISA) {
            throw IllegalArgumentException("Amount exceeds cap of Rs. 10,000,000: '$amountStr'")
        }
        return paisa
    }

    /**
     * Formats Long paisa into standard display format e.g. "Rs. 2,200" or "Rs. 2,200.50"
     */
    fun formatPaisa(paisa: Long): String {
        val isNegative = paisa < 0
        val absPaisa = Math.abs(paisa)
        val rupees = absPaisa / 100
        val remainderPaisa = absPaisa % 100

        val nf = NumberFormat.getNumberInstance(Locale.US)
        val formattedRupees = nf.format(rupees)

        val amountFormatted = if (remainderPaisa == 0L) {
            formattedRupees
        } else {
            String.format(Locale.US, "%s.%02d", formattedRupees, remainderPaisa)
        }

        return if (isNegative) "-Rs. $amountFormatted" else "Rs. $amountFormatted"
    }

    /**
     * Format paisa to decimal string for JSON/inputs e.g. "2200.00"
     */
    fun toDecimalString(paisa: Long): String {
        val rupees = paisa / 100
        val rem = Math.abs(paisa % 100)
        return String.format(Locale.US, "%d.%02d", rupees, rem)
    }
}
