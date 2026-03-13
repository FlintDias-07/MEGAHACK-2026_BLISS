package com.safepulse.ui.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safepulse.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun OnboardingOverlayScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val currentStep = viewModel.getCurrentStep()
    
    // Get component bounds from registry
    val componentBounds = TutorialTargetRegistry.componentBounds
    val targetBounds = currentStep.targetComponentId?.let { componentBounds[it] }
    
    // Pulsing animation for highlight ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                // Tapping anywhere shows skip confirmation
                viewModel.showSkipDialog()
            }
    ) {
        // Highlight ring around target component
        if (targetBounds != null) {
            Box(
                modifier = Modifier
                    .offset(
                        x = (targetBounds.left - 8.dp.value).dp,
                        y = (targetBounds.top - 8.dp.value).dp
                    )
                    .size(
                        width = (targetBounds.width + 16.dp.value).dp,
                        height = (targetBounds.height + 16.dp.value).dp
                    )
                    .border(
                        width = (3.dp.value * pulseScale).dp,
                        color = PrimaryRed.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(20.dp)
                    )
            )
        }
        
        // Tooltip box positioned near target
        if (targetBounds != null) {
            // Position tooltip below the target component
            val tooltipY = targetBounds.bottom + 16.dp.value
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = tooltipY.dp)
                    .padding(horizontal = 20.dp)
            ) {
                // Arrow pointing up to the component
                Box(
                    modifier = Modifier
                        .padding(start = 40.dp)
                        .size(0.dp, 12.dp)
                        .background(
                            color = Color.White,
                            shape = GenericShape { size, _ ->
                                moveTo(size.width / 2, 0f)
                                lineTo(size.width, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                        )
                )
                
                // Tooltip card
                TooltipCard(
                    currentStep = currentStep,
                    state = state,
                    viewModel = viewModel,
                    onComplete = onComplete
                )
            }
        } else {
            // For steps without target (welcome, complete), show centered card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                TooltipCard(
                    currentStep = currentStep,
                    state = state,
                    viewModel = viewModel,
                    onComplete = onComplete
                )
            }
        }
        
        // Progress indicator at top
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        "Step ${state.currentStepIndex + 1} of ${state.totalSteps}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryRed
                    )
                }
                
                IconButton(
                    onClick = { viewModel.showSkipDialog() },
                    modifier = Modifier
                        .background(Color.White, CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Skip",
                        tint = PrimaryRed
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = PrimaryRed,
                trackColor = Color.White
            )
        }
    }
    
    // Skip confirmation dialog
    if (state.showSkipDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideSkipDialog() },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = WarningYellow
                )
            },
            title = { Text("Skip Tutorial?") },
            text = { 
                Text("Are you sure you want to skip the tutorial? You can always access the user manual from settings later.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.skipOnboarding()
                        onComplete()
                    }
                ) {
                    Text("Skip", color = PrimaryRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideSkipDialog() }) {
                    Text("Continue Tutorial")
                }
            }
        )
    }
}

@Composable
private fun TooltipCard(
    currentStep: OnboardingStep,
    state: OnboardingState,
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Title
            Text(
                text = currentStep.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryRed
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Description
            Text(
                text = currentStep.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Voice status indicator
            if (state.ttsAvailable) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (state.isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                        contentDescription = null,
                        tint = if (state.isSpeaking) SafeGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (state.isSpeaking) "Speaking..." else "Tap anywhere to skip",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.isSpeaking) SafeGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Replay button
                    if (!state.isSpeaking) {
                        IconButton(
                            onClick = { viewModel.replaySpeech() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Replay,
                                contentDescription = "Replay",
                                tint = PrimaryRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Previous button
                if (!state.isFirstStep) {
                    OutlinedButton(
                        onClick = { viewModel.previousStep() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Previous", style = MaterialTheme.typography.labelMedium)
                    }
                }
                
                // Next/Finish button
                Button(
                    onClick = {
                        if (state.isLastStep) {
                            viewModel.completeOnboarding()
                            onComplete()
                        } else {
                            viewModel.nextStep()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryRed
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (state.isLastStep) "Finish" else "Next",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        if (state.isLastStep) Icons.Default.Check else Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
