package com.safepulse.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.safepulse.SafePulseApplication
import com.safepulse.data.db.entity.EventLogEntity
import com.safepulse.data.prefs.UserPreferences
import com.safepulse.data.repository.EmergencyContactRepository
import com.safepulse.data.repository.EmergencyServiceRepository
import com.safepulse.data.repository.EventLogRepository
import com.safepulse.data.repository.HotspotRepository
import com.safepulse.data.repository.SafeRoutesRepository
import com.safepulse.data.repository.UnsafeZoneRepository
import com.safepulse.domain.engine.SafetyEngine
import com.safepulse.domain.model.*
import com.safepulse.domain.saferoutes.RouteRiskAnalyzer
import com.safepulse.ml.StubVoiceTriggerModule
import com.safepulse.ml.VoiceTriggerFactory
import com.safepulse.ml.VoiceTriggerModule
import com.safepulse.utils.NotificationHelper
import com.safepulse.utils.SafetyConstants
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Foreground service for continuous safety monitoring.
 * Runs sensors, location tracking, and safety engine in background.
 */
class SafetyForegroundService : LifecycleService(), SensorEventListener {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Components
    private lateinit var safetyEngine: SafetyEngine
    private lateinit var locationTracker: LocationTracker
    private lateinit var emergencyManager: EmergencyManager
    private lateinit var voiceTriggerModule: VoiceTriggerModule
    private lateinit var voiceAssistantService: VoiceAssistantService
    private lateinit var shakeDetector: ShakeDetector
    private lateinit var confirmationService: EmergencyConfirmationService
    
    // Repositories
    private lateinit var hotspotRepository: HotspotRepository
    private lateinit var unsafeZoneRepository: UnsafeZoneRepository
    private lateinit var contactRepository: EmergencyContactRepository
    private lateinit var eventLogRepository: EventLogRepository
    private lateinit var emergencyServiceRepository: EmergencyServiceRepository
    private lateinit var safeRoutesRepository: SafeRoutesRepository
    private lateinit var userPreferences: UserPreferences
    
    // Sensors
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    
    // State
    private var wakeLock: PowerManager.WakeLock? = null
    private var isEmergencyCountdownActive = false
    private var countdownJob: Job? = null
    private var lastRiskLevel: RiskLevel = RiskLevel.LOW
    private var monitoringStarted = false
    private var emergencyFeedbackJob: Job? = null
    private var emergencyRecordingJob: Job? = null
    private var emergencyAudioRecorder: MediaRecorder? = null
    private var currentEmergencyAudioFile: File? = null
    
    override fun onCreate() {
        super.onCreate()
        
        initializeComponents()
        initializeSensors()
        startForeground()
        
        // Store instance for external access
        instance = this
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            SafetyConstants.ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        startMonitoring()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        stopEmergencyFeedbackNow()
        stopEmergencyAudioRecordingNow()
        voiceAssistantService.cleanup()
        confirmationService.cleanup()
        serviceScope.cancel()
        releaseWakeLock()
        
        // Clear instance
        instance = null
    }
    
