package com.safepulse.service

import android.util.Log
import com.google.android.gms.wearable.*
import com.safepulse.SafePulseApplication
import com.safepulse.data.repository.EmergencyContactRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

/**
 * Wearable listener service on the PHONE side.
 * Receives messages from the watch (SOS triggers, action requests)
 * and forwards them to the appropriate phone-side services.
 */
class PhoneWearListenerService : com.google.android.gms.wearable.WearableListenerService() {

    companion object {
        private const val TAG = "WearListener"

        // Message paths (must match wear module WearDataPaths)
        const val PATH_SOS_TRIGGER = "/safepulse/sos/trigger"
        const val PATH_SOS_CANCEL = "/safepulse/sos/cancel"
        const val PATH_SILENT_ALERT = "/safepulse/sos/silent"
        const val PATH_FAKE_CALL = "/safepulse/action/fake_call"
        const val PATH_SHARE_LOCATION = "/safepulse/action/share_location"
        const val PATH_REQUEST_STATUS = "/safepulse/status/request"
        const val PATH_PING = "/safepulse/ping"

        // Data paths for syncing TO watch
        const val PATH_SAFETY_STATUS = "/safepulse/status/safety"
        const val PATH_EMERGENCY_CONTACTS = "/safepulse/data/contacts"
        const val PATH_LOCATION_DATA = "/safepulse/data/location"
        const val PATH_HEART_RATE_DATA = "/safepulse/data/heart_rate"

        // Data keys
        const val KEY_RISK_LEVEL = "risk_level"
        const val KEY_RISK_SCORE = "risk_score"
        const val KEY_SAFETY_MODE = "safety_mode"
        const val KEY_IS_EMERGENCY = "is_emergency"
        const val KEY_SERVICE_RUNNING = "service_running"
        const val KEY_CONTACTS_JSON = "contacts_json"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_HEART_RATE = "heart_rate"
        const val KEY_TIMESTAMP = "timestamp"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        Log.d(TAG, "Message from watch: ${messageEvent.path}")

        when (messageEvent.path) {
            PATH_SOS_TRIGGER -> handleSOSTrigger(messageEvent.data)
            PATH_SOS_CANCEL -> handleSOSCancel()
            PATH_SILENT_ALERT -> handleSilentAlert()
            PATH_FAKE_CALL -> handleFakeCall()
            PATH_SHARE_LOCATION -> handleShareLocation()
            PATH_REQUEST_STATUS -> handleStatusRequest(messageEvent.sourceNodeId)
            PATH_PING -> handlePing(messageEvent.sourceNodeId)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)

        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                when (event.dataItem.uri.path) {
                    PATH_HEART_RATE_DATA -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val hr = dataMap.getInt(KEY_HEART_RATE, 0)
                        Log.d(TAG, "Heart rate from watch: $hr BPM")
                        // Could integrate this into SafetyEngine for distress detection
                    }
                }
            }
        }
    }

    private fun handleSOSTrigger(data: ByteArray) {
        Log.w(TAG, "🚨 SOS triggered from watch!")

        // Start the service if not running
        val service = SafetyForegroundService.getInstance()
        if (service != null) {
            service.triggerManualSOS()
        } else {
            SafetyForegroundService.start(this)
            scope.launch {
                delay(1500) // Wait for service to initialize
                SafetyForegroundService.getInstance()?.triggerManualSOS()
            }
        }
    }

    private fun handleSOSCancel() {
        Log.d(TAG, "SOS cancelled from watch")
        SafetyForegroundService.getInstance()?.cancelEmergencyCountdown()
    }

    private fun handleSilentAlert() {
        Log.d(TAG, "Silent alert from watch")
        val service = SafetyForegroundService.getInstance()
        if (service != null) {
            service.triggerSilentSOS()
        } else {
            SafetyForegroundService.start(this)
            scope.launch {
                delay(1500)
                SafetyForegroundService.getInstance()?.triggerSilentSOS()
            }
        }
    }

    private fun handleFakeCall() {
        Log.d(TAG, "Fake call requested from watch")
        // Trigger fake call notification on phone
        scope.launch {
            try {
                val notificationManager =
                    getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel(
                        "fake_call_channel",
                        "Fake Call",
                        android.app.NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Fake incoming call notifications"
                        setSound(
                            android.provider.Settings.System.DEFAULT_RINGTONE_URI,
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .build()
                        )
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val notification =
                    androidx.core.app.NotificationCompat.Builder(this@PhoneWearListenerService, "fake_call_channel")
                        .setSmallIcon(android.R.drawable.sym_call_incoming)
                        .setContentTitle("Incoming call...")
                        .setContentText("Mom")
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                        .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
                        .setFullScreenIntent(null, true)
                        .setAutoCancel(true)
                        .build()

                notificationManager.notify(9999, notification)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating fake call", e)
            }
        }
    }

    private fun handleShareLocation() {
        Log.d(TAG, "Location share requested from watch")
        // Share location using the phone's share intent
        // This requires an activity context, so we broadcast an intent
        val intent = android.content.Intent("com.safepulse.ACTION_SHARE_LOCATION").apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun handleStatusRequest(nodeId: String) {
        Log.d(TAG, "Status request from watch node: $nodeId")
        scope.launch {
            sendSafetyStatusToWatch()
            sendEmergencyContactsToWatch()
        }
    }

    private fun handlePing(nodeId: String) {
        Log.d(TAG, "Ping from watch, responding...")
        scope.launch {
            try {
                Wearable.getMessageClient(this@PhoneWearListenerService)
                    .sendMessage(nodeId, PATH_PING, "pong".toByteArray())
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to respond to ping", e)
            }
        }
    }

    /**
     * Send current safety status to watch via Data Layer.
     */
    private suspend fun sendSafetyStatusToWatch() {
        try {
            val service = SafetyForegroundService.getInstance()
            val riskLevel = "LOW"  // Default; in real integration, read from SafetyEngine state
            val serviceRunning = service != null

            val request = PutDataMapRequest.create(PATH_SAFETY_STATUS).apply {
                dataMap.putString(KEY_RISK_LEVEL, riskLevel)
                dataMap.putFloat(KEY_RISK_SCORE, 0f)
                dataMap.putString(KEY_SAFETY_MODE, "NORMAL")
                dataMap.putBoolean(KEY_IS_EMERGENCY, false)
                dataMap.putBoolean(KEY_SERVICE_RUNNING, serviceRunning)
                dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(this).putDataItem(request).await()
            Log.d(TAG, "Safety status sent to watch")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send safety status", e)
        }
    }

    /**
     * Send emergency contacts to watch via Data Layer.
     */
    private suspend fun sendEmergencyContactsToWatch() {
        try {
            val app = application as SafePulseApplication
            val contactRepo = EmergencyContactRepository(app.database.emergencyContactDao())
            val contacts = contactRepo.getAllContactsList()

            val contactsJson = com.google.gson.Gson().toJson(
                contacts.map {
                    mapOf(
                        "name" to it.name,
                        "phone" to it.phone,
                        "relationship" to if (it.isPrimary) "Primary" else ""
                    )
                }
            )

            val request = PutDataMapRequest.create(PATH_EMERGENCY_CONTACTS).apply {
                dataMap.putString(KEY_CONTACTS_JSON, contactsJson)
                dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(this).putDataItem(request).await()
            Log.d(TAG, "Emergency contacts sent to watch (${contacts.size} contacts)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send contacts", e)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
