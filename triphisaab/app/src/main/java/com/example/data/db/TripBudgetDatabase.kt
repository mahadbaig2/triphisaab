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
        Transaction::class,
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
    version = 3,
    exportSchema = false
)
abstract class TripBudgetDatabase : RoomDatabase() {
    abstract fun budgetDao(): BudgetDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun transactionDao(): TransactionDao
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create transactions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transactions` (
                        `id` TEXT NOT NULL,
                        `budgetId` TEXT NOT NULL,
                        `sourceEventId` TEXT,
                        `sourceLineIndex` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `direction` TEXT NOT NULL,
                        `amountPaisa` INTEGER NOT NULL,
                        `currency` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `category` TEXT,
                        `subcategory` TEXT,
                        `categoryId` TEXT,
                        `subcategoryId` TEXT,
                        `counterparty` TEXT,
                        `linkedTransactionId` TEXT,
                        `occurrenceTime` INTEGER NOT NULL,
                        `loggedAt` INTEGER NOT NULL,
                        `timeCertainty` TEXT NOT NULL,
                        `timeSource` TEXT NOT NULL,
                        `locationSnapshotId` TEXT,
                        `effectivePlaceId` TEXT,
                        `locationOverride` TEXT,
                        `locationCertainty` TEXT NOT NULL,
                        `messageTime` INTEGER,
                        `status` TEXT NOT NULL,
                        `reversedAt` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_budgetId_status` ON `transactions` (`budgetId`, `status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_direction_status` ON `transactions` (`direction`, `status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_type_status` ON `transactions` (`type`, `status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_effectivePlaceId` ON `transactions` (`effectivePlaceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_occurrenceTime` ON `transactions` (`occurrenceTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_counterparty` ON `transactions` (`counterparty`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_sourceEventId_sourceLineIndex` ON `transactions` (`sourceEventId`, `sourceLineIndex`)")

                // 2. Copy existing active and reversed expenses to transactions
                db.execSQL("""
                    INSERT OR IGNORE INTO `transactions` (
                        `id`, `budgetId`, `sourceEventId`, `sourceLineIndex`,
                        `type`, `direction`, `amountPaisa`, `currency`,
                        `description`, `category`, `subcategory`, `categoryId`, `subcategoryId`,
                        `counterparty`, `linkedTransactionId`, `occurrenceTime`, `loggedAt`,
                        `timeCertainty`, `timeSource`, `locationSnapshotId`, `effectivePlaceId`,
                        `locationOverride`, `locationCertainty`, `messageTime`, `status`, `reversedAt`
                    )
                    SELECT
                        `id`, `budgetId`, `sourceEventId`, `sourceLineIndex`,
                        'EXPENSE', 'OUTGOING', `amountPaisa`, `currency`,
                        `description`, `category`, `subcategory`, `categoryId`, `subcategoryId`,
                        NULL, NULL, `expenseTime`, `loggedAt`,
                        `timeCertainty`, `timeSource`, `locationSnapshotId`, `effectivePlaceId`,
                        `locationOverride`, `locationCertainty`, `messageTime`, `status`, `reversedAt`
                    FROM `expenses`
                """.trimIndent())
            }
        }

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

            for (cat in com.example.data.repository.TaxonomyData.CATEGORIES) {
                insertCat(cat.id, cat.name, cat.normalizedName)
                for (sub in cat.subcategories) {
                    insertSub(sub.id, cat.id, sub.name, sub.normalizedName)
                }
            }
        }

        suspend fun seedInitialData(database: TripBudgetDatabase) {
            // Seed active budget if none exists
            val existingBudget = database.budgetDao().getActiveBudgetOnce()
            if (existingBudget == null) {
                val budget = Budget(
                    id = UUID.randomUUID().toString(),
                    name = "Motorcycle Trip",
                    currency = "PKR",
                    limitPaisa = 100000_00L
                )
                database.budgetDao().insertBudget(budget)
            }

            // Seed default settings
            database.settingsDao().insertOrUpdate(
                AppSettings(
                    id = 1,
                    groqModel = "openai/gpt-oss-120b",
                    strictMode = false,
                    timezone = "Asia/Karachi"
                )
            )

            // Seed comprehensive 24 Categories & Subcategories
            val defaultCategories = com.example.data.repository.TaxonomyData.CATEGORIES.map { cat ->
                Category(id = cat.id, name = cat.name, normalizedName = cat.normalizedName, isDefault = true)
            }
            database.categoryDao().insertCategories(defaultCategories)

            val defaultSubcategories = com.example.data.repository.TaxonomyData.CATEGORIES.flatMap { cat ->
                cat.subcategories.map { sub ->
                    Subcategory(id = sub.id, categoryId = cat.id, name = sub.name, normalizedName = sub.normalizedName, isDefault = true)
                }
            }
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

            // 2. Ensure all taxonomy categories are seeded
            val currentCats = database.categoryDao().getAllCategoriesList()
            if (currentCats.size < com.example.data.repository.TaxonomyData.CATEGORIES.size) {
                val cats = com.example.data.repository.TaxonomyData.CATEGORIES.map { cat ->
                    Category(id = cat.id, name = cat.name, normalizedName = cat.normalizedName, isDefault = true)
                }
                database.categoryDao().insertCategories(cats)
                val subs = com.example.data.repository.TaxonomyData.CATEGORIES.flatMap { cat ->
                    cat.subcategories.map { sub ->
                        Subcategory(id = sub.id, categoryId = cat.id, name = sub.name, normalizedName = sub.normalizedName, isDefault = true)
                    }
                }
                database.subcategoryDao().insertSubcategories(subs)
            }

            // 3. Only attribute place if supported by coordinates or explicit override; DO NOT default to Islamabad
            val unplaced = database.expenseDao().getExpensesWithoutEffectivePlace()
            if (unplaced.isNotEmpty()) {
                for (exp in unplaced) {
                    val snapshot = exp.locationSnapshotId?.let { database.locationSnapshotDao().getSnapshotById(it) }
                    val locName = exp.locationOverride ?: snapshot?.locality ?: snapshot?.district
                    if (!locName.isNullOrBlank()) {
                        val matchedPlace = currentPlaces.firstOrNull {
                            it.canonicalName.equals(locName, ignoreCase = true) ||
                            it.aliasesJson.contains("\"${locName.lowercase()}\"", ignoreCase = true)
                        }
                        if (matchedPlace != null) {
                            database.expenseDao().updateEffectivePlace(exp.id, matchedPlace.id)
                        }
                    }
                }
            }
        }
    }
}
