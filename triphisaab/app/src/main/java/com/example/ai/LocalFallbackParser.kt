package com.example.ai

import java.util.Locale
import java.util.regex.Pattern

object LocalFallbackParser {

    private val CONFIRM_PATTERN = Pattern.compile("^(?:confirm|han|yes)(?:\\s+([A-Za-z0-9]{3,8}))?$", Pattern.CASE_INSENSITIVE)
    private val CANCEL_PATTERN = Pattern.compile("^(?:cancel|nahin|no|discard)$", Pattern.CASE_INSENSITIVE)

    // Non-expense signals per PRD:
    // - Future intent / reminders ("kal petrol ke liye 2000 rakhna", "dena hai")
    // - Price quotes / inquiries ("helmet 3500 ka mil raha hai", "rate kya hai")
    // - Shared shopping links containing a price
    // - Meeting or calendar chatter
    private val NON_EXPENSE_PHRASES = listOf(
        "mil raha", "milta hai", "rate kya", "price kya", "available hai", "quote",
        "rakhna", "rakh lo", "rakh lena", "kal ke liye", "ke liye rakh", "dena hai",
        "denay hain", "bhejna", "salary", "tankhwah", "meeting", "call me", "http://", "https://", "www."
    )

    fun parse(rawText: String): ClassifierResponse {
        val text = rawText.trim()
        val lower = text.lowercase(Locale.ROOT)

        // 1. Check non-expense signals: price quotes, reminders, URLs
        for (phrase in NON_EXPENSE_PHRASES) {
            if (lower.contains(phrase)) {
                return ClassifierResponse(
                    action = "IGNORE",
                    certainty = "clear",
                    evidence = text
                )
            }
        }

        // 2. Just numbers alone do NOT authorize recording -> NEEDS_CONFIRMATION
        if (text.matches(Regex("^[\\d,\\.]+(?:\\s*k|\\s*hazar)?$", RegexOption.IGNORE_CASE))) {
            return ClassifierResponse(
                action = "NEEDS_CONFIRMATION",
                certainty = "ambiguous",
                clarification = "Amount received without description. Please confirm description and amount.",
                evidence = text
            )
        }

        // 3. Confirm commands ("confirm", "confirm E123", "han")
        val confirmMatcher = CONFIRM_PATTERN.matcher(text)
        if (confirmMatcher.matches()) {
            val code = confirmMatcher.group(1)
            return ClassifierResponse(
                action = "NEEDS_CONFIRMATION",
                certainty = "clear",
                clarification = code ?: "CONFIRM_PLAIN",
                evidence = text
            )
        }

        // 4. Cancel commands
        if (CANCEL_PATTERN.matcher(text).matches()) {
            return ClassifierResponse(
                action = "IGNORE",
                certainty = "clear",
                clarification = "CANCEL_PROPOSAL",
                evidence = text
            )
        }

        // 5. Undo commands e.g. "last expense undo kar do", "undo", "wapas", "delete last"
        if (lower.contains("undo") || lower == "wapas" || lower.contains("delete last") || lower.contains("reverse last")) {
            return ClassifierResponse(
                action = "UNDO_EXPENSE",
                certainty = "clear",
                evidence = text
            )
        }

        // 6. Set budget commands:
        // "set the total budget to 50,000", "set budget 50000", "mera total budget 50 hazar kar do", "budget 80k"
        val setBudget1 = Pattern.compile("(?:set\\s+)?(?:the\\s+)?(?:total\\s+)?budget(?:\\s+limit)?(?:\\s+to|\\s+is)?\\s+([\\d,\\.]+(?:\\s*k|\\s*hazar|\\s*hazaar)?)", Pattern.CASE_INSENSITIVE)
        val mSet1 = setBudget1.matcher(text)
        if (mSet1.find()) {
            val rawNum = mSet1.group(1)
            val parsed = parseNumericAmount(rawNum)
            if (parsed != null && parsed > 0) {
                return ClassifierResponse(
                    action = "SET_BUDGET",
                    certainty = "clear",
                    budgetDecimal = parsed.toString(),
                    evidence = text
                )
            }
        }

        val setBudget2 = Pattern.compile("mera(?:\\s+total)?\\s+budget\\s+([\\d,\\.]+(?:\\s*k|\\s*hazar|\\s*hazaar)?)(?:\\s+kar\\s+do)?", Pattern.CASE_INSENSITIVE)
        val mSet2 = setBudget2.matcher(text)
        if (mSet2.find()) {
            val rawNum = mSet2.group(1)
            val parsed = parseNumericAmount(rawNum)
            if (parsed != null && parsed > 0) {
                return ClassifierResponse(
                    action = "SET_BUDGET",
                    certainty = "clear",
                    budgetDecimal = parsed.toString(),
                    evidence = text
                )
            }
        }

        // 7. Queries
        // Strip trailing punctuation like "?", "!", ".", ","
        val cleanQuery = lower.trim().replace(Regex("[?!.,]+$"), "").trim()

        // Overall total queries e.g. "total", "overall", "total kharcha?", "total spent", "kul kharcha"
        val isOverallTotal = cleanQuery in setOf(
            "total", "overall", "total kharcha", "total spent", "total spend", "summary",
            "kul kharcha", "sab kharcha", "total expenses", "all expenses", "total kharch"
        ) || cleanQuery.matches(Regex("^(?:total|overall|sab|kul)\\s+(?:kharcha|kharch|spent|spending|expenses?)$"))

        if (isOverallTotal) {
            return ClassifierResponse(
                action = "QUERY_BUDGET",
                certainty = "clear",
                query = ClassifierQuery(scope = "overall"),
                evidence = text
            )
        }

        // Balance/remaining queries e.g. "total budget", "remaining budget", "kitna budget bacha hai?", "kitna bacha hai", "remaining balance"
        val isRemainingBudget = cleanQuery in setOf(
            "total budget", "budget", "remaining budget", "remaining", "balance",
            "kitna bacha", "kitna budget bacha", "kitna bacha hai", "budget kitna hai",
            "baki kitna hai", "bacha kitna hai", "remaining balance"
        ) || cleanQuery.contains("kitna budget bacha") || cleanQuery.contains("kitna bacha") ||
            cleanQuery.contains("remaining") || cleanQuery.contains("balance") ||
            cleanQuery.matches(Regex("^(?:total\\s+)?budget$")) ||
            cleanQuery.matches(Regex("^(?:remaining\\s+)?(?:budget|balance)$"))

        if (isRemainingBudget) {
            return ClassifierResponse(
                action = "QUERY_BUDGET",
                certainty = "clear",
                query = ClassifierQuery(scope = "remaining"),
                evidence = text
            )
        }

        // Place queries:
        // Priority 1: Check if known trip place is present in query e.g. "Islamabad mein ktne spend hogye abhi tk", "Islamabad kharcha"
        val matchedTripPlace = findTripPlace(cleanQuery)
        val hasPlaceQueryTerms = cleanQuery.contains("kharcha") || cleanQuery.contains("spend") ||
            cleanQuery.contains("spent") || cleanQuery.contains("lagay") || cleanQuery.contains("kitna") ||
            cleanQuery.contains("ktna") || cleanQuery.contains("ktne") || cleanQuery.contains("kitne") ||
            cleanQuery.contains("total") || cleanQuery.contains("abhi tk") || cleanQuery.contains("ab tak") ||
            cleanQuery.contains("hua") || cleanQuery.contains("hogye") || cleanQuery.contains("hogaye")

        if (matchedTripPlace != null && hasPlaceQueryTerms) {
            return ClassifierResponse(
                action = "QUERY_BUDGET",
                certainty = "clear",
                query = ClassifierQuery(
                    scope = "place",
                    placeText = matchedTripPlace
                ),
                evidence = text
            )
        }

        // Place queries e.g. "Gilgit mein total kharcha ktna hua", "Hunza kharcha", "Total spent in Skardu"
        val placeQueryPattern = Pattern.compile("([A-Za-z]+)(?:\\s+mein|\\s+me|\\s+in)?\\s+(?:total\\s+)?(?:kharcha|expense|spent|spend|budget)(?:\\s+ktna|\\s+kitna|\\s+ktne|\\s+kitne|\\s+hua|\\s+how\\s+much)?", Pattern.CASE_INSENSITIVE)
        val mPlace = placeQueryPattern.matcher(cleanQuery)
        if (mPlace.find()) {
            val placeCandidate = mPlace.group(1)?.trim() ?: ""
            if (placeCandidate.isNotEmpty() && !placeCandidate.equals("total", true) && !placeCandidate.equals("aaj", true) && !placeCandidate.equals("mera", true)) {
                return ClassifierResponse(
                    action = "QUERY_BUDGET",
                    certainty = "clear",
                    query = ClassifierQuery(
                        scope = "place",
                        placeText = titleCase(placeCandidate)
                    ),
                    evidence = text
                )
            }
        }

        // Category queries e.g. "aaj khanay pe kitna lagaya?", "petrol pe kitna kharch hua"
        val catQueryPattern = Pattern.compile("(?:aaj\\s+)?([A-Za-z]+)(?:\\s+pe|\\s+par)?\\s+(?:kitna|ktna|kitne|ktne)\\s+(?:lagaya|kharch|spent|spend)", Pattern.CASE_INSENSITIVE)
        val mCat = catQueryPattern.matcher(cleanQuery)
        if (mCat.find()) {
            val catWord = mCat.group(1)?.trim() ?: ""
            val scope = if (cleanQuery.contains("aaj") || cleanQuery.contains("today")) "today" else "category"
            return ClassifierResponse(
                action = "QUERY_BUDGET",
                certainty = "clear",
                query = ClassifierQuery(
                    scope = scope,
                    categoryText = catWord
                ),
                evidence = text
            )
        }

        // Today query e.g. "aaj ka kharcha", "today total"
        if (cleanQuery.startsWith("aaj") || cleanQuery.startsWith("today")) {
            return ClassifierResponse(
                action = "QUERY_BUDGET",
                certainty = "clear",
                query = ClassifierQuery(scope = "today"),
                evidence = text
            )
        }

        // 8. Multi-expense purchase e.g. "petrol 2200 aur chai 180", "petrol pe 2200 aur chai pe 180 lagay"
        val multiPattern = Pattern.compile("([A-Za-z\\s\\-]+?)(?:\\s+pe|\\s+par)?\\s+([\\d,\\.]+)(?:\\s+aur|\\s+and)\\s+([A-Za-z\\s\\-]+?)(?:\\s+pe|\\s+par)?\\s+([\\d,\\.]+)(?:\\s+lagay|\\s+kharch|\\s+diye)?", Pattern.CASE_INSENSITIVE)
        val mMulti = multiPattern.matcher(text)
        if (mMulti.find()) {
            val d1 = cleanDescription(mMulti.group(1))
            val a1 = parseNumericAmount(mMulti.group(2))
            val d2 = cleanDescription(mMulti.group(3))
            val a2 = parseNumericAmount(mMulti.group(4))
            if (d1.isNotEmpty() && a1 != null && a1 > 0 && d2.isNotEmpty() && a2 != null && a2 > 0) {
                val (cat1, sub1) = inferCategoryFromText(d1)
                val (cat2, sub2) = inferCategoryFromText(d2)
                val items = listOf(
                    ClassifierExpenseItem(
                        description = titleCase(d1),
                        amountDecimal = a1.toString(),
                        category = cat1,
                        subcategory = sub1
                    ),
                    ClassifierExpenseItem(
                        description = titleCase(d2),
                        amountDecimal = a2.toString(),
                        category = cat2,
                        subcategory = sub2
                    )
                )
                return ClassifierResponse(
                    action = "ADD_EXPENSE",
                    certainty = "clear",
                    expenses = items,
                    evidence = text
                )
            }
        }

        // 9. Single expense patterns:
        // Pattern A: "Spent 2,200 on petrol" / "Spent 2200 on petrol"
        val spentOnPattern = Pattern.compile("(?:spent|paid|lagay|diye)\\s+(?:rs\\.?\\s*)?([\\d,\\.]+)\\s+(?:on|for|pe|par)\\s+([A-Za-z\\s\\-]+)", Pattern.CASE_INSENSITIVE)
        val mSpent = spentOnPattern.matcher(text)
        if (mSpent.find()) {
            val amt = parseNumericAmount(mSpent.group(1))
            val rawDesc = mSpent.group(2)
            val desc = cleanDescription(rawDesc)
            if (amt != null && amt > 0 && desc.isNotEmpty()) {
                val (cat, sub) = inferCategoryFromText(desc)
                val explicitPlace = findTripPlace(rawDesc ?: "")
                return ClassifierResponse(
                    action = "ADD_EXPENSE",
                    certainty = "clear",
                    expenses = listOf(
                        ClassifierExpenseItem(
                            description = titleCase(desc),
                            amountDecimal = amt.toString(),
                            category = cat,
                            subcategory = sub,
                            explicitPlaceCorrection = explicitPlace
                        )
                    ),
                    evidence = text
                )
            }
        }

        // Pattern B: Description with separators (- or :) and amount e.g. "Petrol - 2200", "Petrol 2200", "Petrol: 2200"
        // Also supports retrospective prefixes like "kal dinner pe 1200 lagay", "19 September ko helmet 3500 ka liya", "aaj subah 9 baje petrol 2200"
        val singlePattern = Pattern.compile(
            "^(.*?)\\b([A-Za-z][A-Za-z\\s]*?)\\s*(?:-|:)?\\s*(?:rs\\.?\\s*)?([\\d,\\.]+)\\s*(?:ka|ki)?(?:\\s*(?:bharwaya|kharida|liya|diye|lagay|pay\\s+kiya))?(?:\\s+lagay)?\\s*$",
            Pattern.CASE_INSENSITIVE
        )
        val mSingle = singlePattern.matcher(text)
        if (mSingle.matches()) {
            val prefix = mSingle.group(1)?.trim() ?: ""
            val rawDesc = mSingle.group(2)?.trim() ?: ""
            val rawAmt = mSingle.group(3)?.trim() ?: ""
            val amt = parseNumericAmount(rawAmt)

            val fullDesc = if (prefix.isNotBlank()) "$prefix $rawDesc" else rawDesc
            val cleanedDesc = cleanDescription(fullDesc)

            if (amt != null && amt > 0 && cleanedDesc.isNotEmpty()) {
                val (cat, sub) = inferCategoryFromText(cleanedDesc)
                val explicitPlace = findTripPlace(fullDesc)
                return ClassifierResponse(
                    action = "ADD_EXPENSE",
                    certainty = "clear",
                    expenses = listOf(
                        ClassifierExpenseItem(
                            description = titleCase(cleanedDesc),
                            amountDecimal = amt.toString(),
                            category = cat,
                            subcategory = sub,
                            datePhrase = if (prefix.isNotBlank()) prefix else null,
                            explicitPlaceCorrection = explicitPlace
                        )
                    ),
                    evidence = text
                )
            }
        }

        // Pattern C: Amount before description in Urdu e.g. "kal 1200 ka lunch kiya", "2200 ka petrol bharwaya", "180 ki chai"
        val amtBeforePattern = Pattern.compile(
            "^(.*?)(?:rs\\.?\\s*)?([\\d,\\.]+)\\s*(?:ka|ki|ke|pe|par)\\s+([A-Za-z\\s\\-]+?)(?:\\s+(?:kiya|khaya|liya|diye|lagay|kharida|bharwaya))?\\s*$",
            Pattern.CASE_INSENSITIVE
        )
        val mAmtBefore = amtBeforePattern.matcher(text)
        if (mAmtBefore.matches()) {
            val prefix = mAmtBefore.group(1)?.trim() ?: ""
            val rawAmt = mAmtBefore.group(2)?.trim() ?: ""
            val rawDesc = mAmtBefore.group(3)?.trim() ?: ""
            val amt = parseNumericAmount(rawAmt)
            val fullDesc = if (prefix.isNotBlank()) "$prefix $rawDesc" else rawDesc
            val cleanedDesc = cleanDescription(fullDesc)

            if (amt != null && amt > 0 && cleanedDesc.isNotEmpty()) {
                val (cat, sub) = inferCategoryFromText(cleanedDesc)
                val explicitPlace = findTripPlace(fullDesc)
                return ClassifierResponse(
                    action = "ADD_EXPENSE",
                    certainty = "clear",
                    expenses = listOf(
                        ClassifierExpenseItem(
                            description = titleCase(cleanedDesc),
                            amountDecimal = amt.toString(),
                            category = cat,
                            subcategory = sub,
                            datePhrase = if (prefix.isNotBlank()) prefix else null,
                            explicitPlaceCorrection = explicitPlace
                        )
                    ),
                    evidence = text
                )
            }
        }

        // Default: Ignore unrecognized messages to avoid accidental ledger corruption
        return ClassifierResponse(
            action = "IGNORE",
            certainty = "clear",
            evidence = text
        )
    }

