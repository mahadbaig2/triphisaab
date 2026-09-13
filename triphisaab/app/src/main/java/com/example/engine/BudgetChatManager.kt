package com.example.engine

import android.util.Log
import com.example.data.db.TripBudgetDatabase
import com.example.data.model.ChatTurn
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class BudgetChatManager(
    private val database: TripBudgetDatabase,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "BudgetChatManager"
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val CHAT_EXPIRY_MS = 2 * 60 * 60 * 1000L // 2 hours
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun handleChat(rawMessage: String): String {
        val query = rawMessage.trim().substring(5).trim() // strip "@chat"

        if (query.isBlank()) {
            return """Trip Budget Assistant:
You can ask questions about your trip budget and spending. Examples:
• @chat Can I afford another 3,000 for bike accessories?
• @chat Gilgit mein mera sab se bara expense kya tha?
• @chat Given my spending so far, how much do I have left for the trip?
• @chat Which category am I spending the most on?

(Financial summaries are processed with cloud AI; no GPS coordinates or unrelated messages leave your phone.)""".trimIndent()
        }

        // 1. Gather all financial aggregates LOCALLY from the database
        val budget = database.budgetDao().getActiveBudgetOnce()
        val totalSpentPaisa = database.expenseDao().getTotalSpentActiveOnce() ?: 0L
        val limitPaisa = budget?.limitPaisa
        val remainingPaisa = limitPaisa?.let { it - totalSpentPaisa }
        val categorySummaries = database.expenseDao().getCategorySummaries()
        val largestExpenses = database.expenseDao().getLargestExpenses(5)
        val recentExpenses = database.expenseDao().getRecentExpensesList(8)
        val placeSummaries = database.expenseDao().getPlaceSummaries()
        val allPlaces = database.placeDao().getAllPlacesList()
        val placesMap = allPlaces.associateBy { it.id }

        val totalSpentStr = MoneyFormatter.formatPaisa(totalSpentPaisa)
        val limitStr = limitPaisa?.let { MoneyFormatter.formatPaisa(it) } ?: "Not set"
        val remainingStr = when {
            limitPaisa == null -> "Budget not set"
            remainingPaisa != null && remainingPaisa < 0 -> "Over budget by ${MoneyFormatter.formatPaisa(Math.abs(remainingPaisa))}"
            else -> MoneyFormatter.formatPaisa(remainingPaisa ?: 0L)
        }

        val placeSummarySb = StringBuilder()
        placeSummaries.forEach { ps ->
            val placeName = if (ps.effectivePlaceId.isNullOrBlank()) {
                "Unassigned / Retrospective"
            } else {
                placesMap[ps.effectivePlaceId]?.canonicalName ?: "Unknown Location"
            }
            placeSummarySb.append("- $placeName: ${MoneyFormatter.formatPaisa(ps.totalPaisa)} (${ps.count} expenses)\n")
        }

        val catSummarySb = StringBuilder()
        categorySummaries.forEach { cs ->
            catSummarySb.append("- ${cs.category ?: "Uncategorized"}: ${MoneyFormatter.formatPaisa(cs.totalPaisa)} (${cs.count} items)\n")
        }

        val largestSb = StringBuilder()
        largestExpenses.forEach { exp ->
            val loc = exp.effectivePlaceId?.let { placesMap[it]?.canonicalName } ?: exp.locationOverride ?: "Unassigned"
            val subcat = if (!exp.subcategory.isNullOrBlank()) " / ${exp.subcategory}" else ""
            largestSb.append("- ${exp.description}: ${MoneyFormatter.formatPaisa(exp.amountPaisa)} [Location: $loc, Category: ${exp.category ?: "Misc"}$subcat]\n")
        }

        val recentSb = StringBuilder()
        recentExpenses.forEach { exp ->
            val dateStr = TripDateParser.formatDisplayDate(exp.expenseTime, exp.timeCertainty)
            val loc = exp.effectivePlaceId?.let { placesMap[it]?.canonicalName } ?: exp.locationOverride ?: "Unassigned"
            val subcat = if (!exp.subcategory.isNullOrBlank()) " / ${exp.subcategory}" else ""
            recentSb.append("- $dateStr: ${exp.description} — ${MoneyFormatter.formatPaisa(exp.amountPaisa)} [Location: $loc, Category: ${exp.category ?: "Misc"}$subcat]\n")
        }

        val localFacts = """
VERIFIED LOCAL LEDGER FACTS:
- Active Budget Limit: $limitStr
- Total Spent: $totalSpentStr
- Remaining Balance: $remainingStr
- Location Spending Breakdown:
${placeSummarySb.toString().trim().ifBlank { "- No location records yet" }}
- Category Breakdown:
${catSummarySb.toString().trim().ifBlank { "- No category records yet" }}
- Largest Expenses:
${largestSb.toString().trim().ifBlank { "- None" }}
- Recent Expenses:
${recentSb.toString().trim().ifBlank { "- None" }}
""".trimIndent()

        // 2. Fetch recent turns (last 6 turns within 2 hours)
        val now = System.currentTimeMillis()
        val cutoff = now - CHAT_EXPIRY_MS
        val priorTurns = database.chatTurnDao().getRecentTurns(cutoff, limit = 6)

        // 3. Check for API key & AI consent
        val settings = settingsRepository.getSettingsOnce()
        val apiKeys = settingsRepository.getApiKeysList()
        val model = settings.groqModel.trim().ifBlank { "openai/gpt-oss-120b" }

        val responseText = if (apiKeys.isEmpty() || !settings.aiConsentGranted) {
            // Local fallback answering
            answerLocally(query, totalSpentStr, limitStr, remainingStr, categorySummaries, largestExpenses, recentExpenses, placeSummaries, allPlaces)
        } else {
            // Cloud AI conversational mode
            askGroqChat(query, model, apiKeys, localFacts, priorTurns)
                ?: answerLocally(query, totalSpentStr, limitStr, remainingStr, categorySummaries, largestExpenses, recentExpenses, placeSummaries, allPlaces)
        }

        // 4. Save to chat turn history
        database.chatTurnDao().insertTurn(ChatTurn(role = "user", content = query, createdAt = now))
        database.chatTurnDao().insertTurn(ChatTurn(role = "assistant", content = responseText, createdAt = now + 1))
        database.chatTurnDao().deleteExpiredTurns(cutoff)

        return responseText
    }

    private suspend fun askGroqChat(
        query: String,
        model: String,
        apiKeys: List<String>,
        localFacts: String,
        priorTurns: List<ChatTurn>
    ): String? = withContext(Dispatchers.IO) {
        val systemInstruction = """
You are the dedicated Trip Budget Assistant for a motorcycle trip across Pakistan.
You are in conversational read-only mode for user queries beginning with @chat.

$localFacts

RULES:
1. Ground your answer STRICTLY in the verified ledger facts above.
2. NEVER invent transactions, fictitious balances, or unsupported forecasts.
3. If the user asks whether they can afford an expense, calculate based on Remaining Balance ($localFacts).
4. READ-ONLY CONSTRAINT: If the user asks you to record, add, change budget, or delete an expense in @chat, explain clearly that @chat is read-only, present the proposed action details, and tell them to send the message directly (e.g. 'Petrol 2200' or 'set budget 50000') without @chat.
5. Answer concisely, informatively, and politely in English or Roman Urdu matching the user's question.
6. STRICT DIRECTNESS CONSTRAINT: The user demanded ONLY a direct and clean response. NEVER output your thinking process, internal monologue, reasoning, or <think> tags. Do NOT start with 'Thinking Process:' or explanations of your thoughts. Output ONLY the answer itself.
""".trimIndent()

        val messagesArray = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemInstruction))
            priorTurns.forEach { turn ->
                put(JSONObject().put("role", turn.role).put("content", turn.content))
            }
            put(JSONObject().put("role", "user").put("content", query))
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.3)
            put("max_tokens", 400)
        }

        for (apiKey in apiKeys) {
            try {
                val request = Request.Builder()
                    .url(GROQ_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (response.code == 429 || response.code == 401) {
                    Log.w(TAG, "Groq chat call returned HTTP ${response.code}; trying next key")
                    continue
                }
                if (response.code != 200) {
                    Log.w(TAG, "Groq chat call returned HTTP ${response.code}")
                    break
                }

                val body = response.body?.string() ?: continue
                val jsonResponse = JSONObject(body)
                val choices = jsonResponse.getJSONArray("choices")
                val rawContent = choices.getJSONObject(0).getJSONObject("message").getString("content")
                val cleaned = cleanThinkTags(rawContent)
                if (cleaned.isNotBlank()) {
                    return@withContext cleaned
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception during Groq chat with key: ${e.message}")
            }
        }
        null
    }

    private fun cleanThinkTags(raw: String): String {
        var text = raw

        // 1. Strip complete <think>...</think> and <thought>...</thought> blocks
        text = text.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<thought>[\\s\\S]*?</thought>", RegexOption.IGNORE_CASE), "")

        // 2. If truncated/unclosed think block, extract only outside content
        if (text.contains("<think>", ignoreCase = true)) {
            val thinkIdx = text.indexOf("<think>", ignoreCase = true)
            val closeIdx = text.indexOf("</think>", ignoreCase = true)
            text = if (closeIdx != -1 && closeIdx > thinkIdx) {
                text.removeRange(thinkIdx, closeIdx + 8)
            } else {
                // Was thinking right until token limit or end
                text.substring(0, thinkIdx)
            }
        }
        if (text.contains("<thought>", ignoreCase = true)) {
            val thoughtIdx = text.indexOf("<thought>", ignoreCase = true)
            val closeIdx = text.indexOf("</thought>", ignoreCase = true)
            text = if (closeIdx != -1 && closeIdx > thoughtIdx) {
                text.removeRange(thoughtIdx, closeIdx + 10)
            } else {
                text.substring(0, thoughtIdx)
            }
        }
        text = text.replace(Regex("</?(?:think|thought)>", RegexOption.IGNORE_CASE), "").trim()

        // 3. Remove "Thinking Process:" or "Thought Process:" headers & internal monologue paragraphs
        val thinkingPatterns = listOf(
            Regex("(?i)^[\\*#_\\s]*thinking process:?[\\*#_\\s]*[\\s\\S]*?\\n\\n+", RegexOption.MULTILINE),
            Regex("(?i)^[\\*#_\\s]*thought process:?[\\*#_\\s]*[\\s\\S]*?\\n\\n+", RegexOption.MULTILINE),
            Regex("(?i)^[\\*#_\\s]*reasoning:?[\\*#_\\s]*[\\s\\S]*?\\n\\n+", RegexOption.MULTILINE),
            Regex("(?i)^here'?s my thought process:?[\\s\\S]*?\\n\\n+", RegexOption.MULTILINE)
        )
        for (pattern in thinkingPatterns) {
            text = text.replace(pattern, "").trim()
        }

        // 4. If text still starts with bulleted thinking like "* The user is asking...", skip down to the actual answer
        if (text.startsWith("* The user", ignoreCase = true) || text.startsWith("The user is asking", ignoreCase = true) || text.startsWith("1. Analyze", ignoreCase = true)) {
            val split = text.split(Regex("\n\n+"), limit = 2)
            if (split.size > 1 && !split[1].startsWith("*")) {
                text = split[1].trim()
            }
        }

        return text.ifBlank { "Based on your verified trip ledger, no active balance issue was found." }
    }

    private fun answerLocally(
        query: String,
        totalSpentStr: String,
        limitStr: String,
        remainingStr: String,
        categorySummaries: List<com.example.data.model.CategorySpendSummary>,
        largestExpenses: List<com.example.data.model.Expense>,
        recentExpenses: List<com.example.data.model.Expense>,
        placeSummaries: List<com.example.data.model.PlaceSpendSummary>,
        allPlaces: List<com.example.data.model.Place>
    ): String {
        val q = query.lowercase(Locale.ROOT)

        // Check if query is asking about a specific place
        val matchedPlace = allPlaces.firstOrNull { p ->
            val pName = p.canonicalName.lowercase(Locale.ROOT)
            q.contains(pName) || (pName == "islamabad" && q.contains("isb")) || (pName == "rawalpindi" && q.contains("pindi"))
        }
        if (matchedPlace != null) {
            val ps = placeSummaries.firstOrNull { it.effectivePlaceId == matchedPlace.id }
            return if (ps != null) {
                "${matchedPlace.canonicalName} Spending:\n• Total: ${MoneyFormatter.formatPaisa(ps.totalPaisa)} (${ps.count} expenses)"
            } else {
                "${matchedPlace.canonicalName} Spending:\n• Total: Rs. 0 (No recorded expenses for ${matchedPlace.canonicalName} yet)"
            }
        }

        return when {
            q.contains("afford") || q.contains("left") || q.contains("remaining") || q.contains("balance") || q.contains("bacha") -> {
                "Trip Budget Summary:\n• Total Spent: $totalSpentStr\n• Budget Limit: $limitStr\n• Remaining: $remainingStr"
            }
            q.contains("bara") || q.contains("largest") || q.contains("biggest") || q.contains("highest") -> {
                if (largestExpenses.isNotEmpty()) {
                    val top = largestExpenses.first()
                    "Your largest expense so far is ${top.description} for ${MoneyFormatter.formatPaisa(top.amountPaisa)} (${top.category ?: "Misc"})."
                } else {
                    "No expenses recorded yet."
                }
            }
            q.contains("category") || q.contains("most") || q.contains("spending on") -> {
                if (categorySummaries.isNotEmpty()) {
                    val top = categorySummaries.first()
                    "You are spending the most on ${top.category ?: "Uncategorized"}: ${MoneyFormatter.formatPaisa(top.totalPaisa)} across ${top.count} expenses."
                } else {
                    "No category spending data yet."
                }
            }
            q.contains("recent") || q.contains("latest") || q.contains("last") -> {
                if (recentExpenses.isNotEmpty()) {
                    val sb = StringBuilder("Recent Expenses:\n")
                    recentExpenses.take(3).forEach {
                        sb.append("• ${it.description} — ${MoneyFormatter.formatPaisa(it.amountPaisa)}\n")
                    }
                    sb.toString().trim()
                } else {
                    "No recent expenses recorded."
                }
            }
            else -> {
                "Trip Budget Status:\n• Total Spent: $totalSpentStr\n• Budget Limit: $limitStr\n• Remaining: $remainingStr"
            }
        }
    }
}
