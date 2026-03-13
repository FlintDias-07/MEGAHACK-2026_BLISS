package com.safepulse.ui.onboarding

import androidx.compose.ui.graphics.Color
import com.safepulse.ui.theme.*

/**
 * Represents a single step in the onboarding tutorial
 */
data class OnboardingStep(
    val id: String,
    val title: String,
    val description: String,
    val voiceText: String, // Text to be spoken by TTS
    val highlightColor: Color = PrimaryRed.copy(alpha = 0.3f),
    val targetComponentId: String? = null // ID of component to spotlight
)

/**
 * State for the onboarding flow
 */
data class OnboardingState(
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val isSpeaking: Boolean = false,
    val ttsAvailable: Boolean = true,
    val showSkipDialog: Boolean = false
) {
    val isFirstStep: Boolean get() = currentStepIndex == 0
    val isLastStep: Boolean get() = currentStepIndex == totalSteps - 1
    val progress: Float get() = if (totalSteps > 0) (currentStepIndex + 1).toFloat() / totalSteps else 0f
}

/**
 * Predefined onboarding steps for SafePulse app
 */
object OnboardingSteps {
    val steps = listOf(
        OnboardingStep(
            id = "welcome",
            title = "Welcome to SafePulse",
            description = "Your personal safety companion. Let me show you the key features that will keep you protected.",
            voiceText = "Welcome to Safe Pulse, your personal safety companion. Let me show you the key features that will keep you protected.",
            targetComponentId = null
        ),
        OnboardingStep(
            id = "main_button",
            title = "Protection Service",
            description = "This is your main protection button. Tap it to start continuous safety monitoring with location tracking and automatic alerts.",
            voiceText = "This is your main protection button. Tap it to start continuous safety monitoring with location tracking and automatic alerts.",
            targetComponentId = "main_sos_button"
        ),
        OnboardingStep(
            id = "voice_trigger",
            title = "Voice Trigger",
            description = "Toggle this switch to enable voice emergency. Just say 'Help' or 'Emergency' for hands-free SOS activation.",
            voiceText = "Here's the voice trigger feature. Toggle the switch to enable voice emergency. Just say help or emergency for hands-free SOS activation.",
            targetComponentId = "voice_trigger_card"
        ),
        OnboardingStep(
            id = "quick_actions",
            title = "Quick Emergency Actions",
            description = "Three quick options: Manual SOS for immediate alert, Fake Call to create an escape, and Silent Alert for discreet help.",
            voiceText = "These are your quick emergency actions. Manual SOS for immediate alert, Fake Call to create an escape, and Silent Alert for discreet help.",
            targetComponentId = "quick_actions"
        ),
        OnboardingStep(
            id = "risk_map",
            title = "Risk Map",
            description = "View crime hotspots and unsafe zones in your area. Tap to see danger zones marked in red and orange on the map.",
            voiceText = "The risk map shows crime hotspots and unsafe zones in your area. Tap it to see danger zones on the map.",
            targetComponentId = "risk_map_card"
        ),
        OnboardingStep(
            id = "safe_routes",
            title = "Safe Routes",
            description = "Plan the safest path to your destination. This feature calculates routes that avoid high-risk areas for your protection.",
            voiceText = "Use safe routes to plan the safest path to your destination. It calculates routes that avoid high-risk areas.",
            targetComponentId = "safe_routes_card"
        ),
        OnboardingStep(
            id = "complete",
            title = "You're Ready!",
            description = "You're all set to stay safe with SafePulse. Remember to add emergency contacts in settings for full protection.",
            voiceText = "You're all set! Safe Pulse is now ready to protect you. Remember to add emergency contacts in settings for full protection. Stay safe!",
            highlightColor = SafeGreen.copy(alpha = 0.3f),
            targetComponentId = null
        )
    )
}
