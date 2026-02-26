package com.safepulse.wear.data

/**
 * Models used in the Wear OS companion app.
 * Simplified versions of the phone app models for watch-appropriate display.
 */

enum class WearRiskLevel {
    LOW, MEDIUM, HIGH;

    companion object {
        fun fromString(value: String): WearRiskLevel {
            return try { valueOf(value.uppercase()) } catch (e: Exception) { LOW }
        }
    }
}

enum class WearSafetyMode {
    NORMAL, HEIGHTENED;

    companion object {
        fun fromString(value: String): WearSafetyMode {
            return try { valueOf(value.uppercase()) } catch (e: Exception) { NORMAL }
        }
    }
}

data class WearSafetyState(
    val riskLevel: WearRiskLevel = WearRiskLevel.LOW,
    val riskScore: Float = 0f,
    val safetyMode: WearSafetyMode = WearSafetyMode.NORMAL,
    val isEmergency: Boolean = false,
    val isPhoneServiceRunning: Boolean = false,
    val isPhoneConnected: Boolean = false,
    val heartRate: Int = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val lastUpdateTimestamp: Long = 0L
)

data class WearEmergencyContact(
    val name: String,
    val phone: String,
    val relationship: String = ""
)
