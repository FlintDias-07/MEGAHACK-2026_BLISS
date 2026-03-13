package com.safepulse.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safepulse.utils.SafetyConstants

/**
 * Receiver for cancelling SOS from notification action
 */
class SOSCancelReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SafetyConstants.ACTION_CANCEL_SOS) {
            SafetyForegroundService.getInstance()?.cancelEmergencyCountdown()
        }
    }
}

/**
 * Receiver for restarting service after device boot
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if service was enabled before reboot
            // For now, we don't auto-start - user needs to manually start
            // This can be changed based on DataStore preference
        }
    }
}
