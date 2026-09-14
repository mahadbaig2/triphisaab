package com.example.data.repository

import com.example.data.db.CategoryDao
import com.example.data.db.SubcategoryDao
import com.example.data.model.Category
import com.example.data.model.Subcategory
import java.util.Locale
import java.util.UUID

class CategoryTaxonomyManager(
    private val categoryDao: CategoryDao,
    private val subcategoryDao: SubcategoryDao
) {
    data class ResolvedTaxonomy(
        val categoryId: String?,
        val categoryName: String,
        val subcategoryId: String?,
        val subcategoryName: String?
    )

    /**
     * Resolves an expense's description, raw category proposal, and optional subcategory
     * against the local database taxonomy. Reuses existing categories before proposing new ones.
     */
    suspend fun resolve(
        description: String,
        proposedCategory: String? = null,
        proposedSubcategory: String? = null
    ): ResolvedTaxonomy {
        val descLower = description.lowercase(Locale.ROOT).trim()

        // 1. If LLM or caller provided an explicit intelligent category, validate and use/create it
        val cleanCat = proposedCategory?.trim()?.replace(Regex("[_\\-]+"), " ")
        if (!cleanCat.isNullOrBlank() && !cleanCat.equals("Miscellaneous", ignoreCase = true) && isValidCategoryCandidate(cleanCat)) {
            val normalizedCat = cleanCat.lowercase(Locale.ROOT)
            val matchedCat = findCategoryByFuzzyOrAlias(normalizedCat)
            if (matchedCat != null) {
                val cleanSub = proposedSubcategory?.trim()?.replace(Regex("[_\\-]+"), " ")
                val sub = if (!cleanSub.isNullOrBlank() && isValidCategoryCandidate(cleanSub)) {
                    findOrGetSubcategory(matchedCat.id, titleCase(cleanSub))
                } else null

                return ResolvedTaxonomy(
                    categoryId = matchedCat.id,
                    categoryName = matchedCat.name,
                    subcategoryId = sub?.id,
                    subcategoryName = sub?.name
                )
            } else {
                // Dynamically create the new justified category chosen by LLM (e.g. 'Lending', 'Charity', etc.)
                val newCat = createJustifiedCategory(cleanCat)
                val cleanSub = proposedSubcategory?.trim()?.replace(Regex("[_\\-]+"), " ")
                val sub = if (!cleanSub.isNullOrBlank() && isValidCategoryCandidate(cleanSub)) {
                    findOrGetSubcategory(newCat.id, titleCase(cleanSub))
                } else null

                return ResolvedTaxonomy(
                    categoryId = newCat.id,
                    categoryName = newCat.name,
                    subcategoryId = sub?.id,
                    subcategoryName = sub?.name
                )
            }
        }

        // 2. Keyword fallback lookup based on description keywords
        val aliasMatch = detectFromKeywords(descLower)
        if (aliasMatch != null) {
            val cat = findOrGetCategory(aliasMatch.categoryName)
            val sub = aliasMatch.subcategoryName?.let { findOrGetSubcategory(cat.id, it) }
            return ResolvedTaxonomy(
                categoryId = cat.id,
                categoryName = cat.name,
                subcategoryId = sub?.id,
                subcategoryName = sub?.name
            )
        }

        // 3. Fallback to Miscellaneous
        val misc = findOrGetCategory("Miscellaneous")
        return ResolvedTaxonomy(
            categoryId = misc.id,
            categoryName = misc.name,
            subcategoryId = null,
            subcategoryName = null
        )
    }

    companion object {
        data class AliasResult(val categoryName: String, val subcategoryName: String?)

        fun detectFromKeywords(text: String): AliasResult? {
            val t = text.lowercase(Locale.ROOT)

            // 1. Check comprehensive TaxonomyData first
            val matched = TaxonomyData.findMatchingSubcategory(t)
            if (matched != null) {
                return AliasResult(matched.first.name, matched.second.name)
            }

            return when {
                // Fuel
                t.contains("petrol") || t.contains("diesel") || t.contains("fuel") || t.contains("fueling") || t.contains("cng") || t.contains("tanki") ->
                    AliasResult("Transport", "Motorcycle Fuel")

            // Taxi / Local transport
            t.contains("taxi") || t.contains("cab") || t.contains("rickshaw") || t.contains("rikshaw") || t.contains("auto") ||
            t.contains("careem") || t.contains("uber") || t.contains("indrive") || t.contains("bus") || t.contains("van") || t.contains("hiace") ||
            t.contains("coaster") || t.contains("daewoo") || t.contains("metro") || t.contains("train") ->
                AliasResult("Transport", "Taxi / Local Transport")

            // General Transport & Tolls
            t.contains("toll") || t.contains("motorway toll") || t.contains("fare") || t.contains("kiraya") ->
                AliasResult("Transport", null)

            // Accommodation
            t.contains("hotel") || t.contains("room") || t.contains("stay") || t.contains("guest house") ||
            t.contains("resort") || t.contains("inn") || t.contains("camp") || t.contains("camping") || t.contains("glamping") ||
            t.contains("motel") || t.contains("lodge") || t.contains("hostel") || t.contains("tent") ->
                AliasResult("Accommodation", null)

            // Motorcycle specific compounds (e.g. "Bike ki accessories", "Bike maintenance")
            t.contains("bike") || t.contains("motorcycle") || t.contains("motorbike") || t.contains("heavy bike") ||
            t.contains("70cc") || t.contains("125") || t.contains("ybr") || t.contains("gs150") || t.contains("cb150") -> {
                when {
                    t.contains("accessori") || t.contains("accessory") || t.contains("parts") || t.contains("mirror") ||
                    t.contains("visor") || t.contains("light") || t.contains("led") || t.contains("guard") || t.contains("mount") ||
                    t.contains("holder") || t.contains("bag") || t.contains("seat") ->
                        AliasResult("Motorcycle", "Accessories")
                    t.contains("maint") || t.contains("service") || t.contains("oil") || t.contains("tuning") ||
                    t.contains("tune") || t.contains("wash") || t.contains("filter") ->
                        AliasResult("Motorcycle", "Maintenance")
                    t.contains("repair") || t.contains("mechanic") || t.contains("punct") || t.contains("puncher") ||
                    t.contains("hawa") || t.contains("brake") || t.contains("break") || t.contains("chain") ||
                    t.contains("tyre") || t.contains("tire") || t.contains("tube") || t.contains("wire") || t.contains("weld") ->
                        AliasResult("Motorcycle", "Repairs")
                    else ->
                        AliasResult("Motorcycle", null)
                }
            }

            // Motorcycle Accessories Standalone
            t.contains("helmet") || t.contains("gloves") || t.contains("jacket") || t.contains("guards") ||
            t.contains("knee guard") || t.contains("elbow guard") || t.contains("mount") || t.contains("holder") ||
            t.contains("saddle bag") || t.contains("tank bag") || t.contains("pannier") || t.contains("top box") ||
            t.contains("bungee") || t.contains("strap") || t.contains("accessories") || t.contains("accessory") ||
            t.contains("fog light") || t.contains("crash guard") ->
                AliasResult("Motorcycle", "Accessories")

            // Motorcycle Maintenance Standalone
            t.contains("oil change") || t.contains("engine oil") || t.contains("tuning") || t.contains("service") ||
            t.contains("mobil oil") || t.contains("chain lube") || t.contains("air filter") || t.contains("oil filter") ||
            t.contains("bike wash") || t.contains("maintenance") ->
                AliasResult("Motorcycle", "Maintenance")

            // Motorcycle Repairs Standalone
            t.contains("puncture") || t.contains("puncher") || t.contains("mechanic") || t.contains("break") ||
            t.contains("brake") || t.contains("chain") || t.contains("tyre") || t.contains("tire") || t.contains("tube") ||
            t.contains("spark plug") || t.contains("clutch") || t.contains("sprocket") || t.contains("hawa") ->
                AliasResult("Motorcycle", "Repairs")

            // Meals (Breakfast / Nashta, Lunch, Dinner, etc.)
            t.contains("dinner") || t.contains("lunch") || t.contains("breakfast") || t.contains("nashta") ||
            t.contains("khana") || t.contains("biryani") || t.contains("karahi") || t.contains("roti") || t.contains("naan") ||
            t.contains("paratha") || t.contains("food") || t.contains("meal") || t.contains("burger") || t.contains("pizza") ||
            t.contains("daal") || t.contains("salan") || t.contains("chawal") || t.contains("rice") || t.contains("bbq") ||
            t.contains("tikka") || t.contains("kebab") || t.contains("kabab") || t.contains("roll") || t.contains("shawarma") ||
            t.contains("sandwich") || t.contains("fish") || t.contains("nihari") || t.contains("haleem") || t.contains("anda") ||
            t.contains("omelette") || t.contains("buffet") ->
                AliasResult("Food & Drinks", "Meals")

            // Tea / Coffee
            t.contains("chai") || t.contains("chaye") || t.contains("tea") || t.contains("coffee") || t.contains("green tea") ||
            t.contains("qehwa") || t.contains("kahwa") || t.contains("doodh patti") || t.contains("kashmiri chai") ||
            t.contains("latte") || t.contains("cappuccino") || t.contains("espresso") ->
                AliasResult("Food & Drinks", "Tea / Coffee")

            // Snacks & Beverages
            t.contains("snack") || t.contains("chips") || t.contains("lays") || t.contains("biscuits") || t.contains("biscuit") ||
            t.contains("nimco") || t.contains("samosa") || t.contains("pakora") || t.contains("juice") || t.contains("water") ||
            t.contains("pani") || t.contains("mineral water") || t.contains("cold drink") || t.contains("coke") ||
            t.contains("pepsi") || t.contains("sting") || t.contains("red bull") || t.contains("sprite") || t.contains("7up") ||
            t.contains("dew") || t.contains("sweet") || t.contains("mithai") || t.contains("ice cream") || t.contains("kulfa") ||
            t.contains("falooda") || t.contains("fruit") || t.contains("fruits") ->
                AliasResult("Food & Drinks", "Snacks")

            // Personal Care & Toiletries (e.g. Body spray, soap, shampoo)
            t.contains("spray") || t.contains("spary") || t.contains("body spray") || t.contains("perfume") || t.contains("itr") ||
            t.contains("attar") || t.contains("deodorant") || t.contains("soap") || t.contains("shampoo") || t.contains("facewash") ||
            t.contains("face wash") || t.contains("toothpaste") || t.contains("toothbrush") || t.contains("sunscreen") ||
            t.contains("sunblock") || t.contains("lotion") || t.contains("cream") || t.contains("tissue") || t.contains("wipes") ||
            t.contains("lip balm") || t.contains("sanitizer") ->
                AliasResult("Shopping", "Personal Care")

            // Health & Medical
            t.contains("panadol") || t.contains("disprin") || t.contains("medicine") || t.contains("dawa") || t.contains("dawai") ||
            t.contains("tablet") || t.contains("capsule") || t.contains("syrup") || t.contains("bandage") || t.contains("saniplast") ||
            t.contains("first aid") || t.contains("ors") || t.contains("flagyl") || t.contains("doctor") ->
                AliasResult("Shopping", "Health & Medical")

            // Supplies & Utilities (Lighter, matchbox, lock, etc.)
            t.contains("lighter") || t.contains("matchbox") || t.contains("machis") || t.contains("maachis") ||
            t.contains("rope") || t.contains("battery") || t.contains("torch") || t.contains("flashlight") ||
            t.contains("tape") || t.contains("super glue") || t.contains("elfy") || t.contains("lock") ||
            t.contains("padlock") || t.contains("taala") || t.contains("charger") || t.contains("cable") ->
                AliasResult("Miscellaneous", "Supplies")

            // Communication / Mobile Load
            t.contains("easyload") || t.contains("mobile balance") || t.contains("load") || t.contains("sim") ||
            t.contains("telenor") || t.contains("jazz") || t.contains("zong") || t.contains("ufone") || t.contains("scom") ||
            t.contains("internet package") || t.contains("data package") ->
                AliasResult("Miscellaneous", "Communication")

            // Shopping & Souvenirs
            t.contains("shopping") || t.contains("gift") || t.contains("souvenir") || t.contains("shawl") ||
            t.contains("clothes") || t.contains("kapray") || t.contains("dry fruit") || t.contains("badam") ||
            t.contains("akhrot") || t.contains("chilgoza") || t.contains("apricot") || t.contains("topi") || t.contains("cap") ->
                AliasResult("Shopping", null)

            // Fees & Permits
            t.contains("permit") || t.contains("entry fee") || t.contains("pass") || t.contains("ticket") ||
            t.contains("challan") || t.contains("fine") || t.contains("parking") ->
                AliasResult("Fees & Permits", null)

            // Charity & Religious Giving (Sadqa, Zakat, Khairat, Donation, Charity)
            t.contains("sadqa") || t.contains("sadqah") || t.contains("zakat") || t.contains("zakaat") ||
            t.contains("khairat") || t.contains("khairaat") || t.contains("donation") || t.contains("charity") ||
            t.contains("bheek") || t.contains("faqqeer") || t.contains("faqeer") || t.contains("masjid") || t.contains("madrasa") -> {
                val sub = when {
                    t.contains("sadqa") || t.contains("sadqah") -> "Sadqa"
                    t.contains("zakat") || t.contains("zakaat") -> "Zakat"
                    else -> "Donations"
                }
                AliasResult("Charity & Donations", sub)
            }

            // Lending / Udhaar (Borrowing, loaning to friend)
            t.contains("udhaar") || t.contains("udhar") || t.contains("loan") || t.contains("qarz") || t.contains("qarza") ->
                AliasResult("Lending", "Personal Loan")

            else -> null
        }
    }
    }

    private suspend fun findOrGetCategory(name: String): Category {
        val norm = name.trim().lowercase(Locale.ROOT)
        val existing = categoryDao.getByNormalizedName(norm)
        if (existing != null) return existing

        val newCat = Category(
            name = titleCase(name),
            normalizedName = norm,
            isDefault = false
        )
        categoryDao.insertCategory(newCat)
        return categoryDao.getByNormalizedName(norm) ?: newCat
    }

    private suspend fun findOrGetSubcategory(categoryId: String, name: String): Subcategory {
        val norm = name.trim().lowercase(Locale.ROOT)
        val existing = subcategoryDao.getByCategoryIdAndNormalizedName(categoryId, norm)
        if (existing != null) return existing

        val newSub = Subcategory(
            categoryId = categoryId,
            name = titleCase(name),
            normalizedName = norm,
            isDefault = false
        )
        subcategoryDao.insertSubcategory(newSub)
        return subcategoryDao.getByCategoryIdAndNormalizedName(categoryId, norm) ?: newSub
    }

    private suspend fun findCategoryByFuzzyOrAlias(normalizedCat: String): Category? {
        val allCats = categoryDao.getAllCategoriesList()
        val direct = allCats.firstOrNull { it.normalizedName == normalizedCat }
        if (direct != null) return direct

        // Fuzzy match
        for (cat in allCats) {
            if (cat.normalizedName.contains(normalizedCat) || normalizedCat.contains(cat.normalizedName)) {
                return cat
            }
        }
        return null
    }

    private suspend fun createJustifiedCategory(raw: String): Category {
        val cleanName = titleCase(raw.trim())
        val norm = cleanName.lowercase(Locale.ROOT)
        val existing = categoryDao.getByNormalizedName(norm)
        if (existing != null) return existing

        val cat = Category(
            name = cleanName,
            normalizedName = norm,
            isDefault = false
        )
        categoryDao.insertCategory(cat)
        return categoryDao.getByNormalizedName(norm) ?: cat
    }

    private fun isValidCategoryCandidate(str: String): Boolean {
        // Enforce that category is a general spending class, not an individual transaction
        // (Reject if too long, contains dates, digits, or sentence-like structures)
        if (str.length > 30) return false
        if (str.any { it.isDigit() }) return false
        val words = str.split("\\s+".toRegex())
        if (words.size > 3) return false
        val lower = str.lowercase(Locale.ROOT)
        val forbidden = listOf("friday", "monday", "yesterday", "tomorrow", "gilgit", "hunza", "paisa", "rs", "rupees", "bought", "spent")
        if (forbidden.any { lower.contains(it) }) return false
        return true
    }

    private fun titleCase(input: String): String {
        return input.split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private data class AliasResult(val categoryName: String, val subcategoryName: String?)
}
