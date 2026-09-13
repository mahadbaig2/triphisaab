package com.example.engine

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

object TripDateParser {

    val KARACHI_TZ: TimeZone = TimeZone.getTimeZone("Asia/Karachi")

    data class ParsedDateResult(
        val epochMillis: Long,
        val timeCertainty: String, // "EXACT", "DATE_ONLY", "UNCERTAIN"
        val timeSource: String, // "MESSAGE_TIMESTAMP", "DETECTION_TIME", "PARSED_RETROSPECTIVE", "MANUAL_INPUT"
        val isRetrospective: Boolean
    )

    private val MONTH_MAP = mapOf(
        "jan" to Calendar.JANUARY, "january" to Calendar.JANUARY,
        "feb" to Calendar.FEBRUARY, "february" to Calendar.FEBRUARY,
        "mar" to Calendar.MARCH, "march" to Calendar.MARCH,
        "apr" to Calendar.APRIL, "april" to Calendar.APRIL,
        "may" to Calendar.MAY,
        "jun" to Calendar.JUNE, "june" to Calendar.JUNE,
        "jul" to Calendar.JULY, "july" to Calendar.JULY,
        "aug" to Calendar.AUGUST, "august" to Calendar.AUGUST,
        "sep" to Calendar.SEPTEMBER, "september" to Calendar.SEPTEMBER,
        "oct" to Calendar.OCTOBER, "october" to Calendar.OCTOBER,
        "nov" to Calendar.NOVEMBER, "november" to Calendar.NOVEMBER,
        "dec" to Calendar.DECEMBER, "december" to Calendar.DECEMBER
    )

    /**
     * Parses the natural text for explicit relative or absolute date/time in Pakistan timezone.
     */
    fun parseExpenseDateTime(
        text: String,
        messageTime: Long?,
        detectionTime: Long = System.currentTimeMillis()
    ): ParsedDateResult {
        val baseMillis = messageTime ?: detectionTime
        val cal = Calendar.getInstance(KARACHI_TZ).apply {
            timeInMillis = baseMillis
        }

        val lower = text.lowercase(Locale.ROOT)

        // 1. Check "kal" / "yesterday"
        if (lower.contains("kal ") || lower.startsWith("kal ") || lower.contains(" yesterday")) {
            // Did it say "kal subah" or "kal sham"?
            val hour = extractTimeHour(lower)
            cal.add(Calendar.DAY_OF_YEAR, -1)
            return if (hour != null) {
                cal.set(Calendar.HOUR_OF_DAY, hour.first)
                cal.set(Calendar.MINUTE, hour.second)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                ParsedDateResult(cal.timeInMillis, "EXACT", "PARSED_RETROSPECTIVE", true)
            } else {
                // Date only
                cal.set(Calendar.HOUR_OF_DAY, 12)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                ParsedDateResult(cal.timeInMillis, "DATE_ONLY", "PARSED_RETROSPECTIVE", true)
            }
        }

        // 2. Check "parson" / "day before yesterday"
        if (lower.contains("parson") || lower.contains("day before yesterday")) {
            val hour = extractTimeHour(lower)
            cal.add(Calendar.DAY_OF_YEAR, -2)
            return if (hour != null) {
                cal.set(Calendar.HOUR_OF_DAY, hour.first)
                cal.set(Calendar.MINUTE, hour.second)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                ParsedDateResult(cal.timeInMillis, "EXACT", "PARSED_RETROSPECTIVE", true)
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 12)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                ParsedDateResult(cal.timeInMillis, "DATE_ONLY", "PARSED_RETROSPECTIVE", true)
            }
        }

