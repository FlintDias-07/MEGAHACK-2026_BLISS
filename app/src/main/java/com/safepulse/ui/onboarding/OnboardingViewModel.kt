package com.safepulse.ui.onboarding

import android.app.Application
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safepulse.SafePulseApplication
import com.safepulse.data.prefs.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    
    private val app = application as SafePulseApplication
    private val userPreferences = UserPreferences(application)
    
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false
    
    private val steps = OnboardingSteps.steps
    
    private val _state = MutableStateFlow(
        OnboardingState(
            currentStepIndex = 0,
            totalSteps = steps.size,
            isSpeaking = false,
            ttsAvailable = false
        )
    )
    val state: StateFlow<OnboardingState> = _state.asStateFlow()
    
    init {
        initializeTextToSpeech()
    }
    
    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("OnboardingVM", "TTS: Language not supported")
                    _state.value = _state.value.copy(ttsAvailable = false)
                } else {
                    ttsInitialized = true
                    _state.value = _state.value.copy(ttsAvailable = true)
                    
                    // Set up utterance listener
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _state.value = _state.value.copy(isSpeaking = true)
                        }
                        
                        override fun onDone(utteranceId: String?) {
                            _state.value = _state.value.copy(isSpeaking = false)
                            
                            // Auto-advance to next step after speaking completes
                            // But not on the last step
                            if (!_state.value.isLastStep) {
                                // Give a small delay before auto-advancing
                                viewModelScope.launch {
                                    kotlinx.coroutines.delay(1500) // 1.5 second pause
                                    if (!_state.value.isLastStep) {
                                        _state.value = _state.value.copy(
                                            currentStepIndex = _state.value.currentStepIndex + 1
                                        )
                                        updateActiveTarget()
                                        speakCurrentStep()
                                    }
                                }
                            }
                        }
                        
                        override fun onError(utteranceId: String?) {
                            _state.value = _state.value.copy(isSpeaking = false)
                            Log.e("OnboardingVM", "TTS error for utterance: $utteranceId")
                        }
                    })
                    
                    // Speak the first step automatically
                    speakCurrentStep()
                }
            } else {
                Log.e("OnboardingVM", "TTS initialization failed")
                _state.value = _state.value.copy(ttsAvailable = false)
            }
        }
    }
    
    fun getCurrentStep(): OnboardingStep {
        return steps[_state.value.currentStepIndex]
    }
    
    fun nextStep() {
        stopSpeaking()
        if (!_state.value.isLastStep) {
            _state.value = _state.value.copy(
                currentStepIndex = _state.value.currentStepIndex + 1
            )
            updateActiveTarget()
            speakCurrentStep()
        }
    }
    
    fun previousStep() {
        stopSpeaking()
        if (!_state.value.isFirstStep) {
            _state.value = _state.value.copy(
                currentStepIndex = _state.value.currentStepIndex - 1
            )
            updateActiveTarget()
            speakCurrentStep()
        }
    }
    
    fun showSkipDialog() {
        _state.value = _state.value.copy(showSkipDialog = true)
    }
    
    fun hideSkipDialog() {
        _state.value = _state.value.copy(showSkipDialog = false)
    }
    
    fun skipOnboarding() {
        stopSpeaking()
        completeOnboarding()
    }
    
    fun completeOnboarding() {
        stopSpeaking()
        TutorialTargetRegistry.setActiveTarget(null)
        viewModelScope.launch {
            userPreferences.setOnboardingTutorialComplete(true)
        }
    }
    
    private fun speakCurrentStep() {
        if (ttsInitialized && _state.value.ttsAvailable) {
            val step = getCurrentStep()
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, step.id)
            
            textToSpeech?.speak(
                step.voiceText,
                TextToSpeech.QUEUE_FLUSH,
                params,
                step.id
            )
        }
    }
    
    fun stopSpeaking() {
        if (textToSpeech?.isSpeaking == true) {
            textToSpeech?.stop()
        }
        _state.value = _state.value.copy(isSpeaking = false)
    }
    
    fun replaySpeech() {
        speakCurrentStep()
    }
    
    override fun onCleared() {
        super.onCleared()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
    
    private fun updateActiveTarget() {
        TutorialTargetRegistry.setActiveTarget(getCurrentStep().targetComponentId)
    }
}
