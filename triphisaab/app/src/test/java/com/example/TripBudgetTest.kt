package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.ClassifierExpenseItem
import com.example.ai.LocalFallbackParser
import com.example.data.db.TripBudgetDatabase
import com.example.data.model.Budget
import com.example.data.model.Expense
import com.example.data.model.Place
import com.example.data.repository.LedgerRepository
import com.example.data.repository.SettingsRepository
import com.example.engine.DeterministicReplyRenderer
import com.example.engine.MoneyFormatter
import com.example.notifications.PairingManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class TripBudgetTest {

    private lateinit var context: Context
    private lateinit var db: TripBudgetDatabase
    private lateinit var ledgerRepository: LedgerRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var pairingManager: PairingManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TripBudgetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            TripBudgetDatabase.seedInitialData(db)
        }
        val secretManager = com.example.ai.KeystoreSecretManager()
        settingsRepository = SettingsRepository(db.settingsDao(), db.pairedConversationDao(), secretManager)
        ledgerRepository = LedgerRepository(db, settingsRepository)
        pairingManager = PairingManager(context, db.pairedConversationDao(), settingsRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ==========================================
    // 1. MONEY ACCURACY TESTS (NO FLOATS)
    // ==========================================
    @Test
    fun testMoneyFormatter_ExactPaisaParsing() {
        assertEquals(2200_00L, MoneyFormatter.parseToPaisa("2200"))
        assertEquals(2200_50L, MoneyFormatter.parseToPaisa("2200.50"))
        assertEquals(2200_00L, MoneyFormatter.parseToPaisa("2,200"))
        assertEquals(180_00L, MoneyFormatter.parseToPaisa("Rs. 180"))
        assertEquals(50000_00L, MoneyFormatter.parseToPaisa("PKR 50000.00"))
    }

    @Test
    fun testMoneyFormatter_RejectsInvalidAndNegative() {
        try {
            MoneyFormatter.parseToPaisa("-500")
            fail("Expected IllegalArgumentException for negative amount")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        try {
            MoneyFormatter.parseToPaisa("0")
            fail("Expected IllegalArgumentException for zero amount")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        try {
            MoneyFormatter.parseToPaisa("invalid")
            fail("Expected IllegalArgumentException for invalid text")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun testMoneyFormatter_Formatting() {
        assertEquals("Rs. 2,200", MoneyFormatter.formatPaisa(2200_00L))
        assertEquals("Rs. 2,200.50", MoneyFormatter.formatPaisa(2200_50L))
        assertEquals("-Rs. 500", MoneyFormatter.formatPaisa(-500_00L))
    }

    // ==========================================
    // 2. DETERMINISTIC CLASSIFIER & PARSER TESTS
    // ==========================================
    @Test
    fun testLocalFallbackParser_ValidPurchases() {
        // Acceptance Test 1: Single clear purchase
        val res1 = LocalFallbackParser.parse("petrol bharwaya 2200 ka")
        assertEquals("ADD_EXPENSE", res1.action)
        assertEquals("clear", res1.certainty)
        assertEquals(1, res1.expenses?.size)
        assertEquals("2200", res1.expenses?.first()?.amountDecimal)

        // Acceptance Test 6: Simple purchase
        val res2 = LocalFallbackParser.parse("helmet 3500 ka liya")
        assertEquals("ADD_EXPENSE", res2.action)
        assertEquals("3500", res2.expenses?.first()?.amountDecimal)

        // Multi-expense purchase
        val res3 = LocalFallbackParser.parse("petrol pe 2200 aur chai pe 180 lagay")
        assertEquals("ADD_EXPENSE", res3.action)
        assertEquals(2, res3.expenses?.size)
    }

    @Test
    fun testLocalFallbackParser_NonExpensesIgnored() {
        // Acceptance Test 7: Price quote should be IGNORED
        val res1 = LocalFallbackParser.parse("helmet 3500 ka mil raha hai")
        assertEquals("IGNORE", res1.action)

        // Acceptance Test 8: Future reminder should be IGNORED
        val res2 = LocalFallbackParser.parse("kal petrol ke liye 2000 rakhna")
        assertEquals("IGNORE", res2.action)

        // URL / link should be IGNORED
        val res4 = LocalFallbackParser.parse("Check this road condition: https://maps.app.goo.gl/xyz")
        assertEquals("IGNORE", res4.action)
    }

    @Test
    fun testUnifiedAgent_ChatTokenActivationGate() {
        val agent = com.example.engine.UnifiedBudgetAgent(
            context, db, ledgerRepository, settingsRepository,
            com.example.location.TripLocationProvider(context, db.locationSnapshotDao(), db.placeDao())
        )

        // Valid activations
        assertTrue(agent.isChatTokenActivated("@chat petrol 2200"))
        assertTrue(agent.isChatTokenActivated("@CHAT petrol 2200"))
        assertTrue(agent.isChatTokenActivated("  @chat   petroll - 2,200  "))
        assertTrue(agent.isChatTokenActivated("@chat Islamabad ka total kharcha"))
        assertTrue(agent.isChatTokenActivated("@chat bhai ne 5000 bhej diye"))
        assertTrue(agent.isChatTokenActivated("@chat"))

        // Unprefixed or invalid - MUST BE REJECTED
        assertFalse(agent.isChatTokenActivated("petrol 2200"))
        assertFalse(agent.isChatTokenActivated("Islamabad ka total kharcha"))
        assertFalse(agent.isChatTokenActivated("bhai ne 5000 bhej diye"))
        assertFalse(agent.isChatTokenActivated("hello @chat petrol 2200"))
        assertFalse(agent.isChatTokenActivated("chat petrol 2200"))
    }

    @Test
    fun testLocalFallbackParser_CashFlowTransactions() {
        // Income: "bhai ne 5000 bhej diye"
        val incRes = LocalFallbackParser.parse("bhai ne 5000 bhej diye")
        assertEquals("RECORD_TRANSACTION", incRes.action)
        val incItem = incRes.expenses?.first()
        assertNotNull(incItem)
        assertEquals("INCOME", incItem!!.type)
        assertEquals("INCOMING", incItem.direction)
        assertEquals("5000", incItem.amountDecimal)

        // Loan Given: "Qaisar ko udhaar 5000 diya"
        val loanRes = LocalFallbackParser.parse("Qaisar ko udhaar 5000 diya")
        assertEquals("RECORD_TRANSACTION", loanRes.action)
        val loanItem = loanRes.expenses?.first()
        assertNotNull(loanItem)
        assertEquals("LOAN_GIVEN", loanItem!!.type)
        assertEquals("OUTGOING", loanItem.direction)
        assertEquals("5000", loanItem.amountDecimal)
        assertEquals("Qaisar", loanItem.counterparty)

        // Loan Repayment Received: "Qaisar ne udhaar wapas kiya 5000"
        val repayRes = LocalFallbackParser.parse("Qaisar ne udhaar wapas kiya 5000")
        assertEquals("RECORD_TRANSACTION", repayRes.action)
        val repayItem = repayRes.expenses?.first()
        assertNotNull(repayItem)
        assertEquals("LOAN_REPAYMENT_RECEIVED", repayItem!!.type)
        assertEquals("INCOMING", repayItem.direction)
        assertEquals("5000", repayItem.amountDecimal)
        assertEquals("Qaisar", repayItem.counterparty)

        // Refund: "hotel refund 1500 mila"
        val refRes = LocalFallbackParser.parse("hotel refund 1500 mila")
        assertEquals("RECORD_TRANSACTION", refRes.action)
        val refItem = refRes.expenses?.first()
        assertNotNull(refItem)
        assertEquals("REFUND_RECEIVED", refItem!!.type)
        assertEquals("INCOMING", refItem.direction)
        assertEquals("1500", refItem.amountDecimal)
    }

    @Test
    fun testLocalFallbackParser_QueriesAndCommands() {
        val q1 = LocalFallbackParser.parse("Gilgit mein total kharcha ktna hua")
        assertEquals("QUERY_BUDGET", q1.action)
        assertEquals("place", q1.query?.scope)
        assertEquals("Gilgit", q1.query?.placeText)

        val q2 = LocalFallbackParser.parse("total")
        assertEquals("QUERY_BUDGET", q2.action)
        assertEquals("overall", q2.query?.scope)

        val q3 = LocalFallbackParser.parse("Islamabad ka total kharcha")
        assertEquals("QUERY_BUDGET", q3.action)
        assertEquals("place", q3.query?.scope)
        assertEquals("Islamabad", q3.query?.placeText)

        val u = LocalFallbackParser.parse("undo")
        assertEquals("UNDO_EXPENSE", u.action)

        val b = LocalFallbackParser.parse("set total budget to 50,000")
        assertEquals("SET_BUDGET", b.action)
        assertEquals("50000", b.budgetDecimal)
    }

    // ==========================================
    // 3. LEDGER & LOCALITY ISOLATION TESTS
    // ==========================================
    @Test
    fun testLedger_RecordAndUndo() = runBlocking {
        val initialBudget = db.budgetDao().getActiveBudgetOnce()
        assertNotNull(initialBudget)

        // Record single expense
        val exp1 = Expense(
            budgetId = initialBudget!!.id,
            amountPaisa = 2200_00L,
            description = "Petrol"
        )
        val commitRes = ledgerRepository.recordExpenses(listOf(exp1))
        assertEquals(2200_00L, commitRes.overallTotalSpentPaisa)
        assertEquals(2200_00L, commitRes.batchTotalPaisa)

        // Undo
        val undoRes = ledgerRepository.undoLastExpense()
        assertNotNull(undoRes)
        assertEquals(2200_00L, undoRes!!.reversedTotalPaisa)
        assertEquals(0L, undoRes.newOverallTotalSpentPaisa)
    }

    @Test
    fun testLedger_CashFlowMathAndBudgetReplacement() = runBlocking {
        // Base budget replaced to 50,000
        ledgerRepository.setBudgetLimit(50000_00L)
        val budget = db.budgetDao().getActiveBudgetOnce()
        assertNotNull(budget)
        assertEquals(50000_00L, budget!!.limitPaisa)

        // Record Income: +5,000
        val incomeTx = com.example.data.model.Transaction(
            budgetId = budget.id,
            type = "INCOME",
            direction = "INCOMING",
            amountPaisa = 5000_00L,
            description = "Bhai se transfer",
            counterparty = "Bhai"
        )
        ledgerRepository.recordTransactions(listOf(incomeTx))

        // Record Expense: -2,200
        val expenseTx = com.example.data.model.Transaction(
            budgetId = budget.id,
            type = "EXPENSE",
            direction = "OUTGOING",
            amountPaisa = 2200_00L,
            description = "Petrol",
            category = "Transport",
            subcategory = "Fuel"
        )
        ledgerRepository.recordTransactions(listOf(expenseTx))

        // Check balance state: Available = Base (50,000) + Added (5,000) - CashOut (2,200) = 52,800
        val bal = ledgerRepository.getBalanceState()
        assertEquals(50000_00L, bal.baseBudgetPaisa)
        assertEquals(5000_00L, bal.addedFundsPaisa)
        assertEquals(2200_00L, bal.cashOutPaisa)
        assertEquals(2200_00L, bal.expenseSpendingPaisa)
        assertEquals(52800_00L, bal.availableFundsPaisa)
    }

    @Test
    fun testLedger_PlaceQueryAccurateAndEmpty() = runBlocking {
        // Islamabad initially has no transactions in our clean seed
        val isbQuery = ledgerRepository.queryByPlace("Islamabad")
        assertNotNull(isbQuery)
        assertEquals("Islamabad", isbQuery!!.canonicalPlaceName)
        assertEquals(0L, isbQuery.totalSpentPaisa)
        assertEquals(0, isbQuery.count)

        // Reply renderer should return clean message
        val reply = DeterministicReplyRenderer.renderPlaceQuery(isbQuery)
        assertEquals("Islamabad mein abhi koi recorded expense nahi hai.", reply)
    }

    @Test
    fun testLedger_GilgitLocalityVsDistrictQueryIsolation() = runBlocking {
        val cityPlace = ledgerRepository.resolvePlace("Gilgit")
        assertNotNull(cityPlace)
        assertEquals("LOCALITY", cityPlace!!.type)
        assertEquals("Gilgit", cityPlace.canonicalName)

        val distPlace = ledgerRepository.resolvePlace("Gilgit District")
        assertNotNull(distPlace)
        assertEquals("DISTRICT", distPlace!!.type)
        assertEquals("Gilgit District", distPlace.canonicalName)

        assertNotEquals(cityPlace.id, distPlace.id)

        // Log expense in City
        val budget = db.budgetDao().getActiveBudgetOnce()!!
        val cityExp = Expense(
            budgetId = budget.id,
            amountPaisa = 2200_00L,
            description = "Petrol City",
            effectivePlaceId = cityPlace.id
        )
        ledgerRepository.recordExpenses(listOf(cityExp))

        // Log expense in District
        val distExp = Expense(
            budgetId = budget.id,
            amountPaisa = 1500_00L,
            description = "Highway Toll",
            effectivePlaceId = distPlace.id
        )
        ledgerRepository.recordExpenses(listOf(distExp))

        // Query City: Should return only 2200, count = 1
        val cityQuery = ledgerRepository.queryByPlace("Gilgit")
        assertNotNull(cityQuery)
        assertEquals(2200_00L, cityQuery!!.totalSpentPaisa)
        assertEquals(1, cityQuery.count)

        // Query District: Should return only 1500, count = 1
        val distQuery = ledgerRepository.queryByPlace("Gilgit District")
        assertNotNull(distQuery)
        assertEquals(1500_00L, distQuery!!.totalSpentPaisa)
        assertEquals(1, distQuery.count)
    }

    // ==========================================
    // 4. M0 CONNECTION SPIKE & PAIRING TESTS
    // ==========================================
    @Test
    fun testPairingManager_GeneratesExpiringCodeAndValidatesCandidate() = runBlocking {
        val code = pairingManager.generateNewPairingCode()
        assertEquals(6, code.length)
        assertTrue(pairingManager.isPairingActive())

        // Non-matching text should fail
        val matchedWrong = pairingManager.checkCandidateForPairing(
            pkg = "com.whatsapp.w4b",
            text = "Hello customer",
            rawKey = "k1",
            conversationTitle = "Customer",
            senderName = "Customer",
            shortcutId = "sc1",
            senderPersonKey = "p1",
            subText = null,
            isGroup = false
        )
        assertFalse(matchedWrong)

        // Group chat containing code should be REJECTED per PRD rules
        val matchedGroup = pairingManager.checkCandidateForPairing(
            pkg = "com.whatsapp.w4b",
            text = "PAIR $code",
            rawKey = "k2",
            conversationTitle = "Bikers Group",
            senderName = "Ali",
            shortcutId = "sc_group",
            senderPersonKey = "p_group",
            subText = null,
            isGroup = true
        )
        assertFalse(matchedGroup)

        // Direct WhatsApp Business notification matching code
        val matchedDirect = pairingManager.checkCandidateForPairing(
            pkg = "com.whatsapp.w4b",
            text = "PAIR $code",
            rawKey = "k3",
            conversationTitle = "Personal (Self)",
            senderName = "Personal",
            shortcutId = "shortcut_personal_99",
            senderPersonKey = "person_key_99",
            subText = null,
            isGroup = false
        )
        assertTrue(matchedDirect)

        val meta = pairingManager.discoveredMetadata.value
        assertNotNull(meta)
        assertTrue(meta!!.hasIsolatingKey)
        assertEquals("shortcut_personal_99", meta.shortcutId)

        // Approve Pairing
        val approved = pairingManager.approvePairing()
        assertTrue(approved)

        // Verified in DB
        val paired = db.pairedConversationDao().getPairedConversationOnce()
        assertNotNull(paired)
        assertEquals("shortcut_personal_99", paired!!.conversationId)
        assertTrue(paired.enabled)
    }

    // ==========================================
    // 5. TAXONOMY & DATE PARSER TESTS
    // ==========================================
    @Test
    fun testTripDateParser_RelativeAndRetrospective() {
        val now = System.currentTimeMillis()

        // 1. "kal 2200 ka petrol"
        val kalRes = com.example.engine.TripDateParser.parseExpenseDateTime("kal 2200 ka petrol", now)
        assertTrue(kalRes.isRetrospective)
        assertEquals("DATE_ONLY", kalRes.timeCertainty)
        assertEquals("PARSED_RETROSPECTIVE", kalRes.timeSource)
        assertTrue(kalRes.epochMillis < now)

        // 2. "kal sham 5 pm petrol"
        val kalShamRes = com.example.engine.TripDateParser.parseExpenseDateTime("kal sham 5 pm petrol", now)
        assertTrue(kalShamRes.isRetrospective)
        assertEquals("EXACT", kalShamRes.timeCertainty)

        // 3. Regular immediate purchase
        val immediateRes = com.example.engine.TripDateParser.parseExpenseDateTime("petrol 2200", now)
        assertFalse(immediateRes.isRetrospective)
        assertEquals("EXACT", immediateRes.timeCertainty)
        assertEquals("MESSAGE_TIMESTAMP", immediateRes.timeSource)
    }

    @Test
    fun testCategoryTaxonomyManager_Classification() = runBlocking {
        val taxonomyManager = com.example.data.repository.CategoryTaxonomyManager(db.categoryDao(), db.subcategoryDao())

        val fuelMatch = taxonomyManager.resolve("petrol bharwaya 2200 ka")
        assertEquals("Transport", fuelMatch.categoryName)
        assertEquals("Fuel", fuelMatch.subcategoryName)

        val teaMatch = taxonomyManager.resolve("chai 180")
        assertEquals("Food & Drinks", teaMatch.categoryName)
        assertEquals("Tea / Coffee", teaMatch.subcategoryName)

        val hotelMatch = taxonomyManager.resolve("hotel stay Chilas 5000")
        assertEquals("Accommodation", hotelMatch.categoryName)
    }

    @Test
    fun testLocalFallbackParser_ExtendedFormats() {
        // Petrol - 2200
        val resHyphen = LocalFallbackParser.parse("Petrol - 2200")
        assertEquals("ADD_EXPENSE", resHyphen.action)
        assertEquals("2200", resHyphen.expenses?.first()?.amountDecimal)

        // set the total budget to 80000
        val resBudget = LocalFallbackParser.parse("set the total budget to 80000")
        assertEquals("SET_BUDGET", resBudget.action)
        assertEquals("80000", resBudget.budgetDecimal)

        // kal 1200 ka lunch kiya
        val resLunch = LocalFallbackParser.parse("kal 1200 ka lunch kiya")
        assertEquals("ADD_EXPENSE", resLunch.action)
        assertEquals("1200", resLunch.expenses?.first()?.amountDecimal)
    }
}
