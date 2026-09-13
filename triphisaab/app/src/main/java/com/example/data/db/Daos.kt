package com.example.data.db

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets LIMIT 1")
    fun getActiveBudget(): Flow<Budget?>

    @Query("SELECT * FROM budgets LIMIT 1")
    suspend fun getActiveBudgetOnce(): Budget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget)

    @Update
    suspend fun updateBudget(budget: Budget)

    @Query("UPDATE budgets SET limitPaisa = :limitPaisa, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLimit(id: String, limitPaisa: Long?, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE status = 'ACTIVE' ORDER BY expenseTime DESC, loggedAt DESC")
    fun getAllActiveExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE status = 'ACTIVE' ORDER BY expenseTime DESC, loggedAt DESC LIMIT :limit")
    fun getRecentExpenses(limit: Int = 10): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: String): Expense?

    @Query("SELECT * FROM expenses WHERE status = 'ACTIVE' ORDER BY loggedAt DESC LIMIT 1")
    suspend fun getLastActiveExpense(): Expense?

    @Query("SELECT SUM(amountPaisa) FROM expenses WHERE status = 'ACTIVE'")
    fun getTotalSpentActive(): Flow<Long?>

    @Query("SELECT SUM(amountPaisa) FROM expenses WHERE status = 'ACTIVE'")
    suspend fun getTotalSpentActiveOnce(): Long?

    @Query("SELECT SUM(amountPaisa) FROM expenses WHERE status = 'ACTIVE' AND expenseTime >= :startTime AND expenseTime <= :endTime")
    suspend fun getTotalSpentActiveBetween(startTime: Long, endTime: Long): Long?

    @Query("SELECT * FROM expenses WHERE status = 'ACTIVE' AND expenseTime >= :startTime AND expenseTime <= :endTime ORDER BY expenseTime DESC")
    suspend fun getExpensesBetween(startTime: Long, endTime: Long): List<Expense>

    @Query("SELECT SUM(amountPaisa) FROM expenses WHERE status = 'ACTIVE' AND (effectivePlaceId = :placeId OR LOWER(locationOverride) = LOWER(:placeName))")
    suspend fun getTotalSpentActiveByPlace(placeId: String, placeName: String): Long?

    @Query("SELECT COUNT(*) FROM expenses WHERE status = 'ACTIVE' AND (effectivePlaceId = :placeId OR LOWER(locationOverride) = LOWER(:placeName))")
    suspend fun countActiveExpensesByPlace(placeId: String, placeName: String): Int

    @Query("SELECT * FROM expenses WHERE status = 'ACTIVE' AND (effectivePlaceId = :placeId OR LOWER(locationOverride) = LOWER(:placeName)) ORDER BY expenseTime DESC LIMIT :limit OFFSET :offset")
    suspend fun getExpensesByPlacePaged(placeId: String, placeName: String, limit: Int, offset: Int): List<Expense>

    @Query("SELECT SUM(amountPaisa) FROM expenses WHERE status = 'ACTIVE' AND (effectivePlaceId IS NULL OR effectivePlaceId = '')")
    suspend fun getTotalSpentActiveWithoutPlace(): Long?

    @Query("SELECT COUNT(*) FROM expenses WHERE status = 'ACTIVE' AND (effectivePlaceId IS NULL OR effectivePlaceId = '')")
    suspend fun countActiveExpensesWithoutPlace(): Int

    @Query("SELECT * FROM expenses WHERE status = 'ACTIVE' AND (effectivePlaceId IS NULL OR effectivePlaceId = '') ORDER BY expenseTime DESC LIMIT :limit OFFSET :offset")
    suspend fun getExpensesWithoutPlacePaged(limit: Int, offset: Int): List<Expense>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpenses(expenses: List<Expense>)

    @Update
    suspend fun updateExpense(expense: Expense)

    @Query("UPDATE expenses SET status = 'REVERSED', reversedAt = :timestamp WHERE id = :id")
    suspend fun reverseExpense(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE expenses SET status = 'REVERSED', reversedAt = :timestamp WHERE sourceEventId = :eventId")
    suspend fun reverseExpensesByEvent(eventId: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM expenses WHERE sourceEventId = :eventId")
    suspend fun getExpensesForEvent(eventId: String): List<Expense>

    @Query("SELECT * FROM expenses ORDER BY loggedAt DESC")
    suspend fun getAllExpensesList(): List<Expense>

    @Query("SELECT * FROM expenses WHERE status = 'ACTIVE' ORDER BY expenseTime DESC, loggedAt DESC LIMIT :limit")
    suspend fun getRecentExpensesList(limit: Int = 10): List<Expense>

    @Query("SELECT * FROM expenses WHERE status = 'ACTIVE' ORDER BY amountPaisa DESC LIMIT :limit")
    suspend fun getLargestExpenses(limit: Int = 5): List<Expense>

    @Query("SELECT * FROM expenses WHERE status = 'ACTIVE' AND category = :category ORDER BY expenseTime DESC")
    suspend fun getExpensesByCategory(category: String): List<Expense>

    @Query("SELECT category, SUM(amountPaisa) as totalPaisa, COUNT(*) as count FROM expenses WHERE status = 'ACTIVE' GROUP BY category ORDER BY totalPaisa DESC")
    suspend fun getCategorySummaries(): List<CategorySpendSummary>

    @Query("SELECT effectivePlaceId, SUM(amountPaisa) as totalPaisa, COUNT(*) as count FROM expenses WHERE status = 'ACTIVE' GROUP BY effectivePlaceId ORDER BY totalPaisa DESC")
    suspend fun getPlaceSummaries(): List<PlaceSpendSummary>

    @Query("SELECT * FROM expenses WHERE category = 'Miscellaneous' OR category IS NULL OR category = ''")
    suspend fun getMiscellaneousOrUncategorizedExpenses(): List<Expense>

    @Query("SELECT * FROM expenses WHERE (effectivePlaceId IS NULL OR effectivePlaceId = '') AND status = 'ACTIVE'")
    suspend fun getExpensesWithoutEffectivePlace(): List<Expense>

    @Query("UPDATE expenses SET effectivePlaceId = :placeId WHERE id = :id")
    suspend fun updateEffectivePlace(id: String, placeId: String)

    @Query("UPDATE expenses SET categoryId = :categoryId, category = :category, subcategoryId = :subcategoryId, subcategory = :subcategory WHERE id = :id")
    suspend fun updateCategoryDetails(id: String, categoryId: String?, category: String?, subcategoryId: String?, subcategory: String?)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getAllCategoriesList(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Category?

    @Query("SELECT * FROM categories WHERE normalizedName = :norm LIMIT 1")
    suspend fun getByNormalizedName(norm: String): Category?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<Category>)

    @Update
    suspend fun updateCategory(category: Category)
}

@Dao
interface SubcategoryDao {
    @Query("SELECT * FROM subcategories WHERE categoryId = :categoryId ORDER BY name ASC")
    fun getSubcategoriesForCategory(categoryId: String): Flow<List<Subcategory>>

    @Query("SELECT * FROM subcategories ORDER BY name ASC")
    suspend fun getAllSubcategoriesList(): List<Subcategory>

    @Query("SELECT * FROM subcategories WHERE categoryId = :categoryId AND normalizedName = :norm LIMIT 1")
    suspend fun getByCategoryIdAndNormalizedName(categoryId: String, norm: String): Subcategory?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubcategory(subcategory: Subcategory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubcategories(subcategories: List<Subcategory>)

    @Update
    suspend fun updateSubcategory(subcategory: Subcategory)
}

@Dao
interface ChatTurnDao {
    @Query("SELECT * FROM chat_turns ORDER BY createdAt ASC")
    suspend fun getAllTurns(): List<ChatTurn>

    @Query("SELECT * FROM chat_turns WHERE createdAt >= :cutoff ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getRecentTurns(cutoff: Long, limit: Int = 12): List<ChatTurn>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTurn(turn: ChatTurn)

    @Query("DELETE FROM chat_turns WHERE createdAt < :cutoff")
    suspend fun deleteExpiredTurns(cutoff: Long)

    @Query("DELETE FROM chat_turns")
    suspend fun clearAll()
}

@Dao
interface PairedConversationDao {
    @Query("SELECT * FROM paired_conversations WHERE enabled = 1 LIMIT 1")
    fun getPairedConversation(): Flow<PairedConversation?>

    @Query("SELECT * FROM paired_conversations WHERE enabled = 1 LIMIT 1")
    suspend fun getPairedConversationOnce(): PairedConversation?

    @Query("SELECT * FROM paired_conversations WHERE pkg = :pkg AND conversationId = :conversationId LIMIT 1")
    suspend fun getByConversationId(pkg: String, conversationId: String): PairedConversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(conversation: PairedConversation)

    @Query("DELETE FROM paired_conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM paired_conversations")
    suspend fun clearAll()
}

@Dao
interface InboxEventDao {
    @Query("SELECT * FROM inbox_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: String): InboxEvent?

    @Query("SELECT * FROM inbox_events WHERE eventIdentity = :identity LIMIT 1")
    suspend fun getEventByIdentity(identity: String): InboxEvent?

    @Query("SELECT * FROM inbox_events WHERE classificationState IN ('DETECTED', 'PENDING_CLASSIFICATION') ORDER BY orderingSequence ASC")
    suspend fun getPendingEvents(): List<InboxEvent>

    @Query("SELECT * FROM inbox_events WHERE classificationState IN ('NEEDS_REVIEW', 'WAITING_CONFIRMATION') ORDER BY detectedAt DESC")
    fun getReviewEvents(): Flow<List<InboxEvent>>

    @Query("SELECT COUNT(*) FROM inbox_events WHERE classificationState IN ('NEEDS_REVIEW', 'WAITING_CONFIRMATION')")
    fun countPendingReview(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: InboxEvent): Long

    @Update
    suspend fun update(event: InboxEvent)

    @Query("DELETE FROM inbox_events WHERE classificationState = 'IGNORED' AND detectedAt < :cutoff")
    suspend fun deleteIgnoredOlderThan(cutoff: Long)
}

@Dao
interface LocationSnapshotDao {
    @Query("SELECT * FROM location_snapshots WHERE id = :id LIMIT 1")
    suspend fun getSnapshotById(id: String): LocationSnapshot?

    @Query("SELECT * FROM location_snapshots ORDER BY detectedAt DESC LIMIT 1")
    suspend fun getLatestSnapshot(): LocationSnapshot?

    @Query("SELECT * FROM location_snapshots WHERE geocodeState = 'PENDING' AND latitude IS NOT NULL ORDER BY detectedAt ASC")
    suspend fun getPendingGeocodes(): List<LocationSnapshot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: LocationSnapshot)

    @Update
    suspend fun update(snapshot: LocationSnapshot)
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY canonicalName ASC")
    fun getAllPlaces(): Flow<List<Place>>

    @Query("SELECT * FROM places")
    suspend fun getAllPlacesList(): List<Place>

    @Query("SELECT * FROM places WHERE id = :id LIMIT 1")
    suspend fun getPlaceById(id: String): Place?

    @Query("SELECT * FROM places WHERE LOWER(canonicalName) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): Place?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlace(place: Place): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaces(places: List<Place>)
}

@Dao
interface PendingProposalDao {
    @Query("SELECT * FROM pending_proposals WHERE id = :id LIMIT 1")
    suspend fun getProposal(id: String): PendingProposal?

    @Query("SELECT * FROM pending_proposals WHERE sourceEventId = :eventId AND state = 'PENDING' LIMIT 1")
    suspend fun getActiveProposalForEvent(eventId: String): PendingProposal?

    @Query("SELECT * FROM pending_proposals WHERE state = 'PENDING' AND expiresAt > :now ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestActiveProposal(now: Long = System.currentTimeMillis()): PendingProposal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(proposal: PendingProposal)

    @Query("UPDATE pending_proposals SET state = :state WHERE id = :id")
    suspend fun updateState(id: String, state: String)

    @Query("UPDATE pending_proposals SET state = 'EXPIRED' WHERE state = 'PENDING' AND expiresAt <= :now")
    suspend fun expireOlderThan(now: Long = System.currentTimeMillis())
}

@Dao
interface ReplyTaskDao {
    @Query("SELECT * FROM reply_tasks WHERE state = 'PENDING' ORDER BY id ASC")
    suspend fun getPendingReplyTasks(): List<ReplyTask>

    @Query("SELECT * FROM reply_tasks WHERE sourceEventId = :eventId LIMIT 1")
    suspend fun getTaskByEventId(eventId: String): ReplyTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ReplyTask)

    @Update
    suspend fun update(task: ReplyTask)
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(entry: AuditEntry)

    @Query("SELECT * FROM audit_entries ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentAudit(limit: Int = 50): Flow<List<AuditEntry>>
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsOnce(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: AppSettings)
}