        // 3. Check specific calendar day (e.g. "19 September", "19 Sep", "19/09")
        val datePattern = Pattern.compile("(\\b\\d{1,2})\\s+([a-zA-Z]+)", Pattern.CASE_INSENSITIVE)
        val m = datePattern.matcher(lower)
        if (m.find()) {
            val day = m.group(1)?.toIntOrNull()
            val monthStr = m.group(2)?.lowercase(Locale.ROOT)
            val month = monthStr?.let { MONTH_MAP[it] }
            if (day != null && day in 1..31 && month != null) {
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day)
                val hour = extractTimeHour(lower)
                return if (hour != null) {
                    cal.set(Calendar.HOUR_OF_DAY, hour.first)
                    cal.set(Calendar.MINUTE, hour.second)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    ParsedDateResult(cal.timeInMillis, "EXACT", "PARSED_RETROSPECTIVE", true)
                } else {
                    cal.set(Calendar.HOUR_OF_DAY, 12)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    ParsedDateResult(cal.timeInMillis, "DATE_ONLY", "PARSED_RETROSPECTIVE", true)
                }
            }
        }

        // 4. Check "aaj subah 9 baje" / "subah 9 baje" / "today at 9"
        val hour = extractTimeHour(lower)
        if (hour != null) {
            cal.set(Calendar.HOUR_OF_DAY, hour.first)
            cal.set(Calendar.MINUTE, hour.second)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val isRetro = cal.timeInMillis < baseMillis - 10 * 60 * 1000L // earlier today by > 10m
            return ParsedDateResult(cal.timeInMillis, "EXACT", if (isRetro) "PARSED_RETROSPECTIVE" else "MESSAGE_TIMESTAMP", isRetro)
        }

        // 5. Default immediate expense
        return ParsedDateResult(
            epochMillis = baseMillis,
            timeCertainty = "EXACT",
            timeSource = if (messageTime != null) "MESSAGE_TIMESTAMP" else "DETECTION_TIME",
            isRetrospective = false
        )
    }

    private fun extractTimeHour(text: String): Pair<Int, Int>? {
        // e.g. "9 baje", "subah 9", "sham 5", "9:15 am", "9 am", "2 pm"
        val p1 = Pattern.compile("(\\b\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)", Pattern.CASE_INSENSITIVE)
        val m1 = p1.matcher(text)
        if (m1.find()) {
            var h = m1.group(1)?.toIntOrNull() ?: return null
            val min = m1.group(2)?.toIntOrNull() ?: 0
            val ampm = m1.group(3)?.lowercase(Locale.ROOT)
            if (ampm == "pm" && h < 12) h += 12
            if (ampm == "am" && h == 12) h = 0
            return Pair(h, min)
        }

        val p2 = Pattern.compile("(subah|dopahar|sham|raat)\\s+(\\d{1,2})\\s*(?:baje)?", Pattern.CASE_INSENSITIVE)
        val m2 = p2.matcher(text)
        if (m2.find()) {
            val period = m2.group(1)?.lowercase(Locale.ROOT)
            var h = m2.group(2)?.toIntOrNull() ?: return null
            if ((period == "sham" || period == "raat" || period == "dopahar") && h < 12 && h > 0) {
                if (h != 12) h += 12
            }
            return Pair(h, 0)
        }

        val p3 = Pattern.compile("(\\b\\d{1,2})\\s*baje", Pattern.CASE_INSENSITIVE)
        val m3 = p3.matcher(text)
        if (m3.find()) {
            var h = m3.group(1)?.toIntOrNull() ?: return null
            if (text.contains("raat") || text.contains("sham")) {
                if (h < 12) h += 12
            }
            return Pair(h, 0)
        }

        return null
    }

    /**
     * Formats timestamp in Asia/Karachi timezone according to certainty.
     */
    fun formatDisplayDate(epochMillis: Long, timeCertainty: String): String {
        val sdf = SimpleDateFormat(
            when (timeCertainty) {
                "DATE_ONLY" -> "d MMM yyyy"
                "UNCERTAIN" -> "d MMM yyyy (time uncertain)"
                else -> "d MMM yyyy, h:mm a"
            },
            Locale.US
        ).apply {
            timeZone = KARACHI_TZ
        }
        return sdf.format(Date(epochMillis))
    }
}
