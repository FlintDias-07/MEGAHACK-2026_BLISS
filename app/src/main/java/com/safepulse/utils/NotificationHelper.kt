package com.safepulse.utils

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.safepulse.MainActivity
import com.safepulse.R
import com.safepulse.domain.model.EventType
import com.safepulse.domain.model.RiskLevel
import com.safepulse.service.SOSCancelReceiver

/**
 * Helper for creating and managing notifications
 */
object NotificationHelper {
    
    /**
     * Create foreground service notification
     */
    fun createForegroundNotification(context: Context): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, SafetyConstants.CHANNEL_ID_SAFETY)
            .setContentTitle(context.getString(R.string.notification_title_monitoring))
            .setContentText(context.getString(R.string.notification_text_monitoring))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    /**
     * Show emergency countdown notification
     */
    fun showEmergencyCountdownNotification(
        context: Context,
        eventType: EventType,
        secondsRemaining: Int
    ) {
        val cancelIntent = Intent(context, SOSCancelReceiver::class.java).apply {
            action = SafetyConstants.ACTION_CANCEL_SOS
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val openIntent = Intent(context, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            context, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val eventText = when (eventType) {
            EventType.ROAD_ACCIDENT -> "Accident detected"
            EventType.FALL -> "Fall detected"
            EventType.POSSIBLE_ASSAULT -> "Possible assault detected"
            EventType.INACTIVITY_ALERT -> "Inactivity detected"
            EventType.VOICE_TRIGGER -> "Voice emergency detected"
            EventType.MANUAL_SOS -> "Manual SOS triggered"
            EventType.HIGH_RISK_ZONE -> "High risk zone alert"
        }
        
        val notification = NotificationCompat.Builder(context, SafetyConstants.CHANNEL_ID_EMERGENCY)
            .setContentTitle("⚠️ $eventText")
            .setContentText("Sending SOS in $secondsRemaining seconds. Tap to cancel.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.action_cancel),
                cancelPendingIntent
            )
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
            as NotificationManager
        notificationManager.notify(SafetyConstants.NOTIFICATION_ID_EMERGENCY, notification)
    }
    
    /**
     * Cancel emergency notification
     */
    fun cancelEmergencyNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
            as NotificationManager
        notificationManager.cancel(SafetyConstants.NOTIFICATION_ID_EMERGENCY)
    }
    
    /**
     * Show high risk zone alert
     */
    fun showRiskAlertNotification(context: Context, riskLevel: RiskLevel) {
        if (riskLevel != RiskLevel.HIGH) return
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, SafetyConstants.CHANNEL_ID_SAFETY)
            .setContentTitle(context.getString(R.string.notification_title_high_risk))
            .setContentText(context.getString(R.string.notification_text_high_risk))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
            as NotificationManager
        notificationManager.notify(SafetyConstants.NOTIFICATION_ID_RISK_ALERT, notification)
    }
    
    /**
     * Cancel risk alert notification
     */
    fun cancelRiskAlertNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
            as NotificationManager
        notificationManager.cancel(SafetyConstants.NOTIFICATION_ID_RISK_ALERT)
    }
}
