package com.safepulse.worker

import android.content.Context
import androidx.work.*
import com.safepulse.SafePulseApplication
import com.safepulse.data.prefs.UserPreferences
import com.safepulse.data.repository.EventLogRepository
import com.safepulse.service.SafetyForegroundService
import com.safepulse.utils.SafetyConstants
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for periodic safety checks and maintenance tasks
 */
class SafetyCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        try {
            val app = applicationContext as SafePulseApplication
            val db = app.database
            
            val userPreferences = UserPreferences(applicationContext)
            val eventLogRepository = EventLogRepository(db.eventLogDao())
            
            // Check if service should be running
            val settings = userPreferences.userSettingsFlow.first()
            if (settings.serviceEnabled) {
                // Ensure service is running
                SafetyForegroundService.start(applicationContext)
            }
            
            // Clean up old event logs (older than 30 days)
            eventLogRepository.deleteOldEvents(30)
            
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
    
    companion object {
        private const val WORK_NAME = "safety_check_periodic"
        
        /**
         * Schedule periodic safety check work
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<SafetyCheckWorker>(
                15, TimeUnit.MINUTES  // Minimum interval for WorkManager
            )
                .setConstraints(constraints)
                .addTag(SafetyConstants.WORK_TAG_SAFETY_CHECK)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
        
        /**
         * Cancel scheduled work
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

/**
 * Worker for refreshing zone data (can be extended for sync with remote)
 */
class DataRefreshWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        // In a full implementation, this would sync with a remote server
        // For the offline-first prototype, we just verify local data integrity
        
        try {
            val app = applicationContext as SafePulseApplication
            
            // Trigger data preload if needed
            app.database.preloadSampleDataIfNeeded(applicationContext)
            
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
    
    companion object {
        private const val WORK_NAME = "data_refresh_periodic"
        
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<DataRefreshWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .addTag(SafetyConstants.WORK_TAG_DATA_REFRESH)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
