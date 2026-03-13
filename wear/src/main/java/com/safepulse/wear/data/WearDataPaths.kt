package com.safepulse.wear.data

/**
 * Constants for Wearable Data Layer API paths used for communication
 * between the phone and watch apps.
 */
object WearDataPaths {

    // Message paths (phone <-> watch)
    const val PATH_SOS_TRIGGER = "/safepulse/sos/trigger"
    const val PATH_SOS_CANCEL = "/safepulse/sos/cancel"
    const val PATH_SOS_CONFIRM = "/safepulse/sos/confirm"
    const val PATH_SILENT_ALERT = "/safepulse/sos/silent"
    const val PATH_FAKE_CALL = "/safepulse/action/fake_call"
    const val PATH_SHARE_LOCATION = "/safepulse/action/share_location"
    const val PATH_REQUEST_STATUS = "/safepulse/status/request"
    const val PATH_PING = "/safepulse/ping"

    // Data item paths (synced state)
    const val PATH_SAFETY_STATUS = "/safepulse/status/safety"
    const val PATH_EMERGENCY_CONTACTS = "/safepulse/data/contacts"
    const val PATH_LOCATION_DATA = "/safepulse/data/location"
    const val PATH_HEART_RATE_DATA = "/safepulse/data/heart_rate"

    // Data item keys
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
    const val KEY_EVENT_TYPE = "event_type"
    const val KEY_CONFIDENCE = "confidence"
}
