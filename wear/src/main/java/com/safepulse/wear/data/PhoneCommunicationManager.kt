package com.safepulse.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Manages communication with the phone app via Wearable Data Layer API.
 * Handles sending messages (SOS, actions) and receiving data items (safety status, contacts).
 */
class PhoneCommunicationManager(private val context: Context) {

    companion object {
        private const val TAG = "PhoneComm"
    }

    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context)

    private val _phoneConnected = MutableStateFlow(false)
    val phoneConnected: StateFlow<Boolean> = _phoneConnected.asStateFlow()

    private val _safetyState = MutableStateFlow(WearSafetyState())
    val safetyState: StateFlow<WearSafetyState> = _safetyState.asStateFlow()

    private val _emergencyContacts = MutableStateFlow<List<WearEmergencyContact>>(emptyList())
    val emergencyContacts: StateFlow<List<WearEmergencyContact>> = _emergencyContacts.asStateFlow()

    /**
     * Check if the phone is reachable.
     */
    suspend fun checkPhoneConnection(): Boolean {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            val connected = nodes.isNotEmpty()
            _phoneConnected.value = connected
            connected
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check phone connection", e)
            _phoneConnected.value = false
            false
        }
    }

    /**
     * Get the best phone node to send messages to.
     */
    private suspend fun getPhoneNodeId(): String? {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            // Prefer nearby nodes (phone is typically nearby)
            nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get phone node", e)
            null
        }
    }

    /**
     * Send a message to the phone app.
     */
    private suspend fun sendMessage(path: String, data: ByteArray = ByteArray(0)): Boolean {
        val nodeId = getPhoneNodeId()
        if (nodeId == null) {
            Log.w(TAG, "No phone node available for message: $path")
            _phoneConnected.value = false
            return false
        }

        return try {
            messageClient.sendMessage(nodeId, path, data).await()
            _phoneConnected.value = true
            Log.d(TAG, "Message sent: $path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: $path", e)
            false
        }
    }

    /**
     * Trigger SOS on the phone. Sends emergency event data.
     */
    suspend fun triggerSOS(eventType: String = "MANUAL_SOS", confidence: Float = 1.0f): Boolean {
        val data = "${eventType}|${confidence}|${System.currentTimeMillis()}".toByteArray()
        return sendMessage(WearDataPaths.PATH_SOS_TRIGGER, data)
    }

    /**
     * Cancel an active SOS.
     */
    suspend fun cancelSOS(): Boolean {
        return sendMessage(WearDataPaths.PATH_SOS_CANCEL)
    }

    /**
     * Trigger silent alert (SMS only, no call/sound).
     */
    suspend fun triggerSilentAlert(): Boolean {
        return sendMessage(WearDataPaths.PATH_SILENT_ALERT)
    }

    /**
     * Request fake call from phone.
     */
    suspend fun triggerFakeCall(): Boolean {
        return sendMessage(WearDataPaths.PATH_FAKE_CALL)
    }

    /**
     * Request location sharing from phone.
     */
    suspend fun shareLocation(): Boolean {
        return sendMessage(WearDataPaths.PATH_SHARE_LOCATION)
    }

    /**
     * Request current safety status from phone.
     */
    suspend fun requestStatusUpdate(): Boolean {
        return sendMessage(WearDataPaths.PATH_REQUEST_STATUS)
    }

    /**
     * Send heart rate data to phone for monitoring.
     */
    suspend fun sendHeartRate(heartRate: Int) {
        try {
            val request = PutDataMapRequest.create(WearDataPaths.PATH_HEART_RATE_DATA).apply {
                dataMap.putInt(WearDataPaths.KEY_HEART_RATE, heartRate)
                dataMap.putLong(WearDataPaths.KEY_TIMESTAMP, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(request).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send heart rate", e)
        }
    }

    /**
     * Process incoming data item changes from phone.
     */
    fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                val dataMap = DataMapItem.fromDataItem(dataItem).dataMap

                when (dataItem.uri.path) {
                    WearDataPaths.PATH_SAFETY_STATUS -> {
                        _safetyState.value = _safetyState.value.copy(
                            riskLevel = WearRiskLevel.fromString(
                                dataMap.getString(WearDataPaths.KEY_RISK_LEVEL, "LOW")
                            ),
                            riskScore = dataMap.getFloat(WearDataPaths.KEY_RISK_SCORE, 0f),
                            safetyMode = WearSafetyMode.fromString(
                                dataMap.getString(WearDataPaths.KEY_SAFETY_MODE, "NORMAL")
                            ),
                            isEmergency = dataMap.getBoolean(WearDataPaths.KEY_IS_EMERGENCY, false),
                            isPhoneServiceRunning = dataMap.getBoolean(WearDataPaths.KEY_SERVICE_RUNNING, false),
                            isPhoneConnected = true,
                            lastUpdateTimestamp = dataMap.getLong(WearDataPaths.KEY_TIMESTAMP, 0L)
                        )
                    }

                    WearDataPaths.PATH_LOCATION_DATA -> {
                        _safetyState.value = _safetyState.value.copy(
                            latitude = dataMap.getDouble(WearDataPaths.KEY_LATITUDE, 0.0),
                            longitude = dataMap.getDouble(WearDataPaths.KEY_LONGITUDE, 0.0)
                        )
                    }

                    WearDataPaths.PATH_EMERGENCY_CONTACTS -> {
                        val json = dataMap.getString(WearDataPaths.KEY_CONTACTS_JSON, "[]")
                        try {
                            val contacts = parseContactsJson(json)
                            _emergencyContacts.value = contacts
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse contacts", e)
                        }
                    }
                }
            }
        }
    }

    /**
     * Process incoming message from phone.
     */
    fun onMessageReceived(path: String, data: ByteArray) {
        when (path) {
            WearDataPaths.PATH_SOS_CONFIRM -> {
                _safetyState.value = _safetyState.value.copy(isEmergency = true)
            }
            WearDataPaths.PATH_SOS_CANCEL -> {
                _safetyState.value = _safetyState.value.copy(isEmergency = false)
            }
        }
    }

    private fun parseContactsJson(json: String): List<WearEmergencyContact> {
        return try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<WearEmergencyContact>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
