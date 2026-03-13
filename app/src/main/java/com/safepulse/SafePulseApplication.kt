package com.safepulse

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.safepulse.data.db.SafePulseDatabase
import com.safepulse.data.prefs.UserPreferences
import com.safepulse.utils.LocaleManager
import com.safepulse.utils.SafetyConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class SafePulseApplication : Application() {
    
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    val database: SafePulseDatabase by lazy {
        SafePulseDatabase.getInstance(this)
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        applySavedLanguage()
        createNotificationChannels()
        preloadDataIfNeeded()
    }

    private fun applySavedLanguage() {
        if (LocaleManager.hasActiveAppLocale()) return
        val userPreferences = UserPreferences(this)
        val languageCode = runBlocking { userPreferences.appLanguageCodeFlow.first() }
        LocaleManager.applyLanguage(languageCode)
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Safety monitoring channel (low priority, persistent)
            val safetyChannel = NotificationChannel(
                SafetyConstants.CHANNEL_ID_SAFETY,
                getString(R.string.notification_channel_safety),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing safety monitoring notification"
                setShowBadge(false)
            }
            
            // Emergency alerts channel (high priority)
            val emergencyChannel = NotificationChannel(
                SafetyConstants.CHANNEL_ID_EMERGENCY,
                getString(R.string.notification_channel_emergency),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency and SOS alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setShowBadge(true)
            }
            
            notificationManager.createNotificationChannel(safetyChannel)
            notificationManager.createNotificationChannel(emergencyChannel)
        }
    }
    
    private fun preloadDataIfNeeded() {
        applicationScope.launch {
            database.preloadSampleDataIfNeeded(this@SafePulseApplication)
        }
    }
    
    companion object {
        lateinit var instance: SafePulseApplication
            private set
    }
}
