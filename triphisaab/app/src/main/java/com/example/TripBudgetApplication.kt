package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.ai.KeystoreSecretManager
import com.example.data.db.TripBudgetDatabase
import com.example.data.repository.LedgerRepository
import com.example.data.repository.SettingsRepository
import com.example.engine.BudgetProcessingEngine
import com.example.location.LocationTrackingService
import com.example.location.TripLocationProvider
import com.example.notifications.PairingManager

class TripBudgetApplication : Application() {
    lateinit var database: TripBudgetDatabase
        private set
    lateinit var ledgerRepository: LedgerRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var keystoreSecretManager: KeystoreSecretManager
        private set
    lateinit var pairingManager: PairingManager
        private set
    lateinit var locationProvider: TripLocationProvider
        private set
    lateinit var budgetEngine: BudgetProcessingEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = TripBudgetDatabase.getDatabase(this)
        keystoreSecretManager = KeystoreSecretManager()
        settingsRepository = SettingsRepository(database.settingsDao(), database.pairedConversationDao(), keystoreSecretManager)
        ledgerRepository = LedgerRepository(database, settingsRepository)
        locationProvider = TripLocationProvider(this, database.locationSnapshotDao(), database.placeDao())
        pairingManager = PairingManager(this, database.pairedConversationDao(), settingsRepository)
        budgetEngine = BudgetProcessingEngine(this, database, ledgerRepository, settingsRepository, locationProvider)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val trackingChannel = NotificationChannel(
                LocationTrackingService.CHANNEL_ID,
                "Trip Budget Location Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status while trip location tracking is active"
            }

            val statusChannel = NotificationChannel(
                "trip_budget_status",
                "Trip Budget Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Status updates and verification replies"
            }

            notificationManager.createNotificationChannel(trackingChannel)
            notificationManager.createNotificationChannel(statusChannel)
        }
    }

    companion object {
        lateinit var instance: TripBudgetApplication
            private set
    }
}
