package com.safepulse.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.safepulse.data.repository.SafeRoutesRepository
import com.safepulse.domain.saferoutes.VoiceNavigationRoute
import com.safepulse.domain.saferoutes.VoiceNavigationStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Voice assistant for location queries and walking navigation.
 */
class VoiceAssistantService(
    private val context: Context,
    private val scope: CoroutineScope,
    private val safeRoutesRepository: SafeRoutesRepository,
    private val onSendSosToContact: suspend (String) -> String
) {
    companion object {
        private const val TAG = "VoiceAssistant"
        private const val NAVIGATION_UPDATE_INTERVAL_MS = 3000L
        private const val STEP_REACHED_THRESHOLD_METERS = 20f
        private const val DESTINATION_REACHED_THRESHOLD_METERS = 15f
        private const val MIN_VALID_TEXT_LENGTH = 2
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isInitialized = false
    private var pendingOnSpeechDone: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isVoiceAssistantEnabled = false
    private var awaitingActivationResponse = false
    private var awaitingSosConfirmation = false
    private var pendingSosContactName: String? = null
    private var awaitingNavigationConfirmation = false
    private var pendingNavigationRoute: VoiceNavigationRoute? = null

    private var activeNavigationRoute: VoiceNavigationRoute? = null
    private var currentStepIndex = 0
    private var navigationLocationCallback: LocationCallback? = null

    fun initialize(onReady: () -> Unit) {
        if (isInitialized) {
            onReady()
            return
        }

        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        pendingOnSpeechDone?.let { action ->
                            pendingOnSpeechDone = null
                            action()
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        pendingOnSpeechDone?.let { action ->
                            pendingOnSpeechDone = null
                            action()
                        }
                    }
                })
                isInitialized = true
                onReady()
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }
    }

    fun startAssistantSession() {
        if (!isInitialized) {
            initialize { startAssistantSession() }
            return
        }

        if (!hasAudioPermission()) {
            speak("Microphone permission is required for voice assistant.")
            return
        }

        isVoiceAssistantEnabled = true
        awaitingActivationResponse = false
        awaitingSosConfirmation = false
        pendingSosContactName = null
        awaitingNavigationConfirmation = false
        pendingNavigationRoute = null
        stopListening()
        speak("Voice assistant enabled. You can say commands like send help, where am I, or directions to a location.") {
            startListeningForCommands()
        }
    }

    fun promptVoiceAssistantActivation() {
        if (!isInitialized) {
            initialize { promptVoiceAssistantActivation() }
            return
        }

        awaitingNavigationConfirmation = false
        pendingNavigationRoute = null
        awaitingSosConfirmation = false
        pendingSosContactName = null
        isVoiceAssistantEnabled = false
        awaitingActivationResponse = true

        if (!hasAudioPermission()) {
            awaitingActivationResponse = false
            speak("Safety monitoring started. Microphone permission is required to enable voice assistant.")
            return
        }

        stopListening()
        speak("Safety monitoring started. Do you want to enable voice assistant?") {
            startListeningForCommands()
        }
    }

    fun stopAssistantSession() {
        isVoiceAssistantEnabled = false
        awaitingActivationResponse = false
        awaitingSosConfirmation = false
        pendingSosContactName = null
        awaitingNavigationConfirmation = false
        pendingNavigationRoute = null
        stopListening()
        stopNavigation()
    }

    private fun startListeningForCommands() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            speak("Speech recognition is not available right now.")
            return
        }

        runOnMain {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        showToast("Voice assistant listening...")
                    }

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() = Unit

                    override fun onError(error: Int) {
                        Log.w(TAG, "Voice assistant recognition error: $error")
                        if (!shouldKeepListening()) return
                        if (error == SpeechRecognizer.ERROR_CLIENT) return
                        scheduleListeningRestart(
                            delayMs = if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                                150
                            } else {
                                350
                            }
                        )
                    }

                    override fun onResults(results: Bundle?) {
                        val recognizedCandidates = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?: arrayListOf()

                        val recognizedText = selectBestRecognitionCandidate(recognizedCandidates)

                        if (normalizedText(recognizedText).length < MIN_VALID_TEXT_LENGTH) {
                            if (shouldKeepListening()) {
                                scheduleListeningRestart()
                            }
                            return
                        }

                        Log.d(TAG, "Recognized voice command: $recognizedText")
                        processRecognizedText(recognizedText)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()

                        if (normalizedText(partial).length >= 4) {
                            Log.v(TAG, "Partial recognition: $partial")
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 700L)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start voice assistant", e)
                if (shouldKeepListening()) {
                    speak("I could not start voice assistant.") {
                        scheduleListeningRestart()
                    }
                }
            }
        }
    }

    private fun processRecognizedText(recognizedText: String) {
        stopListening()

        val normalized = normalizedText(recognizedText)
        if (normalized.length < MIN_VALID_TEXT_LENGTH) {
            resumeListeningIfNeeded()
            return
        }

        val text = stripWakePrefix(normalized)

        if (containsAnyPhrase(text, STOP_LISTENING_PHRASES)) {
            isVoiceAssistantEnabled = false
            awaitingActivationResponse = false
            awaitingSosConfirmation = false
            pendingSosContactName = null
            awaitingNavigationConfirmation = false
            pendingNavigationRoute = null
            speak("Voice assistant stopped.")
            return
        }

        if (containsAnyPhrase(text, START_LISTENING_PHRASES)) {
            isVoiceAssistantEnabled = true
            awaitingActivationResponse = false
            awaitingSosConfirmation = false
            pendingSosContactName = null
            speak("Voice assistant enabled.") {
                resumeListeningIfNeeded()
            }
            return
        }

        if (awaitingActivationResponse) {
            handleActivationResponse(text)
            return
        }

        if (awaitingSosConfirmation) {
            handleSosConfirmation(text)
            return
        }

        if (awaitingNavigationConfirmation) {
            handleNavigationConfirmation(text)
            return
        }

        if (!isVoiceAssistantEnabled) {
            return
        }

        when {
            isLocationIntent(text) -> {
                handleWhereAmI()
            }

            isNavigationIntent(text) -> {
                val destination = extractDestination(text)
                startVoiceNavigation(destination)
            }

            isSosIntent(text) -> {
                handleSosCommand(text)
            }

            else -> speak("I understood your speech, but not the command.") {
                resumeListeningIfNeeded()
            }
        }
    }

    private fun handleActivationResponse(recognizedText: String) {
        val affirmative = listOf("yes", "yeah", "yep", "enable")
        val negative = listOf("no", "nope", "not now", "disable")

        when {
            affirmative.any { recognizedText.contains(it) } -> {
                awaitingActivationResponse = false
                isVoiceAssistantEnabled = true
                speak("Voice assistant enabled. You can say commands like send help, where am I, or directions to a location.") {
                    resumeListeningIfNeeded()
                }
            }

            negative.any { recognizedText.contains(it) } -> {
                awaitingActivationResponse = false
                isVoiceAssistantEnabled = false
                speak("Voice assistant not enabled. Safety monitoring will continue.")
            }

            else -> {
                speak("Please say yes or no.") {
                    resumeListeningIfNeeded()
                }
            }
        }
    }

    private fun handleSosConfirmation(recognizedText: String) {
        val contactName = pendingSosContactName
        if (contactName.isNullOrBlank()) {
            awaitingSosConfirmation = false
            pendingSosContactName = null
            speak("SOS request expired.") {
                resumeListeningIfNeeded()
            }
            return
        }

        val affirmative = listOf("yes", "yeah", "confirm", "send", "go ahead")
        val negative = listOf("no", "cancel", "stop")

        when {
            affirmative.any { recognizedText.contains(it) } -> {
                awaitingSosConfirmation = false
                pendingSosContactName = null
                scope.launch {
                    val response = onSendSosToContact(contactName)
                    speak(response) {
                        resumeListeningIfNeeded()
                    }
                }
            }

            negative.any { recognizedText.contains(it) } -> {
                awaitingSosConfirmation = false
                pendingSosContactName = null
                speak("SOS cancelled.") {
                    resumeListeningIfNeeded()
                }
            }

            else -> {
                speak("Please say yes or no.") {
                    resumeListeningIfNeeded()
                }
            }
        }
    }

    private fun handleNavigationConfirmation(recognizedText: String) {
        val route = pendingNavigationRoute
        if (route == null) {
            awaitingNavigationConfirmation = false
            speak("Navigation request expired.") {
                resumeListeningIfNeeded()
            }
            return
        }

        val affirmative = listOf("yes", "yeah", "start", "go ahead")
        val negative = listOf("no", "cancel", "stop")

        when {
            affirmative.any { recognizedText.contains(it) } -> {
                awaitingNavigationConfirmation = false
                pendingNavigationRoute = null
                beginVoiceNavigation(route)
            }

            negative.any { recognizedText.contains(it) } -> {
                awaitingNavigationConfirmation = false
                pendingNavigationRoute = null
                speak("Navigation cancelled.") {
                    resumeListeningIfNeeded()
                }
            }

            else -> {
                speak("Please say yes or no.") {
                    resumeListeningIfNeeded()
                }
            }
        }
    }

    private fun handleSosCommand(recognizedText: String) {
        val contactName = extractContactName(recognizedText)
        if (contactName.isBlank()) {
            speak("I could not find that contact.") {
                resumeListeningIfNeeded()
            }
            return
        }

        awaitingSosConfirmation = true
        pendingSosContactName = contactName
        speak("Do you want to send an emergency SOS to ${contactName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}?") {
            resumeListeningIfNeeded()
        }
    }

    private fun extractContactName(command: String): String {
        val marker = SOS_CONTACT_MARKERS
            .map { "$it " }
            .firstOrNull { containsPhraseFuzzy(command, it.trim()) }

        if (marker != null) {
            val extracted = command.substringAfter(marker, "").trim()
            if (extracted.isNotBlank()) {
                return cleanName(extracted)
            }
        }

        var stripped = command
        SOS_INTENT_KEYWORDS.sortedByDescending { it.length }.forEach { keyword ->
            stripped = stripped.replace(keyword, " ")
        }

        val fallbackName = cleanName(stripped)
        return if (fallbackName in NON_NAME_WORDS || fallbackName.isBlank()) {
            ""
        } else {
            fallbackName
        }
    }

    private fun cleanName(value: String): String {
        return value
            .split(" ")
            .filter { token -> token.isNotBlank() && token !in NON_NAME_WORDS }
            .joinToString(" ")
            .trim()
    }

    private fun isLocationIntent(text: String): Boolean {
        if (containsAnyPhrase(text, LOCATION_PHRASES)) {
            return true
        }

        val hasWhere = text.contains("where")
        val hasLocation = text.contains("location")
        val hasAmI = text.contains("am i")
        return (hasWhere && hasAmI) || (hasLocation && hasAmI) || (hasWhere && hasLocation)
    }

    private fun isNavigationIntent(text: String): Boolean {
        return containsAnyPhrase(text, NAVIGATION_PHRASES)
    }

    private fun extractDestination(text: String): String {
        val markers = listOf(" to ", " for ", " towards ")
        for (marker in markers) {
            if (text.contains(marker)) {
                val destination = text.substringAfter(marker, "").trim()
                if (destination.isNotBlank()) {
                    return destination
                }
            }
        }

        var stripped = text
        NAVIGATION_PHRASES.sortedByDescending { it.length }.forEach { phrase ->
            stripped = stripped.replace(phrase, " ")
        }
        return stripped.trim()
    }

    private fun isSosIntent(text: String): Boolean {
        return SOS_INTENT_KEYWORDS.any { keyword -> containsPhraseFuzzy(text, keyword) }
    }

    private fun containsAnyPhrase(text: String, phrases: List<String>): Boolean {
        return phrases.any { phrase -> containsPhraseFuzzy(text, phrase) }
    }

    private fun containsPhraseFuzzy(text: String, phrase: String): Boolean {
        if (text.contains(phrase)) {
            return true
        }

        val textTokens = text.split(" ").filter { it.isNotBlank() }
        val phraseTokens = phrase.split(" ").filter { it.isNotBlank() }
        if (textTokens.isEmpty() || phraseTokens.isEmpty()) {
            return false
        }

        return phraseTokens.all { phraseToken ->
            textTokens.any { textToken -> isSimilarToken(textToken, phraseToken) }
        }
    }

    private fun isSimilarToken(a: String, b: String): Boolean {
        if (a == b) {
            return true
        }

        if (a.length >= 4 && b.length >= 4 && (a.startsWith(b) || b.startsWith(a))) {
            return true
        }

        if (kotlin.math.abs(a.length - b.length) > 1) {
            return false
        }

        return levenshteinDistance(a, b) <= 1
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val costs = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var previous = costs[0]
            costs[0] = i
            for (j in 1..b.length) {
                val temp = costs[j]
                val substitutionCost = if (a[i - 1] == b[j - 1]) 0 else 1
                costs[j] = minOf(
                    costs[j] + 1,
                    costs[j - 1] + 1,
                    previous + substitutionCost
                )
                previous = temp
            }
        }
        return costs[b.length]
    }

    private fun selectBestRecognitionCandidate(candidates: List<String>): String {
        if (candidates.isEmpty()) {
            return ""
        }

        if (candidates.size == 1) {
            return candidates.first()
        }

        return candidates.maxByOrNull { candidate -> scoreRecognitionCandidate(normalizedText(candidate)) }
            ?: candidates.first()
    }

    private fun scoreRecognitionCandidate(text: String): Int {
        if (text.length < MIN_VALID_TEXT_LENGTH) {
            return 0
        }

        var score = 1

        if (containsAnyPhrase(text, STOP_LISTENING_PHRASES)) score += 20
        if (containsAnyPhrase(text, START_LISTENING_PHRASES)) score += 12
        if (isLocationIntent(text)) score += 10
        if (isNavigationIntent(text)) score += 10
        if (isSosIntent(text)) score += 14

        if (awaitingActivationResponse || awaitingSosConfirmation || awaitingNavigationConfirmation) {
            val responseWords = listOf("yes", "no", "cancel", "confirm", "start", "stop")
            if (responseWords.any { text.contains(it) }) {
                score += 18
            }
        }

        if (text.length >= 6) {
            score += 2
        }

        return score
    }

    private fun normalizedText(input: String): String {
        return input
            .lowercase(Locale.getDefault())
            .replace("[^a-z0-9\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun stripWakePrefix(text: String): String {
        val wakePhrases = listOf("hey safe pulse", "safe pulse", "assistant")
        for (wakePhrase in wakePhrases) {
            if (text.startsWith("$wakePhrase ")) {
                return text.removePrefix(wakePhrase).trim()
            }
        }
        return text
    }

    private fun handleWhereAmI() {
        scope.launch {
            val currentLocation = getCurrentLocation() ?: run {
                speak("I cannot determine your location right now.") {
                    resumeListeningIfNeeded()
                }
                return@launch
            }

            val address = reverseGeocode(currentLocation)
            speak(buildLocationMessage(address, currentLocation)) {
                resumeListeningIfNeeded()
            }
        }
    }

    private fun startVoiceNavigation(destinationName: String) {
        if (destinationName.isBlank()) {
            speak("I could not find that location.") {
                resumeListeningIfNeeded()
            }
            return
        }

        scope.launch {
            val origin = getCurrentLocation() ?: run {
                speak("I cannot determine your location right now.") {
                    resumeListeningIfNeeded()
                }
                return@launch
            }

            val destinationAddress = geocodeDestination(destinationName) ?: run {
                speak("I could not find that location.") {
                    resumeListeningIfNeeded()
                }
                return@launch
            }

            val destination = LatLng(destinationAddress.latitude, destinationAddress.longitude)
            val resolvedName = destinationAddress.featureName
                ?: destinationAddress.locality
                ?: destinationName

            val route = safeRoutesRepository.getWalkingNavigationRoute(
                origin = origin,
                destination = destination,
                destinationName = resolvedName
            ) ?: run {
                speak("Navigation could not be started.") {
                    resumeListeningIfNeeded()
                }
                return@launch
            }

            pendingNavigationRoute = route
            awaitingNavigationConfirmation = true
            speak("Do you want to start navigation to ${route.destinationName}?") {
                resumeListeningIfNeeded()
            }
        }
    }

    private fun beginVoiceNavigation(route: VoiceNavigationRoute) {
        activeNavigationRoute = route
        currentStepIndex = 0
        startNavigationUpdates()

        val firstStep = route.steps.firstOrNull()?.let { formatStepInstruction(it, prefix = "") }
        val startMessage = buildString {
            append("Navigation started to ${route.destinationName}.")
            if (!firstStep.isNullOrBlank()) {
                append(' ')
                append(firstStep)
            }
        }
        speak(startMessage) {
            resumeListeningIfNeeded()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startNavigationUpdates() {
        if (!hasLocationPermission()) {
            speak("I cannot determine your location right now.") {
                resumeListeningIfNeeded()
            }
            return
        }

        stopNavigationLocationUpdates()

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            NAVIGATION_UPDATE_INTERVAL_MS
        ).setMinUpdateIntervalMillis(2000L)
            .setWaitForAccurateLocation(false)
            .build()

        navigationLocationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                handleNavigationLocation(LatLng(location.latitude, location.longitude))
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            navigationLocationCallback!!,
            Looper.getMainLooper()
        )
    }

    private fun handleNavigationLocation(userLocation: LatLng) {
        val route = activeNavigationRoute ?: return

        if (distanceBetweenMeters(userLocation, route.destination) < DESTINATION_REACHED_THRESHOLD_METERS) {
            speak("You have arrived at your destination.")
            stopNavigation()
            resumeListeningIfNeeded()
            return
        }

        val currentStep = route.steps.getOrNull(currentStepIndex) ?: return
        if (distanceBetweenMeters(userLocation, currentStep.endLocation) < STEP_REACHED_THRESHOLD_METERS) {
            currentStepIndex += 1
            val nextStep = route.steps.getOrNull(currentStepIndex)
            if (nextStep != null) {
                speak(formatStepInstruction(nextStep, prefix = "Next,")) {
                    resumeListeningIfNeeded()
                }
            }
        }
    }

    private fun stopNavigation() {
        stopNavigationLocationUpdates()
        activeNavigationRoute = null
        currentStepIndex = 0
    }

    private fun stopNavigationLocationUpdates() {
        navigationLocationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        navigationLocationCallback = null
    }

    private suspend fun getCurrentLocation(): LatLng? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext null
        }

        try {
            val location = fusedLocationClient.lastLocation.await() ?: return@withContext null
            LatLng(location.latitude, location.longitude)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current location", e)
            null
        }
    }

    private suspend fun reverseGeocode(location: LatLng): Address? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Reverse geocoding failed", e)
            null
        }
    }

    private suspend fun geocodeDestination(destinationName: String): Address? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(destinationName, 1)?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Forward geocoding failed for $destinationName", e)
            null
        }
    }

    private fun buildLocationMessage(address: Address?, fallbackLocation: LatLng): String {
        if (address == null) {
            return "You are at latitude ${"%.5f".format(Locale.US, fallbackLocation.latitude)} and longitude ${"%.5f".format(Locale.US, fallbackLocation.longitude)}."
        }

        val primary = address.featureName
            ?: address.subLocality
            ?: address.thoroughfare
            ?: address.locality
        val city = address.locality ?: address.subAdminArea ?: address.adminArea

        return when {
            !primary.isNullOrBlank() && !city.isNullOrBlank() -> "You are near $primary, $city."
            !primary.isNullOrBlank() -> "You are near $primary."
            !city.isNullOrBlank() -> "You are in $city."
            else -> "I cannot determine your exact place name right now."
        }
    }

    private fun formatStepInstruction(step: VoiceNavigationStep, prefix: String): String {
        val distanceText = when {
            step.distanceMeters >= 1000 -> String.format(Locale.US, "%.1f kilometers", step.distanceMeters / 1000.0)
            step.distanceMeters > 0 -> "${step.distanceMeters} meters"
            else -> "a short distance"
        }

        return buildString {
            if (prefix.isNotBlank()) {
                append(prefix)
                append(' ')
            }
            append(step.instruction)
            if (!step.instruction.endsWith(".")) {
                append('.')
            }
            append(" Continue for ")
            append(distanceText)
            append('.')
        }
    }

    private fun speak(message: String, onDone: (() -> Unit)? = null) {
        pendingOnSpeechDone = onDone
        textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "voice_assistant_${System.currentTimeMillis()}"
        )
        showToast(message)
        Log.d(TAG, "Speaking: $message")
    }

    private fun stopListening() {
        runOnMain {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop speech recognizer", e)
            }
        }
    }

    private fun scheduleListeningRestart(delayMs: Long = 350L) {
        scope.launch {
            delay(delayMs)
            if (shouldKeepListening()) {
                startListeningForCommands()
            }
        }
    }

    private fun shouldKeepListening(): Boolean {
        return isVoiceAssistantEnabled || awaitingActivationResponse || awaitingNavigationConfirmation
    }

    private fun resumeListeningIfNeeded() {
        if (shouldKeepListening()) {
            startListeningForCommands()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    private fun showToast(message: String) {
        android.os.Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun distanceBetweenMeters(from: LatLng, to: LatLng): Float {
        val result = FloatArray(1)
        android.location.Location.distanceBetween(
            from.latitude,
            from.longitude,
            to.latitude,
            to.longitude,
            result
        )
        return result[0]
    }

    fun cleanup() {
        stopAssistantSession()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        pendingOnSpeechDone = null
        isInitialized = false
    }

    private val STOP_LISTENING_PHRASES = listOf(
        "close mic",
        "stop mic",
        "disable mic",
        "stop listening",
        "assistant stop",
        "turn off assistant",
        "stop voice assistant",
        "disable voice assistant"
    )

    private val START_LISTENING_PHRASES = listOf(
        "on mic"
    )

    private val LOCATION_PHRASES = listOf(
        "where am i",
        "my location",
        "current location",
        "tell me where i am",
        "what is my location",
        "check my location"
    )

    private val NAVIGATION_PHRASES = listOf(
        "directions to",
        "navigate to",
        "take me to",
        "path to",
        "show me path to",
        "how do i go to",
        "towards"
    )

    private val SOS_INTENT_KEYWORDS = listOf(
        "send help",
        "help me",
        "emergency",
        "sos",
        "call help",
        "send emergency",
        "alert"
    )

    private val SOS_CONTACT_MARKERS = listOf("to", "for", "contact", "notify")

    private val NON_NAME_WORDS = setOf(
        "please",
        "send",
        "help",
        "me",
        "emergency",
        "sos",
        "call",
        "alert",
        "message",
        "to",
        "for",
        "contact",
        "notify",
        "assistant",
        "safe",
        "pulse"
    )
}