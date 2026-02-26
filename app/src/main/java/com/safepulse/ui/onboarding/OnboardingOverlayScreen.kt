package com.safepulse.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safepulse.ui.theme.PrimaryRed
import com.safepulse.ui.theme.SafeGreen
import kotlinx.coroutines.delay

/**
 * Voice-guided tutorial overlay that appears on top of the main navigation
 * after the initial onboarding (permissions/contacts) is completed.
 * Highlights key UI features one at a time.
 */
@Composable
fun OnboardingOverlayScreen(onComplete: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }

    val steps = remember {
        listOf(
            TutorialStep(
                title = "Welcome to SafePulse!",
                description = "Let's quickly walk through the main features so you feel safe and confident.",
                icon = Icons.Default.Shield
            ),
            TutorialStep(
                title = "SOS Button",
                description = "Tap the SOS button anytime to send an emergency alert with your location to all your contacts.",
                icon = Icons.Default.Warning
            ),
            TutorialStep(
                title = "Voice Trigger",
                description = "Say \"Help\" or \"Emergency\" and SafePulse will automatically trigger an SOS — even from your pocket.",
                icon = Icons.Default.Mic
            ),
            TutorialStep(
                title = "Shake to SOS",
                description = "Shake your phone 3 times rapidly to trigger an emergency alert when you can't reach the screen.",
                icon = Icons.Default.Vibration
            ),
            TutorialStep(
                title = "Silent Alert",
                description = "Use Silent Alert to discreetly send your location via SMS — no sound, no call.",
                icon = Icons.Default.VolumeOff
            ),
            TutorialStep(
                title = "You're All Set!",
                description = "SafePulse is now protecting you in the background. Stay safe!",
                icon = Icons.Default.CheckCircle
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { /* consume taps */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(28.dp)
        ) {
            val step = steps[currentStep]

            // Icon
            Icon(
                imageVector = step.icon,
                contentDescription = null,
                tint = if (currentStep == steps.lastIndex) SafeGreen else PrimaryRed,
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = step.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                text = step.description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Step dots
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentStep) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentStep) PrimaryRed
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Button
            if (currentStep < steps.lastIndex) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onComplete) {
                        Text("Skip")
                    }
                    Button(
                        onClick = { currentStep++ },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
                    ) {
                        Text("Next")
                    }
                }
            } else {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                ) {
                    Text("Get Started", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class TutorialStep(
    val title: String,
    val description: String,
    val icon: ImageVector
)
