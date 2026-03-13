package com.safepulse.ui.onboarding

import androidx.compose.ui.res.stringResource
import com.safepulse.R


import androidx.annotation.StringRes
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
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector
)

private val tutorialSteps = listOf(
    TutorialStep(
        id = "sos_button",
        titleRes = R.string.tutorial_sos_button_title,
        descriptionRes = R.string.tutorial_sos_button_description,
        icon = Icons.Default.Warning
    ),
    TutorialStep(
        id = "safety_status",
        titleRes = R.string.tutorial_safety_status_title,
        descriptionRes = R.string.tutorial_safety_status_description,
        icon = Icons.Default.Shield
    ),
    TutorialStep(
        id = "quick_actions",
        titleRes = R.string.tutorial_quick_actions_title,
        descriptionRes = R.string.tutorial_quick_actions_description,
        icon = Icons.Default.Bolt
    ),
    TutorialStep(
        id = "risk_map",
        titleRes = R.string.tutorial_risk_map_title,
        descriptionRes = R.string.tutorial_risk_map_description,
        icon = Icons.Default.Map
    ),
    TutorialStep(
        id = "safe_routes",
        titleRes = R.string.tutorial_safe_routes_title,
        descriptionRes = R.string.tutorial_safe_routes_description,
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
                        text = stringResource(step.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Description
                    Text(
                        text = stringResource(step.descriptionRes),
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
                                text = stringResource(R.string.extracted_skip),
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
                                        contentDescription = stringResource(R.string.action_back),
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
                                Text(if (isLastStep) stringResource(R.string.action_complete) else stringResource(R.string.action_next))
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
