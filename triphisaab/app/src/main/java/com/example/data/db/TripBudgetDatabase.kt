package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        Budget::class,
        PairedConversation::class,
        InboxEvent::class,
        Expense::class,
        LocationSnapshot::class,
        Place::class,
        PendingProposal::class,
        ReplyTask::class,
        AuditEntry::class,
        AppSettings::class,
        Category::class,
        Subcategory::class,
        ChatTurn::class
    ],
    version = 2,
    exportSchema = false
)
abstract class TripBudgetDatabase : RoomDatabase() {
    abstract fun budgetDao(): BudgetDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun pairedConversationDao(): PairedConversationDao
    abstract fun inboxEventDao(): InboxEventDao
    abstract fun locationSnapshotDao(): LocationSnapshotDao
    abstract fun placeDao(): PlaceDao
    abstract fun pendingProposalDao(): PendingProposalDao
    abstract fun replyTaskDao(): ReplyTaskDao
    abstract fun auditDao(): AuditDao
    abstract fun settingsDao(): SettingsDao
    abstract fun categoryDao(): CategoryDao
    abstract fun subcategoryDao(): SubcategoryDao
    abstract fun chatTurnDao(): ChatTurnDao

    companion object {
        @Volatile
        private var INSTANCE: TripBudgetDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create categories table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `normalizedName` TEXT NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_normalizedName` ON `categories` (`normalizedName`)")

                // 2. Create subcategories table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `subcategories` (
                        `id` TEXT NOT NULL,
                        `categoryId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `normalizedName` TEXT NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subcategories_categoryId_normalizedName` ON `subcategories` (`categoryId`, `normalizedName`)")

                // 3. Create chat_turns table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_turns` (
                        `id` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_turns_createdAt` ON `chat_turns` (`createdAt`)")

                // 4. Add new columns to expenses
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `subcategory` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `categoryId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `subcategoryId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `timeCertainty` TEXT NOT NULL DEFAULT 'EXACT'")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `timeSource` TEXT NOT NULL DEFAULT 'MESSAGE_TIMESTAMP'")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `locationCertainty` TEXT NOT NULL DEFAULT 'DEVICE'")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `messageTime` INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_categoryId` ON `expenses` (`categoryId`)")

                // 5. Seed default taxonomy in SQLite directly
                seedTaxonomySql(db)

                // 6. Backfill existing expenses category references if available
                db.execSQL("UPDATE `expenses` SET `category` = 'Miscellaneous' WHERE `category` IS NULL OR `category` = ''")
            }
        }

