package com.safepulse.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite keyword spotting implementation.
 * Uses a CNN trained on Audio_Dataset3 to detect emergency phrases:
 * "help me", "send help", "i need help", "mujhe bachao", "madat karo", etc.
 */
class TFLiteVoiceTriggerModule(private val context: Context) : VoiceTriggerModule {

    companion object {
        private const val TAG = "TFLiteVoiceTrigger"
        private const val MODEL_PATH  = "keyword_spotting_model.tflite"
        private const val LABELS_PATH = "keyword_labels.txt"

        // Audio config — must match training script
        private const val SAMPLE_RATE   = 16000
        private const val DURATION_SEC  = 1.0
        private const val INPUT_SAMPLES = 16000   // 1 second at 16kHz
        private const val N_MFCC        = 40
        private const val N_FRAMES      = 32

        // Detection config
        private const val CONFIDENCE_THRESHOLD = 0.80f
        private const val TRIGGER_COOLDOWN_MS  = 3000L

        // Classes that should NOT trigger SOS (background/silence)
        private val NON_TRIGGER_LABELS = setOf("background", "silence", "noise", "_background_noise_")
    }

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private val audioRecorder = AudioRecorder()
    private var listening = false
    private var processingScope: CoroutineScope? = null
    private var lastTriggerTime = 0L

    private val _keywordDetected = MutableStateFlow(VoiceDetectionResult(false, null, 0f))

    private val audioBuffer = mutableListOf<Short>()
    private val bufferLock  = Any()

    init {
        loadModel()
    }

    // ── Model loading ────────────────────────────────────────────────────────

    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile()
            interpreter = Interpreter(modelBuffer, Interpreter.Options().apply { setNumThreads(2) })
            labels = loadLabels()
            Log.i(TAG, "✅ Keyword spotting model loaded. Classes: $labels")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Model not found — falling back to energy detection. Train model first!", e)
            interpreter = null
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_PATH)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length
        )
    }

    private fun loadLabels(): List<String> {
        return context.assets.open(LABELS_PATH)
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }
    }

    // ── Listening ────────────────────────────────────────────────────────────

    override fun startListening() {
        if (listening) return
        listening = true
        processingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val mode = if (interpreter != null) "ML keyword spotting" else "energy fallback"
        Log.i(TAG, "🎤 Voice trigger started — mode: $mode")

        audioRecorder.startRecording(processingScope!!, bufferSize = 512)
        if (!audioRecorder.isRecording()) {
            Log.e(TAG, "❌ AudioRecorder failed. Check RECORD_AUDIO permission.")
            listening = false; processingScope?.cancel(); return
        }

        processingScope!!.launch {
            audioRecorder.audioDataFlow.collect { chunk -> processChunk(chunk) }
        }
    }

    override fun stopListening() {
        if (!listening) return
        listening = false
        audioRecorder.stopRecording()
        processingScope?.cancel(); processingScope = null
        synchronized(bufferLock) { audioBuffer.clear() }
        _keywordDetected.value = VoiceDetectionResult(false, null, 0f)
        Log.d(TAG, "🔇 Stopped listening")
    }

    override fun isListening(): Boolean = listening
    override fun keywordDetectedFlow(): Flow<VoiceDetectionResult> = _keywordDetected.asStateFlow()
    override fun getSupportedKeywords(): List<String> = labels.ifEmpty { listOf("loud_voice") }

    // ── Audio processing ─────────────────────────────────────────────────────

    private fun processChunk(chunk: ShortArray) {
        synchronized(bufferLock) {
            audioBuffer.addAll(chunk.toList())
            if (audioBuffer.size >= INPUT_SAMPLES) {
                val samples = audioBuffer.take(INPUT_SAMPLES).toShortArray()
                // 50% overlap sliding window
                repeat(INPUT_SAMPLES / 2) { audioBuffer.removeAt(0) }
                runInference(samples)
            }
        }
    }

    private fun runInference(samples: ShortArray) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < TRIGGER_COOLDOWN_MS) return

        try {
            if (interpreter != null) {
                runMLInference(samples, now)
            } else {
                runEnergyFallback(samples, now)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}", e)
        }
    }

    // ── ML inference (TFLite model) ───────────────────────────────────────────

    private fun runMLInference(samples: ShortArray, now: Long) {
        // 1. Compute MFCC — simplified: use normalised audio energy per frame
        //    (Full MFCC requires a native lib; this approximation works for the CNN trained above)
        val normalized = AudioPreprocessor.normalizeAudio(samples)
        val mfcc = AudioPreprocessor.computeMFCC(normalized, SAMPLE_RATE, N_MFCC, N_FRAMES)

        // 2. Build input tensor: shape [1, N_FRAMES, N_MFCC, 1]
        val inputBuffer = ByteBuffer.allocateDirect(1 * N_FRAMES * N_MFCC * 4)
            .order(ByteOrder.nativeOrder())
        mfcc.forEach { frame -> frame.forEach { v -> inputBuffer.putFloat(v) } }
        inputBuffer.rewind()

        // 3. Run model
        val output = Array(1) { FloatArray(labels.size) }
        interpreter!!.run(inputBuffer, output)
        val scores = output[0]

        // 4. Find best class
        val maxIdx   = scores.indices.maxByOrNull { scores[it] } ?: return
        val maxScore = scores[maxIdx]
        val label    = labels.getOrElse(maxIdx) { "unknown" }

        Log.d(TAG, "🧠 Best: $label (${(maxScore * 100).toInt()}%)")

        if (maxScore >= CONFIDENCE_THRESHOLD && label !in NON_TRIGGER_LABELS) {
            triggerDetected(label, maxScore, now)
        }
    }

    // ── Energy fallback (when model not available) ────────────────────────────

    private val energyHistory = mutableListOf<Float>()
    private val ENERGY_THRESHOLD     = 0.15f
    private val MIN_ENERGY_SAMPLES   = 5

    private fun runEnergyFallback(samples: ShortArray, now: Long) {
        val energy = AudioPreprocessor.calculateRMS(AudioPreprocessor.normalizeAudio(samples))
        energyHistory.add(energy)
        if (energyHistory.size > 10) energyHistory.removeAt(0)

        if (energyHistory.size >= MIN_ENERGY_SAMPLES) {
            val avg  = energyHistory.average().toFloat()
            val peak = energyHistory.maxOrNull() ?: 0f
            if (avg >= ENERGY_THRESHOLD && peak >= ENERGY_THRESHOLD * 1.2f) {
                triggerDetected("loud_voice", avg / ENERGY_THRESHOLD, now)
                energyHistory.clear()
            }
        }
    }

    // ── Trigger emission ──────────────────────────────────────────────────────

    private fun triggerDetected(keyword: String, confidence: Float, now: Long) {
        lastTriggerTime = now
        Log.i(TAG, "🚨🚨🚨 KEYWORD DETECTED: $keyword (${(confidence * 100).toInt()}%) 🚨🚨🚨")
        _keywordDetected.value = VoiceDetectionResult(true, keyword, confidence)
        processingScope?.launch {
            delay(TRIGGER_COOLDOWN_MS)
            _keywordDetected.value = VoiceDetectionResult(false, null, 0f)
        }
    }

    fun release() {
        stopListening()
        interpreter?.close()
        interpreter = null
    }
}
