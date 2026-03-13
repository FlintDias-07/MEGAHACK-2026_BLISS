package com.safepulse.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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

private data class TutorialStep(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

private val tutorialSteps = listOf(
    TutorialStep(
        id = "sos_button",
        title = "SOS Button",
        description = "Press and hold this button to immediately send an SOS alert with your location to all emergency contacts.",
        icon = Icons.Default.Warning
    ),
    TutorialStep(
        id = "safety_status",
        title = "Safety Status",
        description = "This shows your current safety mode. It switches automatically based on your location and sensor data.",
        icon = Icons.Default.Shield
    ),
    TutorialStep(
        id = "quick_actions",
        title = "Quick Actions",
        description = "Use these shortcuts to quickly access Fake Call, Silent Alert, and other safety features.",
        icon = Icons.Default.Bolt
    ),
    TutorialStep(
        id = "risk_map",
        title = "Risk Map",
        description = "View a heatmap of accident hotspots and unsafe zones near you for safer navigation.",
        icon = Icons.Default.Map
    ),
    TutorialStep(
        id = "safe_routes",
        title = "Safe Routes",
        description = "Plan routes that avoid high-risk areas. Always prefer safety over speed.",
        icon = Icons.Default.Route
    )
)

@Composable
fun OnboardingOverlayScreen(onComplete: () -> Unit) {
    var currentStep by remember { mutableStateOf(0) }
    val step = tutorialSteps[currentStep]
    val isLastStep = currentStep == tutorialSteps.lastIndex

    // Semi-transparent dark overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = false, onClick = {}) // Block touches from passing through
    ) {
        // Tutorial card centered on screen
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Step indicator dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tutorialSteps.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == currentStep) 10.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index <= currentStep)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = step.icon,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Description
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Navigation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Skip button
                        TextButton(onClick = onComplete) {
                            Text(
                                text = "Skip",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Back button
                            if (currentStep > 0) {
                                OutlinedButton(
                                    onClick = { currentStep-- },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Next / Done button
                            Button(
                                onClick = {
                                    if (isLastStep) onComplete()
                                    else currentStep++
                                }
                            ) {
                                Text(if (isLastStep) "Done" else "Next")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    if (isLastStep) Icons.Default.Check else Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Step counter in top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "${currentStep + 1} / ${tutorialSteps.size}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
