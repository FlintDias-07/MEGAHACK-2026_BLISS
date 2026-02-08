package com.safepulse.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.safepulse.SafePulseApplication
import com.safepulse.data.db.entity.EmergencyContactEntity
import com.safepulse.data.db.entity.HotspotEntity
import com.safepulse.data.prefs.UserPreferences
import com.safepulse.data.repository.DisasterRepository
import com.safepulse.data.repository.EmergencyContactRepository
import com.safepulse.data.repository.EventLogRepository
import com.safepulse.data.repository.HotspotRepository
import com.safepulse.domain.model.RiskLevel
import com.safepulse.domain.model.SafetyMode
import com.safepulse.domain.saferoutes.DisasterAlert
import com.safepulse.service.SafetyForegroundService
import com.safepulse.worker.SafetyCheckWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeState(
    val isServiceRunning: Boolean = false,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val riskScore: Float = 0f,
    val safetyMode: SafetyMode = SafetyMode.NORMAL,
    val eventCount: Int = 0,
    val sosCount: Int = 0,
    val emergencyContacts: List<EmergencyContactEntity> = emptyList(),
    val isOnboardingComplete: Boolean = true,
    // Map state
    val currentLocation: LatLng? = null,
    val crimeHotspots: List<HotspotEntity> = emptyList(),
    val disasters: List<DisasterAlert> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    
    private val app = application as SafePulseApplication
    private val userPreferences = UserPreferences(application)
    private val contactRepository = EmergencyContactRepository(app.database.emergencyContactDao())
    private val eventLogRepository = EventLogRepository(app.database.eventLogDao())
    private val hotspotRepository = HotspotRepository(app.database.hotspotDao())
    private val disasterRepository = DisasterRepository()
    
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    
    init {
        loadInitialState()
        observeData()
    }
    
    private fun loadInitialState() {
        viewModelScope.launch {
            val settings = userPreferences.userSettingsFlow.first()
            _state.value = _state.value.copy(
                isServiceRunning = settings.serviceEnabled,
                isOnboardingComplete = settings.onboardingComplete
            )
        }
    }
    
    private fun observeData() {
        viewModelScope.launch {
            userPreferences.serviceEnabledFlow.collect { enabled ->
                _state.value = _state.value.copy(isServiceRunning = enabled)
            }
        }
        
        viewModelScope.launch {
            contactRepository.getAllContacts().collect { contacts ->
                _state.value = _state.value.copy(emergencyContacts = contacts)
            }
        }
        
        viewModelScope.launch {
            eventLogRepository.getAllEvents().collect { events ->
                _state.value = _state.value.copy(
                    eventCount = events.size,
                    sosCount = events.count { it.wasSOSSent }
                )
            }
        }
        
        // Load crime hotspots
        viewModelScope.launch {
            val hotspots = hotspotRepository.getAllHotspotsList()
            _state.value = _state.value.copy(crimeHotspots = hotspots)
        }
        
        // Load disaster alerts
        viewModelScope.launch {
            disasterRepository.getActiveDisasters().collect { disasters ->
                _state.value = _state.value.copy(disasters = disasters)
            }
        }
    }
    
    fun toggleService() {
        viewModelScope.launch {
            val newState = !_state.value.isServiceRunning
            
            if (newState) {
                SafetyForegroundService.start(app)
                SafetyCheckWorker.schedule(app)
            } else {
                SafetyForegroundService.stop(app)
                SafetyCheckWorker.cancel(app)
            }
            
            userPreferences.setServiceEnabled(newState)
        }
    }
    
    fun triggerManualSOS() {
        val service = SafetyForegroundService.getInstance()
        if (service != null) {
            service.triggerManualSOS()
        } else {
            // Service not running, start it first then trigger
            viewModelScope.launch {
                if (!_state.value.isServiceRunning) {
                    SafetyForegroundService.start(app)
                    userPreferences.setServiceEnabled(true)
                    // Give service time to initialize
                    kotlinx.coroutines.delay(1000)
                }
                SafetyForegroundService.getInstance()?.triggerManualSOS()
            }
        }
    }
    
    fun shareLocation() {
        viewModelScope.launch {
            val location = _state.value.currentLocation
            val message = if (location != null) {
                "📍 My current location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
            } else {
                "📍 Sharing my location from SafePulse app"
            }
            
            // Share via Android sharing intent
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, message)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "My Location - SafePulse")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            try {
                val chooser = android.content.Intent.createChooser(shareIntent, "Share Location")
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(chooser)
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error sharing location", e)
            }
        }
    }
    
    /**
     * Trigger a fake incoming call to help escape uncomfortable situations
     */
    fun triggerFakeCall() {
        viewModelScope.launch {
            try {
                // Create a call-like notification
                val notificationManager = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel(
                        "fake_call_channel",
                        "Fake Call",
                        android.app.NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Fake incoming call notifications"
                        setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI, 
                                android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                    .build())
                    }
                    notificationManager.createNotificationChannel(channel)
                }
                
                val notification = androidx.core.app.NotificationCompat.Builder(app, "fake_call_channel")
                    .setSmallIcon(android.R.drawable.sym_call_incoming)
                    .setContentTitle("Incoming call...")
                    .setContentText("Mom")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
                    .setFullScreenIntent(null, true)
                    .setAutoCancel(true)
                    .build()
                
                notificationManager.notify(999, notification)
                android.util.Log.i("HomeViewModel", "Fake call triggered")
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error triggering fake call", e)
            }
        }
    }
    
    /**
     * Trigger silent alert - sends SOS without sound/vibration for discreet situations
     */
    fun triggerSilentAlert() {
        viewModelScope.launch {
            val service = SafetyForegroundService.getInstance()
            if (service != null) {
                android.util.Log.i("HomeViewModel", "🔇 Triggering silent alert...")
                service.triggerSilentSOS()
            } else {
                // Service not running, start it first
                if (!_state.value.isServiceRunning) {
                    SafetyForegroundService.start(app)
                    userPreferences.setServiceEnabled(true)
                    kotlinx.coroutines.delay(1000)
                }
                SafetyForegroundService.getInstance()?.triggerSilentSOS()
            }
        }
    }
    
    fun updateRiskLevel(level: RiskLevel, score: Float) {
        _state.value = _state.value.copy(riskLevel = level, riskScore = score)
    }
    
    fun updateSafetyMode(mode: SafetyMode) {
        _state.value = _state.value.copy(safetyMode = mode)
    }
    
    // Demo functions for testing
    fun simulateHighRisk() {
        _state.value = _state.value.copy(
            riskLevel = RiskLevel.HIGH,
            riskScore = 0.85f
        )
    }
    
    fun simulateHeightenedMode() {
        _state.value = _state.value.copy(safetyMode = SafetyMode.HEIGHTENED)
    }
    
    fun resetDemo() {
        _state.value = _state.value.copy(
            riskLevel = RiskLevel.LOW,
            riskScore = 0f,
            safetyMode = SafetyMode.NORMAL
        )
    }
    
    // Map functions
    fun updateLocation(location: LatLng) {
        _state.value = _state.value.copy(currentLocation = location)
    }
}
