package com.safepulse.wear.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safepulse.wear.R
import com.safepulse.wear.WearSafePulseApp
import com.safepulse.wear.data.PhoneCommunicationManager
import com.safepulse.wear.data.WearPreferences
import com.safepulse.wear.presentation.WearMainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Foreground service for continuous safety monitoring on the watch.
 * Handles:
 * - Shake detection for SOS trigger
 * - Heart rate monitoring
 * - Phone connectivity monitoring
 * - Periodic status sync
 */
class WearSafetyService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "WearSafetyService"
        private const val NOTIFICATION_ID = 1001
        private const val SHAKE_THRESHOLD = 25f // m/s² threshold for shake
        private const val SHAKE_TIME_WINDOW = 1500L // ms for triple shake
        private const val SHAKE_COUNT_TRIGGER = 3
        private const val HEART_RATE_INTERVAL = 30_000L // 30 seconds
        private const val STATUS_SYNC_INTERVAL = 60_000L // 1 minute

        private var instance: WearSafetyService? = null

        fun getInstance(): WearSafetyService? = instance

        fun start(context: Context) {
            val intent = Intent(context, WearSafetyService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WearSafetyService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var sensorManager: SensorManager
    private lateinit var preferences: WearPreferences
    private lateinit var communicationManager: PhoneCommunicationManager
    private var wakeLock: PowerManager.WakeLock? = null

    // Shake detection
    private val shakeTimes = mutableListOf<Long>()
    private var lastShakeTime = 0L

    // Heart rate
    private var heartRateSensor: Sensor? = null
    private var lastHeartRate = 0
    private var heartRateMonitoringEnabled = true

    override fun onCreate() {
        super.onCreate()
        instance = this

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        preferences = WearPreferences(this)
        communicationManager = WearDataListenerService.communicationManager
            ?: PhoneCommunicationManager(this)

        acquireWakeLock()
        startForeground(NOTIFICATION_ID, createNotification())
        registerSensors()
        startPeriodicSync()

        Log.d(TAG, "WearSafetyService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        unregisterSensors()
        releaseWakeLock()
        super.onDestroy()
        Log.d(TAG, "WearSafetyService destroyed")
    }

    // ─── Sensors ─────────────────────────────────────────

    private fun registerSensors() {
        // Accelerometer for shake detection
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        // Heart rate sensor
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (heartRateSensor != null) {
            serviceScope.launch {
                heartRateMonitoringEnabled = preferences.heartRateMonitoringFlow.first()
                if (heartRateMonitoringEnabled) {
                    startHeartRateMonitoring()
                }
            }
        }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }

    private fun startHeartRateMonitoring() {
        heartRateSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
            Sensor.TYPE_HEART_RATE -> handleHeartRate(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ─── Shake Detection ─────────────────────────────────

    private fun handleAccelerometer(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        val now = System.currentTimeMillis()

        if (magnitude > SHAKE_THRESHOLD && (now - lastShakeTime) > 300) {
            lastShakeTime = now
            shakeTimes.add(now)

            // Remove old shakes outside the time window
            shakeTimes.removeAll { now - it > SHAKE_TIME_WINDOW }

            if (shakeTimes.size >= SHAKE_COUNT_TRIGGER) {
                shakeTimes.clear()
                onShakeDetected()
            }
        }
    }

    private fun onShakeDetected() {
        serviceScope.launch {
            val shakeEnabled = preferences.shakeSOSEnabledFlow.first()
            if (!shakeEnabled) return@launch

            Log.w(TAG, "Shake SOS detected!")

            // Vibrate to confirm
            vibrateAlert()

            // Trigger SOS on phone
            communicationManager.triggerSOS("MANUAL_SOS", 0.9f)
        }
    }

    // ─── Heart Rate ──────────────────────────────────────

    private fun handleHeartRate(event: SensorEvent) {
        val hr = event.values[0].toInt()
        if (hr > 0 && hr != lastHeartRate) {
            lastHeartRate = hr

            // Send to phone periodically
            serviceScope.launch {
                communicationManager.sendHeartRate(hr)
            }

            // Check for abnormal heart rate (potential distress)
            if (hr > 150 || hr < 40) {
                Log.w(TAG, "Abnormal heart rate detected: $hr BPM")
                // Could trigger alert — for now just log
            }
        }
    }

    // ─── Periodic Sync ───────────────────────────────────

    private fun startPeriodicSync() {
        serviceScope.launch {
            while (isActive) {
                try {
                    communicationManager.checkPhoneConnection()
                    communicationManager.requestStatusUpdate()
                } catch (e: Exception) {
                    Log.e(TAG, "Periodic sync failed", e)
                }
                delay(STATUS_SYNC_INTERVAL)
            }
        }
    }

    // ─── Utilities ───────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun vibrateAlert() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 200, 100, 200, 100, 400)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SafePulse::WearSafetyWakeLock"
        ).apply { acquire(10 * 60 * 1000L) } // 10 minutes max
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, WearMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, WearSafePulseApp.CHANNEL_SAFETY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("SafePulse Active")
            .setContentText("Safety monitoring running")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