        fun getDatabase(context: Context): TripBudgetDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TripBudgetDatabase::class.java,
                    "trip_budget.db"
                )
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedInitialData(getDatabase(context))
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            ensureSeeded(getDatabase(context))
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun seedTaxonomySql(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            fun insertCat(id: String, name: String, norm: String) {
                db.execSQL("INSERT OR IGNORE INTO categories (id, name, normalizedName, isDefault, createdAt) VALUES ('$id', '$name', '$norm', 1, $now)")
            }
            fun insertSub(id: String, catId: String, name: String, norm: String) {
                db.execSQL("INSERT OR IGNORE INTO subcategories (id, categoryId, name, normalizedName, isDefault, createdAt) VALUES ('$id', '$catId', '$name', '$norm', 1, $now)")
            }

            insertCat("cat_transport", "Transport", "transport")
            insertSub("sub_fuel", "cat_transport", "Fuel", "fuel")
            insertSub("sub_taxi", "cat_transport", "Taxi / Local Transport", "taxi / local transport")

            insertCat("cat_accommodation", "Accommodation", "accommodation")

            insertCat("cat_food", "Food & Drinks", "food & drinks")
            insertSub("sub_meals", "cat_food", "Meals", "meals")
            insertSub("sub_tea", "cat_food", "Tea / Coffee", "tea / coffee")
            insertSub("sub_snacks", "cat_food", "Snacks", "snacks")

            insertCat("cat_motorcycle", "Motorcycle", "motorcycle")
            insertSub("sub_maint", "cat_motorcycle", "Maintenance", "maintenance")
            insertSub("sub_repairs", "cat_motorcycle", "Repairs", "repairs")
            insertSub("sub_acc", "cat_motorcycle", "Accessories", "accessories")

            insertCat("cat_shopping", "Shopping", "shopping")
            insertCat("cat_fees", "Fees & Permits", "fees & permits")
            insertCat("cat_misc", "Miscellaneous", "miscellaneous")
        }

        suspend fun seedInitialData(database: TripBudgetDatabase) {
            // Seed active budget
            val budget = Budget(
                id = UUID.randomUUID().toString(),
                name = "Motorcycle Trip",
                currency = "PKR",
                limitPaisa = 100000_00L // Default limit Rs. 100,000 (can be updated by user)
            )
            database.budgetDao().insertBudget(budget)

            // Seed default settings
            database.settingsDao().insertOrUpdate(
                AppSettings(
                    id = 1,
                    groqModel = "openai/gpt-oss-120b",
                    strictMode = false,
                    timezone = "Asia/Karachi"
                )
            )

            // Seed Categories & Subcategories
            val defaultCategories = listOf(
                Category(id = "cat_transport", name = "Transport", normalizedName = "transport", isDefault = true),
                Category(id = "cat_accommodation", name = "Accommodation", normalizedName = "accommodation", isDefault = true),
                Category(id = "cat_food", name = "Food & Drinks", normalizedName = "food & drinks", isDefault = true),
                Category(id = "cat_motorcycle", name = "Motorcycle", normalizedName = "motorcycle", isDefault = true),
                Category(id = "cat_shopping", name = "Shopping", normalizedName = "shopping", isDefault = true),
                Category(id = "cat_fees", name = "Fees & Permits", normalizedName = "fees & permits", isDefault = true),
                Category(id = "cat_misc", name = "Miscellaneous", normalizedName = "miscellaneous", isDefault = true)
            )
            database.categoryDao().insertCategories(defaultCategories)

            val defaultSubcategories = listOf(
                Subcategory(id = "sub_fuel", categoryId = "cat_transport", name = "Fuel", normalizedName = "fuel", isDefault = true),
                Subcategory(id = "sub_taxi", categoryId = "cat_transport", name = "Taxi / Local Transport", normalizedName = "taxi / local transport", isDefault = true),
                Subcategory(id = "sub_meals", categoryId = "cat_food", name = "Meals", normalizedName = "meals", isDefault = true),
                Subcategory(id = "sub_tea", categoryId = "cat_food", name = "Tea / Coffee", normalizedName = "tea / coffee", isDefault = true),
                Subcategory(id = "sub_snacks", categoryId = "cat_food", name = "Snacks", normalizedName = "snacks", isDefault = true),
                Subcategory(id = "sub_maint", categoryId = "cat_motorcycle", name = "Maintenance", normalizedName = "maintenance", isDefault = true),
                Subcategory(id = "sub_repairs", categoryId = "cat_motorcycle", name = "Repairs", normalizedName = "repairs", isDefault = true),
                Subcategory(id = "sub_acc", categoryId = "cat_motorcycle", name = "Accessories", normalizedName = "accessories", isDefault = true)
            )
            database.subcategoryDao().insertSubcategories(defaultSubcategories)

            // Seed known trip corridor places
            val places = listOf(
                Place(id = "place_isb", canonicalName = "Islamabad", type = "LOCALITY", aliasesJson = "[\"isb\",\"islamabad\"]"),
                Place(id = "place_rwp", canonicalName = "Rawalpindi", type = "LOCALITY", aliasesJson = "[\"pindi\",\"rawalpindi\"]"),
                Place(id = "place_abb", canonicalName = "Abbottabad", type = "LOCALITY", aliasesJson = "[\"abbottabad\"]"),
                Place(id = "place_man", canonicalName = "Mansehra", type = "LOCALITY", aliasesJson = "[\"mansehra\"]"),
                Place(id = "place_bes", canonicalName = "Besham", type = "LOCALITY", aliasesJson = "[\"besham\"]"),
                Place(id = "place_chi", canonicalName = "Chilas", type = "LOCALITY", aliasesJson = "[\"chilas\"]"),
                Place(id = "place_gil_city", canonicalName = "Gilgit", type = "LOCALITY", aliasesJson = "[\"gilgit\",\"gilgit city\"]"),
                Place(id = "place_gil_dist", canonicalName = "Gilgit District", type = "DISTRICT", aliasesJson = "[\"gilgit district\",\"district gilgit\"]"),
                Place(id = "place_hunza_reg", canonicalName = "Hunza", type = "REGION", aliasesJson = "[\"hunza\",\"hunza valley\"]"),
                Place(id = "place_karim", canonicalName = "Karimabad", type = "LOCALITY", parentId = "place_hunza_reg", aliasesJson = "[\"karimabad\",\"baltit\"]"),
                Place(id = "place_ali", canonicalName = "Aliabad", type = "LOCALITY", parentId = "place_hunza_reg", aliasesJson = "[\"aliabad\"]"),
                Place(id = "place_passu", canonicalName = "Passu", type = "LOCALITY", aliasesJson = "[\"passu\"]"),
                Place(id = "place_sost", canonicalName = "Sost", type = "LOCALITY", aliasesJson = "[\"sost\",\"sust\"]"),
                Place(id = "place_khun", canonicalName = "Khunjerab", type = "LOCALITY", aliasesJson = "[\"khunjerab\",\"khunjerab pass\",\"china border\"]"),
                Place(id = "place_naran", canonicalName = "Naran", type = "LOCALITY", aliasesJson = "[\"naran\"]"),
                Place(id = "place_babusar", canonicalName = "Babusar", type = "LOCALITY", aliasesJson = "[\"babusar\",\"babusar top\"]"),
                Place(id = "place_skardu", canonicalName = "Skardu", type = "LOCALITY", aliasesJson = "[\"skardu\"]")
            )
            database.placeDao().insertPlaces(places)
        }

        suspend fun ensureSeeded(database: TripBudgetDatabase) {
            // 1. Ensure places exist
            val currentPlaces = database.placeDao().getAllPlacesList()
            if (currentPlaces.isEmpty()) {
                seedInitialData(database)
                return
            }

            // 2. Backfill any active expenses without effectivePlaceId
            val unplaced = database.expenseDao().getExpensesWithoutEffectivePlace()
            if (unplaced.isNotEmpty()) {
                val isbPlace = currentPlaces.firstOrNull { it.canonicalName.equals("Islamabad", ignoreCase = true) }
                for (exp in unplaced) {
                    val snapshot = exp.locationSnapshotId?.let { database.locationSnapshotDao().getSnapshotById(it) }
                    val locName = exp.locationOverride ?: snapshot?.locality ?: snapshot?.district
                    val matchedPlace = if (!locName.isNullOrBlank()) {
                        currentPlaces.firstOrNull {
                            it.canonicalName.equals(locName, ignoreCase = true) ||
                            it.aliasesJson.contains("\"${locName.lowercase()}\"", ignoreCase = true)
                        } ?: isbPlace
                    } else {
                        isbPlace
                    }
                    if (matchedPlace != null) {
                        database.expenseDao().updateEffectivePlace(exp.id, matchedPlace.id)
                    }
                }
            }

            // 3. Re-classify any active expenses that defaulted to Miscellaneous or uncategorized
            val miscExpenses = database.expenseDao().getMiscellaneousOrUncategorizedExpenses()
            if (miscExpenses.isNotEmpty()) {
                val allCats = database.categoryDao().getAllCategoriesList()
                val allSubs = database.subcategoryDao().getAllSubcategoriesList()
                for (exp in miscExpenses) {
                    val match = com.example.data.repository.CategoryTaxonomyManager.detectFromKeywords(exp.description)
                    if (match != null) {
                        val catObj = allCats.firstOrNull { it.name.equals(match.categoryName, ignoreCase = true) }
                        val subObj = if (match.subcategoryName != null) {
                            allSubs.firstOrNull { it.name.equals(match.subcategoryName, ignoreCase = true) }
                        } else null
                        database.expenseDao().updateCategoryDetails(
                            id = exp.id,
                            categoryId = catObj?.id,
                            category = match.categoryName,
                            subcategoryId = subObj?.id,
                            subcategory = match.subcategoryName
                        )
                    }
                }
            }
        }
    }
}