    private fun initializeComponents() {
        // Initialize repositories
        val database = (application as SafePulseApplication).database
        hotspotRepository = HotspotRepository(database.hotspotDao())
        unsafeZoneRepository = UnsafeZoneRepository(database.unsafeZoneDao())
        contactRepository = EmergencyContactRepository(database.emergencyContactDao())
        eventLogRepository = EventLogRepository(database.eventLogDao())
        emergencyServiceRepository = EmergencyServiceRepository(database.emergencyServiceDao())
        userPreferences = UserPreferences(this@SafetyForegroundService)
        
        // Initialize managers
        locationTracker = LocationTracker(this)
        emergencyManager = EmergencyManager(this)
        
        // Create and link nearby services manager
        val nearbyServicesManager = NearbyServicesManager(emergencyServiceRepository)
        emergencyManager.setNearbyServicesManager(nearbyServicesManager)
        
        // Initialize safety engine
        safetyEngine = SafetyEngine()

        // Initialize safe routes + voice assistant services
        val routeRiskAnalyzer = RouteRiskAnalyzer(hotspotRepository)
        safeRoutesRepository = SafeRoutesRepository(this@SafetyForegroundService, routeRiskAnalyzer)
        voiceAssistantService = VoiceAssistantService(
            context = this@SafetyForegroundService,
            scope = serviceScope,
            safeRoutesRepository = safeRoutesRepository,
            onSendSosToContact = { contactName -> sendVoiceSosToContact(contactName) }
        )
        voiceAssistantService.initialize {
            Log.d("SafetyService", "Voice assistant service initialized")
        }
        
        // Initialize voice trigger module
        voiceTriggerModule = VoiceTriggerFactory.create(this@SafetyForegroundService)
        Log.d("SafetyService", "Voice trigger module initialized: ${voiceTriggerModule.javaClass.simpleName}")
        
        // Initialize shake detector
        shakeDetector = ShakeDetector(serviceScope)
        Log.d("SafetyService", "Shake detector initialized")
        
        // Initialize emergency confirmation service
        confirmationService = EmergencyConfirmationService(this@SafetyForegroundService, serviceScope)
        confirmationService.initialize {
            Log.d("SafetyService", "Emergency confirmation service initialized")
        }
    }
    
    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }
    
    private fun startForeground() {
        val notification = NotificationHelper.createForegroundNotification(this)
        startForeground(SafetyConstants.NOTIFICATION_ID_FOREGROUND, notification)
        
        acquireWakeLock()
    }
    
    private fun startMonitoring() {
        if (monitoringStarted) {
            Log.d("SafetyService", "Monitoring already started; skipping duplicate initialization")
            return
        }
        monitoringStarted = true

        // Load zone data
        serviceScope.launch {
            loadZoneData()
            loadUserProfile()
        }
        
        // Start location tracking
        locationTracker.startTracking(SafetyMode.NORMAL)
        
        // Register sensor listeners
        val sensorDelay = SafetyConstants.SENSOR_DELAY_NORMAL_US
        accelerometer?.let {
            sensorManager.registerListener(this, it, sensorDelay)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, sensorDelay)
        }
        
        // Start voice trigger if enabled
        serviceScope.launch {
            userPreferences.voiceTriggerEnabledFlow.collect { enabled ->
                Log.d("SafetyService", "Voice trigger enabled setting changed: $enabled")
                Log.d("SafetyService", "Voice trigger module type: ${voiceTriggerModule::class.simpleName}")
                if (enabled) {
                    Log.i("SafetyService", "Starting voice trigger listening...")
                    voiceTriggerModule.startListening()
                    Log.i("SafetyService", "Voice trigger listening started, isListening=${voiceTriggerModule.isListening()}")
                } else {
                    Log.i("SafetyService", "Stopping voice trigger...")
                    voiceTriggerModule.stopListening()
                }
            }
        }
        
        // Observe location updates
        serviceScope.launch {
            locationTracker.currentLocation.collect { location ->
                location?.let {
                    safetyEngine.processLocation(it)
                }
            }
        }
        
        // Observe safety state changes
        serviceScope.launch {
            safetyEngine.safetyState.collect { state ->
                handleSafetyStateChange(state)
            }
        }
        
        // Observe emergency events
        serviceScope.launch {
            safetyEngine.emergencyEvent.collect { event ->
                event?.let { handleEmergencyEvent(it) }
            }
        }
        
        // Observe voice trigger
        serviceScope.launch {
            voiceTriggerModule.keywordDetectedFlow().collect { result ->
                Log.d("SafetyService", "Voice trigger result received: detected=${result.detected}, keyword=${result.keyword}, confidence=${result.confidence}")
                if (result.detected) {
                    Log.i("SafetyService", "🔥 VOICE TRIGGER DETECTED! Activating emergency response...")
                    handleVoiceEmergency()
                }
            }
        }
        
        // Observe triple shake gesture
        serviceScope.launch {
            shakeDetector.tripleShakeDetected.collect {
                Log.i("SafetyService", "🚨 TRIPLE SHAKE DETECTED!")
                handleShakeEmergency()
            }
        }
    }
    
    private fun stopMonitoring() {
        monitoringStarted = false
        locationTracker.stopTracking()
        sensorManager.unregisterListener(this)
        voiceTriggerModule.stopListening()
        countdownJob?.cancel()
    }
    
    private suspend fun loadZoneData() {
        val hotspots = hotspotRepository.getAllHotspotsList()
        val unsafeZones = unsafeZoneRepository.getAllUnsafeZonesList()
        safetyEngine.updateZoneData(hotspots, unsafeZones)
    }
    
    private suspend fun loadUserProfile() {
        val settings = userPreferences.userSettingsFlow.first()
        safetyEngine.updateUserProfile(
            UserProfile(settings.gender, settings.voiceTriggerEnabled)
        )
    }
    
    private fun handleSafetyStateChange(state: SafetyState) {
        // Update location tracking mode
        if (state.mode == SafetyMode.HEIGHTENED) {
            locationTracker.updateMode(SafetyMode.HEIGHTENED)
            updateSensorDelay(SafetyConstants.SENSOR_DELAY_HEIGHTENED_US)
        } else {
            locationTracker.updateMode(SafetyMode.NORMAL)
            updateSensorDelay(SafetyConstants.SENSOR_DELAY_NORMAL_US)
        }
        
        // Show high risk notification
        if (state.riskLevel == RiskLevel.HIGH && lastRiskLevel != RiskLevel.HIGH) {
            NotificationHelper.showRiskAlertNotification(this, state.riskLevel)
        } else if (state.riskLevel != RiskLevel.HIGH && lastRiskLevel == RiskLevel.HIGH) {
            NotificationHelper.cancelRiskAlertNotification(this)
        }
        
        lastRiskLevel = state.riskLevel
    }
    
    private fun handleEmergencyEvent(event: EmergencyEvent) {
        if (isEmergencyCountdownActive) return
        
        if (event.requiresConfirmation) {
            startEmergencyCountdown(event)
        } else {
            executeEmergencyResponse(event)
        }
    }
    
    private fun startEmergencyCountdown(event: EmergencyEvent) {
        isEmergencyCountdownActive = true
        
        countdownJob = serviceScope.launch {
            var secondsRemaining = SafetyConstants.CANCEL_WINDOW_SECONDS
            
            while (secondsRemaining > 0 && isActive) {
                NotificationHelper.showEmergencyCountdownNotification(
                    this@SafetyForegroundService,
                    event.type,
                    secondsRemaining
                )
                delay(1000)
                secondsRemaining--
            }
            
            if (isActive) {
                NotificationHelper.cancelEmergencyNotification(this@SafetyForegroundService)
                executeEmergencyResponse(event)
            }
            
            isEmergencyCountdownActive = false
        }
    }
    
    fun cancelEmergencyCountdown() {
        countdownJob?.cancel()
        isEmergencyCountdownActive = false
        NotificationHelper.cancelEmergencyNotification(this)
        safetyEngine.clearEmergency()
    }
    
    /**
     * Trigger manual SOS from UI
     */
    fun triggerManualSOS() {
        safetyEngine.triggerManualSOS()
    }
    
    /**
     * Trigger silent SOS - sends SMS without call, for discreet emergencies
     */
    fun triggerSilentSOS() {
        safetyEngine.triggerSilentSOS()
    }
    
    private fun executeEmergencyResponse(event: EmergencyEvent) {
        serviceScope.launch {
            startEmergencyFeedback()

            // Get emergency contacts
            val contacts = contactRepository.getAllContactsList()
            val primaryContact = contactRepository.getPrimaryContact()
            
            Log.i("SafetyService", if (event.silent) "🔇 Silent Alert - SMS only" else "🚨 Full Alert - SMS + Call")
            
            // Send SMS to all emergency contacts
            val smsSent = emergencyManager.sendSOSMessages(contacts, event)
            
            // Send SMS to nearby emergency services (police/hospital)
            emergencyManager.sendAlertToNearbyServices(event)
            
            // Only initiate call if NOT silent mode
            if (!event.silent) {
                Log.i("SafetyService", "📞 Initiating emergency call...")
                emergencyManager.initiateEmergencyCall(primaryContact)
            } else {
                Log.i("SafetyService", "🔇 Skipping call (silent mode)")
            }
            
            // Log event
            eventLogRepository.logEvent(
                type = event.type.name,
                confidence = event.confidence,
                lat = event.location?.latitude ?: 0.0,
                lng = event.location?.longitude ?: 0.0,
                mode = safetyEngine.safetyState.value.mode.name,
                wasSOSSent = smsSent
            )
            
            // Clear emergency state
            safetyEngine.clearEmergency()
        }
    }
    
    private fun updateSensorDelay(delayUs: Int) {
        sensorManager.unregisterListener(this)
        accelerometer?.let {
            sensorManager.registerListener(this, it, delayUs)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, delayUs)
        }
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SafePulse::SafetyMonitoringLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    fun startVoiceAssistantSession() {
        voiceAssistantService.startAssistantSession()
    }

    fun promptVoiceAssistantActivation() {
        voiceAssistantService.promptVoiceAssistantActivation()
    }

    private suspend fun sendVoiceSosToContact(rawContactName: String): String {
        val contactName = rawContactName.trim()
        if (contactName.isBlank()) return "I could not find that contact."

        val contacts = contactRepository.getAllContactsList()
        if (contacts.isEmpty()) return "No emergency contacts are configured."

        val normalizedName = contactName.lowercase()
        val contact = contacts.firstOrNull { it.name.lowercase() == normalizedName }
            ?: contacts.firstOrNull { it.name.lowercase().contains(normalizedName) }
            ?: return "I could not find that contact."

        val event = EmergencyEvent(
            timestamp = System.currentTimeMillis(),
            type = EventType.VOICE_TRIGGER,
            confidence = 1.0f,
            location = locationTracker.currentLocation.value,
            requiresConfirmation = false,
            silent = true
        )

        startEmergencyFeedback()

        val smsSent = emergencyManager.sendSOSMessages(listOf(contact), event)
        if (!smsSent) return "I could not send the SOS message."

        eventLogRepository.logEvent(
            type = event.type.name,
            confidence = event.confidence,
            lat = event.location?.latitude ?: 0.0,
            lng = event.location?.longitude ?: 0.0,
            mode = safetyEngine.safetyState.value.mode.name,
            wasSOSSent = true
        )

        return "SOS message sent to ${contact.name}."
    }
    
    // Sensor data accumulator
    private var lastAccelData = floatArrayOf(0f, 0f, 0f)
    private var lastGyroData = floatArrayOf(0f, 0f, 0f)
    
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccelData = event.values.clone()
                
                // Pass to shake detector for emergency gesture detection
                shakeDetector.onAccelerometerChanged(event)
            }
            Sensor.TYPE_GYROSCOPE -> {
                lastGyroData = event.values.clone()
                
                // Process combined sensor data
                val sensorData = SensorData(
                    accelerometerX = lastAccelData[0],
                    accelerometerY = lastAccelData[1],
                    accelerometerZ = lastAccelData[2],
                    gyroscopeX = lastGyroData[0],
                    gyroscopeY = lastGyroData[1],
                    gyroscopeZ = lastGyroData[2]
                )
                
                safetyEngine.processSensorData(sensorData, locationTracker.currentLocation.value)
            }
        }
    }
    
    /**
     * Handle shake gesture emergency trigger
     * Uses voice confirmation to prevent false positives
     */
    private fun handleShakeEmergency() {
        Log.i("SafetyService", "🚨 Triple shake detected - starting confirmation...")
        
        // Start voice confirmation
        // Note: Voice trigger is NOT paused because it's only active when manually triggered via button
        confirmationService.startConfirmation { confirmed ->
            if (confirmed) {
                Log.i("SafetyService", "✅ Emergency confirmed - proceeding")
                proceedWithShakeEmergency()
            } else {
                Log.i("SafetyService", "🚫 Emergency cancelled by voice command - stopping protection service")
                shakeDetector.reset()

                // Turn off service state so Home screen ACTIVE button switches off.
                serviceScope.launch {
                    userPreferences.setServiceEnabled(false)
                }

                stopMonitoring()
                stopSelf()
            }
        }
    }
    
    /**
     * Proceed with shake emergency after confirmation
     */
    private fun proceedWithShakeEmergency() {
        serviceScope.launch {
            try {
                startEmergencyFeedback()

                // Get emergency contacts
                val contacts = contactRepository.getAllContactsList()
                
                if (contacts.isEmpty()) {
                    Log.w("SafetyService", "⚠️ No emergency contacts configured")
                    // Reset shake detection to allow retry
                    shakeDetector.reset()
                    return@launch
                }
                
                // Get current location
                val location = locationTracker.currentLocation.value
                
                // Create emergency event
                val event = EmergencyEvent(
                    timestamp = System.currentTimeMillis(),
                    type = EventType.MANUAL_SOS,
                    confidence = 1.0f, // User triggered
                    location = location
                )
                
                Log.i("SafetyService", "")
                Log.i("SafetyService", "🚨 Processing shake gesture emergency...")
                Log.i("SafetyService", "   Contacts: ${contacts.size}")
                Log.i("SafetyService", "   Location: ${if (location != null) "Available" else "Unavailable"}")
                Log.i("SafetyService", "")
                
                // Trigger emergency response with photo capture
                emergencyManager.triggerVolumeButtonEmergency(
                    contacts = contacts,
                    event = event,
                    lifecycleOwner = this@SafetyForegroundService
                )
                
                // Save event to log
                eventLogRepository.logEvent(
                    type = event.type.name,
                    confidence = event.confidence,
                    lat = location?.latitude ?: 0.0,
                    lng = location?.longitude ?: 0.0,
                    mode = safetyEngine.safetyState.value.mode.name,
                    wasSOSSent = true
                )
                
                Log.i("SafetyService", "✅ Shake gesture emergency response completed")
                
            } catch (e: Exception) {
                Log.e("SafetyService", "❌ Error handling shake emergency", e)
                // Reset shake detection to allow retry
                shakeDetector.reset()
            }
        }
    }
    
    /**
     * Handle voice trigger emergency
     */
    private fun handleVoiceEmergency() {
        serviceScope.launch {
            try {
                startEmergencyFeedback()

                // Get emergency contacts
                val contacts = contactRepository.getAllContactsList()
                
                if (contacts.isEmpty()) {
                    Log.w("SafetyService", "⚠️ No emergency contacts configured")
                    return@launch
                }
                
                // Get current location
                val location = locationTracker.currentLocation.value
                
                // Create emergency event
                val event = EmergencyEvent(
                    timestamp = System.currentTimeMillis(),
                    type = EventType.MANUAL_SOS,
                    confidence = 1.0f, // User triggered
                    location = location
                )
                
                Log.i("SafetyService", "")
                Log.i("SafetyService", "🗣️ Processing voice emergency trigger...")
                Log.i("SafetyService", "   Contacts: ${contacts.size}")
                Log.i("SafetyService", "   Location: ${if (location != null) "Available" else "Unavailable"}")
                Log.i("SafetyService", "")
                
                // Trigger emergency response with photo capture
                emergencyManager.triggerVolumeButtonEmergency(
                    contacts = contacts,
                    event = event,
                    lifecycleOwner = this@SafetyForegroundService
                )
                
                // Save event to log
                eventLogRepository.logEvent(
                    type = event.type.name,
                    confidence = event.confidence,
                    lat = location?.latitude ?: 0.0,
                    lng = location?.longitude ?: 0.0,
                    mode = safetyEngine.safetyState.value.mode.name,
                    wasSOSSent = true
                )
                
                Log.i("SafetyService", "✅ Voice emergency response completed")
                
            } catch (e: Exception) {
                Log.e("SafetyService", "❌ Error handling voice emergency", e)
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    private fun startEmergencyFeedback(durationMs: Long = 20_000L) {
        emergencyFeedbackJob?.cancel()
        stopEmergencyFeedbackNow()
        startEmergencyAudioRecording(durationMs)

        emergencyFeedbackJob = serviceScope.launch {
            startEmergencyVibration()
            val torchCameraId = setTorchEnabled(true)

            delay(durationMs)

            stopEmergencyVibration()
            if (torchCameraId != null) {
                setTorchEnabled(false, torchCameraId)
            }
        }
    }

    private fun stopEmergencyFeedbackNow() {
        stopEmergencyVibration()
        setTorchEnabled(false)
    }

    private fun startEmergencyAudioRecording(durationMs: Long = 20_000L) {
        emergencyRecordingJob?.cancel()
        stopEmergencyAudioRecordingNow()

        if (!hasRecordAudioPermission()) {
            Log.w("SafetyService", "Skipping SOS audio recording: RECORD_AUDIO permission not granted")
            return
        }

        val outputFile = createEmergencyAudioFile() ?: run {
            Log.w("SafetyService", "Skipping SOS audio recording: could not create output file")
            return
        }

        val recorder = MediaRecorder()
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(96000)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()

            emergencyAudioRecorder = recorder
            currentEmergencyAudioFile = outputFile
            Log.i("SafetyService", "SOS audio recording started: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("SafetyService", "Could not start SOS audio recording", e)
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
            outputFile.delete()
            emergencyAudioRecorder = null
            currentEmergencyAudioFile = null
            return
        }

        emergencyRecordingJob = serviceScope.launch {
            delay(durationMs)
            stopEmergencyAudioRecordingNow()
        }
    }

    private fun stopEmergencyAudioRecordingNow() {
        emergencyRecordingJob?.cancel()
        emergencyRecordingJob = null

        val recorder = emergencyAudioRecorder ?: return
        val outputFile = currentEmergencyAudioFile

        var stopSucceeded = true
        try {
            recorder.stop()
        } catch (e: Exception) {
            stopSucceeded = false
            Log.w("SafetyService", "SOS audio stop failed; deleting partial file", e)
        } finally {
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
            emergencyAudioRecorder = null
        }

        if (!stopSucceeded) {
            outputFile?.delete()
            currentEmergencyAudioFile = null
            return
        }

        if (outputFile != null) {
            Log.i("SafetyService", "SOS audio recording saved: ${outputFile.absolutePath}")
        }
        currentEmergencyAudioFile = null
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createEmergencyAudioFile(): File? {
        val rootDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(filesDir, "audio")
        val recordingsDir = File(rootDir, "sos_recordings")
        if (!recordingsDir.exists() && !recordingsDir.mkdirs()) {
            return null
        }

        return File(recordingsDir, "sos_audio_${System.currentTimeMillis()}.m4a")
    }

    @Suppress("DEPRECATION")
    private fun startEmergencyVibration() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return

        try {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 300), 0)
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Log.w("SafetyService", "Could not start emergency vibration", e)
            return
        }
    }

    @Suppress("DEPRECATION")
    private fun stopEmergencyVibration() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        try {
            vibrator.cancel()
        } catch (e: Exception) {
            Log.w("SafetyService", "Could not stop emergency vibration", e)
        }
    }

    private fun setTorchEnabled(enabled: Boolean, preferredCameraId: String? = null): String? {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null

        val cameraId = preferredCameraId ?: findTorchCameraId(cameraManager) ?: return null

        return try {
            cameraManager.setTorchMode(cameraId, enabled)
            cameraId
        } catch (e: SecurityException) {
            Log.w("SafetyService", "Torch control denied (camera permission missing)", e)
            null
        } catch (e: CameraAccessException) {
            Log.w("SafetyService", "Torch control failed", e)
            null
        } catch (e: Exception) {
            Log.w("SafetyService", "Unexpected torch error", e)
            null
        }
    }

    private fun findTorchCameraId(cameraManager: CameraManager): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { cameraId ->
                val chars = cameraManager.getCameraCharacteristics(cameraId)
                chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            Log.w("SafetyService", "Could not resolve flash camera", e)
            null
        }
    }
    
    companion object {
        private var instance: SafetyForegroundService? = null
        
        fun getInstance(): SafetyForegroundService? = instance
        
        fun start(context: Context) {
            val intent = Intent(context, SafetyForegroundService::class.java)
            context.startForegroundService(intent)
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, SafetyForegroundService::class.java).apply {
                action = SafetyConstants.ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
