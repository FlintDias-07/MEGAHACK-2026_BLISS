package com.safepulse.wear.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safepulse.wear.data.*
import com.safepulse.wear.service.WearDataListenerService
import com.safepulse.wear.service.WearSafetyService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * UI state for the watch home screen.
 */
data class WearHomeState(
    val safetyState: WearSafetyState = WearSafetyState(),
    val emergencyContacts: List<WearEmergencyContact> = emptyList(),
    val isServiceRunning: Boolean = false,
    val isSosActive: Boolean = false,
    val sosCountdown: Int = -1, // -1 means not counting
    val heartRate: Int = 0,
    val isPhoneConnected: Boolean = false
)

class WearHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = WearPreferences(application)
    private val communicationManager: PhoneCommunicationManager =
        WearDataListenerService.communicationManager ?: PhoneCommunicationManager(application)

    private val _state = MutableStateFlow(WearHomeState())
    val state: StateFlow<WearHomeState> = _state.asStateFlow()

    init {
        observePhoneData()
        observePreferences()
        checkConnection()
    }

    private fun observePhoneData() {
        viewModelScope.launch {
            communicationManager.safetyState.collect { safety ->
                _state.value = _state.value.copy(
                    safetyState = safety,
                    heartRate = safety.heartRate,
                    isPhoneConnected = safety.isPhoneConnected
                )
            }
        }

        viewModelScope.launch {
            communicationManager.emergencyContacts.collect { contacts ->
                _state.value = _state.value.copy(emergencyContacts = contacts)
            }
        }

        viewModelScope.launch {
            communicationManager.phoneConnected.collect { connected ->
                _state.value = _state.value.copy(isPhoneConnected = connected)
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferences.serviceEnabledFlow.collect { enabled ->
                _state.value = _state.value.copy(isServiceRunning = enabled)
            }
        }
    }

    private fun checkConnection() {
        viewModelScope.launch {
            communicationManager.checkPhoneConnection()
        }
    }

    /**
     * Toggle the watch safety monitoring service.
     */
    fun toggleService() {
        viewModelScope.launch {
            val newState = !_state.value.isServiceRunning
            if (newState) {
                WearSafetyService.start(getApplication())
            } else {
                WearSafetyService.stop(getApplication())
            }
            preferences.setServiceEnabled(newState)
        }
    }

    /**
     * Trigger manual SOS with countdown.
     */
    fun triggerSOS() {
        if (_state.value.isSosActive) return

        viewModelScope.launch {
            val countdownSeconds = preferences.sosCountdownSecondsFlow.first()
            _state.value = _state.value.copy(isSosActive = true, sosCountdown = countdownSeconds)

            // Countdown
            for (i in countdownSeconds downTo 1) {
                _state.value = _state.value.copy(sosCountdown = i)
                delay(1000)

                // Check if cancelled
                if (!_state.value.isSosActive) return@launch
            }

            // Execute SOS
            _state.value = _state.value.copy(sosCountdown = 0)
            communicationManager.triggerSOS()

            // Reset after a delay
            delay(3000)
            _state.value = _state.value.copy(isSosActive = false, sosCountdown = -1)
        }
    }

    /**
     * Cancel an active SOS countdown.
     */
    fun cancelSOS() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSosActive = false, sosCountdown = -1)
            communicationManager.cancelSOS()
        }
    }

    /**
     * Send silent alert (SMS only).
     */
    fun triggerSilentAlert() {
        viewModelScope.launch {
            communicationManager.triggerSilentAlert()
        }
    }

    /**
     * Trigger fake call via phone.
     */
    fun triggerFakeCall() {
        viewModelScope.launch {
            communicationManager.triggerFakeCall()
        }
    }

    /**
     * Share current location via phone.
     */
    fun shareLocation() {
        viewModelScope.launch {
            communicationManager.shareLocation()
        }
    }

    /**
     * Refresh phone connection and data.
     */
    fun refresh() {
        viewModelScope.launch {
            communicationManager.checkPhoneConnection()
            communicationManager.requestStatusUpdate()
        }
    }
}