    private fun parseNumericAmount(str: String?): Long? {
        if (str == null) return null
        val clean = str.trim().lowercase(Locale.ROOT).replace(",", "").replace("rs.", "").replace("rs", "").trim()
        return try {
            when {
                clean.endsWith("k") -> {
                    val num = clean.removeSuffix("k").trim().toDouble()
                    (num * 1000).toLong()
                }
                clean.endsWith("hazar") -> {
                    val num = clean.removeSuffix("hazar").trim().toDouble()
                    (num * 1000).toLong()
                }
                clean.endsWith("hazaar") -> {
                    val num = clean.removeSuffix("hazaar").trim().toDouble()
                    (num * 1000).toLong()
                }
                else -> {
                    val d = clean.toDouble()
                    d.toLong()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanDescription(raw: String?): String {
        if (raw == null) return ""
        return raw
            .replace(Regex("(?i)\\b(bharwaya|kharida|liya|diye|lagay|pay\\s+kiya|pe|par|ka|ki|ko|mein|in)\\b"), " ")
            .replace(Regex("[\\-_:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private val TRIP_PLACES = listOf(
        "Islamabad" to listOf("islamabad", "isb"),
        "Rawalpindi" to listOf("rawalpindi", "pindi"),
        "Abbottabad" to listOf("abbottabad"),
        "Mansehra" to listOf("mansehra"),
        "Besham" to listOf("besham"),
        "Chilas" to listOf("chilas"),
        "Gilgit" to listOf("gilgit"),
        "Hunza" to listOf("hunza", "karimabad", "aliabad"),
        "Passu" to listOf("passu"),
        "Sost" to listOf("sost", "sust"),
        "Khunjerab" to listOf("khunjerab"),
        "Naran" to listOf("naran"),
        "Babusar" to listOf("babusar"),
        "Skardu" to listOf("skardu")
    )

    fun findTripPlace(text: String): String? {
        val t = text.lowercase(Locale.ROOT)
        for ((canonical, aliases) in TRIP_PLACES) {
            for (alias in aliases) {
                val regex = Regex("\\b${Regex.escape(alias)}\\b")
                if (regex.containsMatchIn(t)) {
                    return canonical
                }
            }
        }
        return null
    }

    fun inferCategoryFromText(text: String): Pair<String?, String?> {
        val t = text.lowercase(Locale.ROOT)
        return when {
            // Fuel
            t.contains("petrol") || t.contains("diesel") || t.contains("fuel") || t.contains("cng") || t.contains("tanki") ->
                Pair("Transport", "Fuel")

            // Taxi / Local transport
            t.contains("taxi") || t.contains("cab") || t.contains("rickshaw") || t.contains("rikshaw") || t.contains("auto") ||
            t.contains("careem") || t.contains("uber") || t.contains("indrive") || t.contains("bus") || t.contains("van") || t.contains("hiace") ->
                Pair("Transport", "Taxi / Local Transport")

            // General Transport & Tolls
            t.contains("toll") || t.contains("fare") || t.contains("kiraya") ->
                Pair("Transport", null)

            // Accommodation
            t.contains("hotel") || t.contains("room") || t.contains("stay") || t.contains("guest house") ||
            t.contains("resort") || t.contains("inn") || t.contains("camp") || t.contains("tent") || t.contains("glamping") ->
                Pair("Accommodation", null)

            // Motorcycle compound
            t.contains("bike") || t.contains("motorcycle") || t.contains("motorbike") || t.contains("70cc") || t.contains("125") -> {
                when {
                    t.contains("accessori") || t.contains("accessory") || t.contains("parts") || t.contains("mirror") ||
                    t.contains("visor") || t.contains("guard") || t.contains("mount") || t.contains("bag") || t.contains("seat") ->
                        Pair("Motorcycle", "Accessories")
                    t.contains("maint") || t.contains("service") || t.contains("oil") || t.contains("tune") || t.contains("tuning") || t.contains("wash") ->
                        Pair("Motorcycle", "Maintenance")
                    t.contains("repair") || t.contains("mechanic") || t.contains("punct") || t.contains("puncher") ||
                    t.contains("brake") || t.contains("break") || t.contains("chain") || t.contains("tyre") || t.contains("tire") ->
                        Pair("Motorcycle", "Repairs")
                    else ->
                        Pair("Motorcycle", null)
                }
            }

            // Motorcycle Accessories Standalone
            t.contains("helmet") || t.contains("gloves") || t.contains("jacket") || t.contains("guards") ||
            t.contains("mount") || t.contains("saddle bag") || t.contains("tank bag") || t.contains("accessories") ||
            t.contains("accessory") || t.contains("fog light") ->
                Pair("Motorcycle", "Accessories")

            // Motorcycle Maintenance Standalone
            t.contains("oil change") || t.contains("engine oil") || t.contains("tuning") || t.contains("mobil oil") ||
            t.contains("maintenance") || t.contains("bike wash") || t.contains("air filter") ->
                Pair("Motorcycle", "Maintenance")

            // Motorcycle Repairs Standalone
            t.contains("puncture") || t.contains("puncher") || t.contains("mechanic") || t.contains("brake") ||
            t.contains("break") || t.contains("chain") || t.contains("tyre") || t.contains("tire") || t.contains("tube") ||
            t.contains("spark plug") ->
                Pair("Motorcycle", "Repairs")

            // Meals
            t.contains("nashta") || t.contains("breakfast") || t.contains("lunch") || t.contains("dinner") ||
            t.contains("khana") || t.contains("biryani") || t.contains("karahi") || t.contains("roti") || t.contains("naan") ||
            t.contains("food") || t.contains("burger") || t.contains("pizza") || t.contains("daal") || t.contains("anda") ||
            t.contains("shawarma") || t.contains("paratha") ->
                Pair("Food & Drinks", "Meals")

            // Tea / Coffee
            t.contains("chai") || t.contains("chaye") || t.contains("tea") || t.contains("coffee") ||
            t.contains("green tea") || t.contains("qehwa") || t.contains("kahwa") || t.contains("doodh patti") ->
                Pair("Food & Drinks", "Tea / Coffee")

            // Snacks
            t.contains("snack") || t.contains("chips") || t.contains("biscuits") || t.contains("biscuit") ||
            t.contains("nimco") || t.contains("samosa") || t.contains("pakora") || t.contains("juice") ||
            t.contains("water") || t.contains("pani") || t.contains("coke") || t.contains("sting") || t.contains("pepsi") ->
                Pair("Food & Drinks", "Snacks")

            // Personal Care
            t.contains("spray") || t.contains("spary") || t.contains("perfume") || t.contains("soap") ||
            t.contains("shampoo") || t.contains("facewash") || t.contains("sunscreen") || t.contains("toothpaste") ->
                Pair("Shopping", "Personal Care")

            // Health & Medical
            t.contains("panadol") || t.contains("disprin") || t.contains("medicine") || t.contains("dawa") ||
            t.contains("dawai") || t.contains("bandage") || t.contains("first aid") ->
                Pair("Shopping", "Health & Medical")

            // Supplies
            t.contains("lighter") || t.contains("matchbox") || t.contains("machis") || t.contains("rope") ||
            t.contains("battery") || t.contains("torch") || t.contains("lock") ->
                Pair("Miscellaneous", "Supplies")

            // Communication
            t.contains("load") || t.contains("balance") || t.contains("easyload") || t.contains("sim") ->
                Pair("Miscellaneous", "Communication")

            // General Shopping
            t.contains("shopping") || t.contains("souvenir") || t.contains("gift") || t.contains("shawl") || t.contains("clothes") ->
                Pair("Shopping", null)

            // Fees & Permits
            t.contains("permit") || t.contains("pass") || t.contains("ticket") || t.contains("challan") ->
                Pair("Fees & Permits", null)

            // Charity & Religious Giving (Sadqa, Zakat, Khairat, Donation, Charity)
            t.contains("sadqa") || t.contains("sadqah") || t.contains("zakat") || t.contains("zakaat") ||
            t.contains("khairat") || t.contains("khairaat") || t.contains("donation") || t.contains("charity") ||
            t.contains("bheek") || t.contains("faqqeer") || t.contains("faqeer") || t.contains("masjid") || t.contains("madrasa") -> {
                val sub = when {
                    t.contains("sadqa") || t.contains("sadqah") -> "Sadqa"
                    t.contains("zakat") || t.contains("zakaat") -> "Zakat"
                    else -> "Donations"
                }
                Pair("Charity & Donations", sub)
            }

            // Lending / Udhaar (Borrowing, loaning to friend)
            t.contains("udhaar") || t.contains("udhar") || t.contains("loan") || t.contains("qarz") || t.contains("qarza") ->
                Pair("Lending", "Personal Loan")

            else -> Pair(null, null)
        }
    }

    private fun titleCase(input: String): String {
        return input.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }
}
