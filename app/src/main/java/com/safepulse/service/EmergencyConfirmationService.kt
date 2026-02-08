package com.safepulse.service

import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import java.util.*

/**
 * Service to confirm emergency situations using voice interaction.
 * Asks "Do you need help?" via TTS and listens for user response.
 * Cancels emergency if user says "no", otherwise proceeds after timeout.
 */
class EmergencyConfirmationService(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "EmergencyConfirmation"
        private const val UTTERANCE_ID = "emergency_confirmation"
        private const val CONFIRMATION_TIMEOUT_SECONDS = 10
    }

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isInitialized = false
    private var countdownJob: Job? = null
    private var confirmationCallback: ((Boolean) -> Unit)? = null
    private var isConfirmationInProgress = false

    /**
     * Initialize TTS and Speech Recognition
     */
    fun initialize(onReady: () -> Unit) {
        if (isInitialized) {
            onReady()
            return
        }

        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                
                // Set up utterance listener
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS started: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS completed: $utteranceId")
                        if (utteranceId == UTTERANCE_ID) {
                            // Start listening after TTS finishes
                            startListening()
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS error: $utteranceId")
                        // If TTS fails, start listening anyway
                        if (utteranceId == UTTERANCE_ID) {
                            startListening()
                        }
                    }
                })

                isInitialized = true
                Log.i(TAG, "TTS initialized successfully")
                onReady()
            } else {
                Log.e(TAG, "TTS initialization failed")
                isInitialized = false
            }
        }
    }

    /**
     * Start confirmation process
     * @param onConfirmed Called with true if emergency confirmed, false if cancelled
     */
    fun startConfirmation(onConfirmed: (Boolean) -> Unit) {
        if (isConfirmationInProgress) {
            Log.w(TAG, "Confirmation already in progress")
            return
        }

        if (!isInitialized) {
            Log.e(TAG, "Service not initialized, proceeding with emergency")
            onConfirmed(true)
            return
        }

        isConfirmationInProgress = true
        confirmationCallback = onConfirmed

        Log.i(TAG, "🎤 Starting emergency confirmation...")
        Log.i(TAG, "   Question: 'Do you need help?'")
        Log.i(TAG, "   Timeout: $CONFIRMATION_TIMEOUT_SECONDS seconds")
        Log.i(TAG, "   Cancel word: 'no'")
        
        // Show toast to user
        showToast("Emergency confirmation started - Say NO to cancel")

        // Start countdown timer
        startCountdownTimer()

        // Speak the confirmation question
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
        }
        textToSpeech?.speak("Do you need help?", TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
        
        // CRITICAL FIX: Start listening IMMEDIATELY, don't wait for TTS callback
        // The TTS callback sometimes doesn't fire, leaving us with no speech recognition
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isConfirmationInProgress) {
                Log.i(TAG, "Starting speech recognition now...")
                startListening()
            }
        }, 500) // Small delay to let TTS start
    }
    
    /**
     * Show toast notification
     */
    private fun showToast(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(
                context,
                message,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Start countdown timer for confirmation timeout
     */
    private fun startCountdownTimer() {
        countdownJob?.cancel()
        
        Log.i(TAG, "⏱️ Starting 10-second countdown timer...")
        
        countdownJob = scope.launch {
            var secondsRemaining = CONFIRMATION_TIMEOUT_SECONDS
            
            while (secondsRemaining > 0 && isConfirmationInProgress) {
                Log.d(TAG, "⏱️ Countdown: $secondsRemaining seconds remaining")
                delay(1000)
                secondsRemaining--
            }
            
            if (isConfirmationInProgress) {
                Log.i(TAG, "⏰ Countdown completed - 10 seconds elapsed, proceeding with emergency")
                confirmEmergency()
            } else {
                Log.d(TAG, "Countdown stopped - confirmation already handled")
            }
        }
    }

    /**
     * Start listening for user's voice response
     */
    private fun startListening() {
        try {
            // Check for microphone permission first
            if (!hasAudioPermission()) {
                Log.e(TAG, "❌ RECORD_AUDIO permission not granted - cannot listen for response")
                Log.e(TAG, "   User cannot cancel via voice - will proceed after timeout")
                // Speak to inform user
                textToSpeech?.speak(
                    "Microphone permission needed to cancel",
                    TextToSpeech.QUEUE_ADD,
                    null,
                    "permission_warning"
                )
                return
            }
            
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.e(TAG, "Speech recognition not available - waiting for countdown timer")
                // Don't immediately confirm - let the countdown timer handle it
                return
            }

            Log.d(TAG, "🎤 Initializing speech recognizer...")
            Log.d(TAG, "   Microphone permission: GRANTED")
            
            showToast("Listening for 'NO'...")
            
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech input")
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Check partial results too - cancel as soon as we hear "no"
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                        if (matches.isNotEmpty()) {
                            Log.d(TAG, "Partial result: ${matches.first()}")
                            
                            // Check if "no" is detected in partial results
                            val cancelWords = listOf("no", "nope", "cancel", "stop")
                            val containsNo = matches.any { result ->
                                val lower = result.lowercase().trim()
                                cancelWords.any { word -> 
                                    lower == word || lower.contains(" $word") || lower.startsWith("$word ")
                                }
                            }
                            
                            if (containsNo) {
                                Log.i(TAG, "🚫 Detected NO in partial results - immediate cancel!")
                                showToast("Heard 'NO' - Cancelling...")
                                cancelEmergency()
                            }
                        }
                    }
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "User started speaking")
                    showToast("Heard you speaking...")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Audio level changed
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // Partial audio buffer received
                }

                override fun onEndOfSpeech() {
                    Log.d(TAG, "User stopped speaking")
                }

                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error: $error"
                    }
                    Log.w(TAG, "Speech recognition error: $errorMessage")
                    
                    // Show critical errors to user
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        showToast("Microphone permission denied!")
                    }
                    
                    // CRITICAL FIX: Keep restarting recognition until countdown expires
                    // This ensures we're ALWAYS listening during the full 10 seconds
                    if (isConfirmationInProgress) {
                        Log.d(TAG, "Restarting speech recognition to keep listening...")
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (isConfirmationInProgress) {
                                try {
                                    startListening()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to restart recognition", e)
                                }
                            }
                        }, 500) // Small delay before restart
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.isNotEmpty()) {
                        handleSpeechResults(matches)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Not used
                }
            })

            // Start recognition
            val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                
                // CRITICAL: Extend speech timeout to match our 10-second countdown
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 0L)
                
                // Prefer offline recognition for faster response
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Speech recognition started with extended 10-second timeout")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            // Don't immediately confirm - let the countdown timer handle it
            Log.w(TAG, "Speech recognition failed to start, waiting for countdown timer to complete")
        }
    }

    /**
     * Process speech recognition results
     */
    private fun handleSpeechResults(matches: List<String>) {
        Log.i(TAG, "📝 Speech results received (${matches.size} matches):")
        matches.forEachIndexed { index, match ->
            Log.i(TAG, "   [$index] \"$match\"")
        }

        // Check if any result contains "no" or similar negation
        val cancelWords = listOf("no", "nope", "cancel", "stop", "false", "negative", "nahi")
        
        var detectedCancelWord: String? = null
        val userSaidNo = matches.any { result ->
            val lowerResult = result.lowercase().trim()
            
            // Check for exact word match or word boundaries
            cancelWords.any { cancelWord ->
                val wordBoundaryMatch = lowerResult.split(" ").contains(cancelWord) || 
                                       lowerResult == cancelWord ||
                                       lowerResult.startsWith("$cancelWord ") ||
                                       lowerResult.endsWith(" $cancelWord") ||
                                       lowerResult.contains(" $cancelWord ")
                
                if (wordBoundaryMatch) {
                    detectedCancelWord = cancelWord
                    true
                } else {
                    false
                }
            }
        }

        if (userSaidNo) {
            Log.i(TAG, "🚫 User said NO - cancelling emergency")
            Log.i(TAG, "   Detected cancel word: '$detectedCancelWord'")
            
            // Show toast notification to user
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    context,
                    "Emergency CANCELLED - Protection STOPPED",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            
            cancelEmergency()
        } else {
            Log.i(TAG, "✅ User confirmed (no cancellation word detected) - proceeding with emergency")
            confirmEmergency()
        }
    }

    /**
     * Confirm emergency and trigger callback
     */
    private fun confirmEmergency() {
        if (!isConfirmationInProgress) return
        
        isConfirmationInProgress = false
        countdownJob?.cancel()
        stopListening()
        
        Log.i(TAG, "✅ Emergency CONFIRMED - proceeding")
        confirmationCallback?.invoke(true)
        confirmationCallback = null
    }

    /**
     * Cancel emergency and trigger callback
     */
    private fun cancelEmergency() {
        if (!isConfirmationInProgress) return
        
        isConfirmationInProgress = false
        countdownJob?.cancel()
        stopListening()
        
        Log.i(TAG, "🚫 Emergency CANCELLED by user")
        confirmationCallback?.invoke(false)
        confirmationCallback = null
    }

    /**
     * Check if RECORD_AUDIO permission is granted
     */
    private fun hasAudioPermission(): Boolean {
        return android.content.pm.PackageManager.PERMISSION_GRANTED == 
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
    }

    /**
     * Stop speech recognition
     */
    private fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        isConfirmationInProgress = false
        confirmationCallback = null
        countdownJob?.cancel()
        
        stopListening()
        
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        
        isInitialized = false
        Log.d(TAG, "EmergencyConfirmationService cleaned up")
    }
}