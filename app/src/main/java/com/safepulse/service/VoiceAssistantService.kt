package com.safepulse.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import com.safepulse.data.repository.SafeRoutesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Lightweight voice assistant coordinator.
 * This class is intentionally conservative: it exposes stable entry points
 * used by SafetyForegroundService while keeping internals simple and resilient.
 */
class VoiceAssistantService(
    private val context: Context,
    private val scope: CoroutineScope,
    @Suppress("unused")
    private val safeRoutesRepository: SafeRoutesRepository,
    private val onSendSosToContact: suspend (String) -> String
) {
    companion object {
        private const val TAG = "VoiceAssistantService"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var initialized = false
    private var enabled = false
    private var awaitingActivationResponse = false

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private val activationYesKeywords = listOf(
        "yes", "enable", "start", "on", "ok", "okay", "haan", "ha", "yes please"
    )
    private val activationNoKeywords = listOf(
        "no", "not now", "cancel", "nahi", "nahin", "mat"
    )
    private val stopKeywords = listOf("stop assistant", "disable assistant", "stop listening", "turn off")
    private val sosKeywords = listOf("send help", "sos", "emergency", "help me")

    fun initialize(onReady: () -> Unit = {}) {
        runOnMain {
            if (initialized) {
                onReady()
                return@runOnMain
            }

            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = Locale.getDefault()
                }
                initialized = true
                onReady()
            }
        }
    }

    fun startAssistantSession() {
        if (!initialized) {
            initialize { startAssistantSession() }
            return
        }
        if (!hasAudioPermission()) {
            speak("Microphone permission is required for voice assistant.")
            return
        }

        enabled = true
        awaitingActivationResponse = false
        speak("Voice assistant enabled. Say help to hear available commands.") {
            startListeningForCommands()
        }
    }

    fun promptVoiceAssistantActivation() {
        if (!initialized) {
            initialize { promptVoiceAssistantActivation() }
            return
        }
        if (!hasAudioPermission()) {
            awaitingActivationResponse = false
            enabled = false
            speak("Safety monitoring started. Microphone permission is required to enable voice assistant.")
            return
        }

        enabled = false
        awaitingActivationResponse = true
        speak("Safety monitoring started. Do you want to enable voice assistant?") {
            startListeningForCommands()
        }
    }

    fun stopAssistantSession() {
        enabled = false
        awaitingActivationResponse = false
        stopListening()
        Log.i(TAG, "Voice assistant session stopped")
    }

    fun sendSosToContact(contactName: String) {
        if (!enabled) return
        scope.launch {
            val result = onSendSosToContact(contactName)
            Log.i(TAG, "Voice SOS result: $result")
            speak(result) { resumeListeningIfNeeded() }
        }
    }

    fun cleanup() {
        enabled = false
        awaitingActivationResponse = false
        runOnMain {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
            speechRecognizer = null

            try {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
            } catch (_: Exception) {
            }
            textToSpeech = null
        }
    }

    @Suppress("unused")
    fun isEnabled(): Boolean = enabled

    private fun startListeningForCommands() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            speak("Speech recognition is not available right now.")
            return
        }

        runOnMain {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit

                    override fun onError(error: Int) {
                        if (shouldKeepListening()) {
                            scheduleRestart(if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 150L else 350L)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val spoken = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                        if (spoken.length < 2) {
                            resumeListeningIfNeeded()
                            return
                        }

                        processRecognizedText(spoken)
                    }
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start speech recognition", e)
                scheduleRestart(400L)
            }
        }
    }

    private fun processRecognizedText(rawText: String) {
        stopListening()
        val text = rawText.lowercase(Locale.getDefault()).trim()

        if (awaitingActivationResponse) {
            if (activationYesKeywords.any { text.contains(it) }) {
                awaitingActivationResponse = false
                enabled = true
                speak("Voice assistant enabled.") { resumeListeningIfNeeded() }
                return
            }
            if (activationNoKeywords.any { text.contains(it) }) {
                awaitingActivationResponse = false
                enabled = false
                speak("Okay. Voice assistant will remain off.")
                return
            }
            speak("Please say yes or no.") { resumeListeningIfNeeded() }
            return
        }

        if (!enabled) {
            return
        }

        if (stopKeywords.any { text.contains(it) }) {
            enabled = false
            awaitingActivationResponse = false
            speak("Voice assistant stopped.")
            return
        }

        if (text.contains("help")) {
            speak("You can say send help to contact name, or stop assistant.") { resumeListeningIfNeeded() }
            return
        }

        if (sosKeywords.any { text.contains(it) }) {
            val contact = extractContactName(text)
            if (contact.isBlank()) {
                speak("Please say contact name, for example send help to mom.") { resumeListeningIfNeeded() }
            } else {
                sendSosToContact(contact)
            }
            return
        }

        speak("I heard you, but did not recognize that command.") { resumeListeningIfNeeded() }
    }

    private fun extractContactName(text: String): String {
        val marker = "to "
        val idx = text.indexOf(marker)
        if (idx == -1) return ""
        return text.substring(idx + marker.length).trim()
    }

    private fun stopListening() {
        runOnMain {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
        }
    }

    private fun speak(message: String, onDone: (() -> Unit)? = null) {
        runOnMain {
            try {
                if (textToSpeech == null) {
                    onDone?.invoke()
                    return@runOnMain
                }
                textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "voice_assistant")
            } catch (_: Exception) {
            } finally {
                if (onDone != null) {
                    mainHandler.postDelayed({ onDone() }, 900L)
                }
            }
        }
    }

    private fun shouldKeepListening(): Boolean {
        return enabled || awaitingActivationResponse
    }

    private fun resumeListeningIfNeeded() {
        if (shouldKeepListening()) {
            scheduleRestart()
        }
    }

    private fun scheduleRestart(delayMs: Long = 250L) {
        mainHandler.postDelayed({
            if (shouldKeepListening()) startListeningForCommands()
        }, delayMs)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
