package com.safepulse.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.safepulse.wear.data.WearRiskLevel
import com.safepulse.wear.data.WearSafetyMode
import com.safepulse.wear.presentation.WearHomeViewModel
import com.safepulse.wear.ui.theme.*

/**
 * Main home screen for the Wear OS SafePulse app.
 * Shows: SOS button, safety status, heart rate, quick navigation chips.
 * Optimized for small round watch displays.
 */
@Composable
fun WearHomeScreen(
    viewModel: WearHomeViewModel,
    onNavigateToSos: () -> Unit,
    onNavigateToQuickActions: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ─── Connection Status ──────────────────
            item {
                ConnectionBadge(isConnected = state.isPhoneConnected)
            }

            // ─── SOS Button (prominent center) ─────
            item {
                SOSButton(onClick = onNavigateToSos)
            }

            // ─── Safety Status Card ─────────────────
            item {
                SafetyStatusCard(
                    riskLevel = state.safetyState.riskLevel,
                    safetyMode = state.safetyState.safetyMode,
                    isServiceRunning = state.isServiceRunning
                )
            }

            // ─── Heart Rate ─────────────────────────
            item {
                HeartRateDisplay(heartRate = state.heartRate)
            }

            // ─── Quick Actions Chip ─────────────────
            item {
                Chip(
                    onClick = onNavigateToQuickActions,
                    label = { Text("Quick Actions", fontSize = 13.sp) },
                    secondaryLabel = { Text("Silent Alert, Fake Call", fontSize = 10.sp) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }

            // ─── Contacts Chip ──────────────────────
            item {
                Chip(
                    onClick = onNavigateToContacts,
                    label = { Text("Emergency Contacts", fontSize = 13.sp) },
                    secondaryLabel = {
                        Text(
                            "${state.emergencyContacts.size} contacts",
                            fontSize = 10.sp
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }

            // ─── Service Toggle ─────────────────────
            item {
                ToggleChip(
                    checked = state.isServiceRunning,
                    onCheckedChange = { viewModel.toggleService() },
                    label = { Text("Monitoring", fontSize = 13.sp) },
                    toggleControl = {
                        Switch(
                            checked = state.isServiceRunning
                        )
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }

            // ─── Settings Chip ──────────────────────
            item {
                Chip(
                    onClick = onNavigateToSettings,
                    label = { Text("Settings", fontSize = 13.sp) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }
        }
    }
}

// ─── Sub-components ──────────────────────────────────────

@Composable
private fun ConnectionBadge(isConnected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isConnected) SafeGreen else Color.Gray)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isConnected) "Phone Connected" else "Phone Disconnected",
            fontSize = 10.sp,
            color = if (isConnected) SafeGreen else Color.Gray
        )
    }
}

@Composable
private fun SOSButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = DangerRed
        )
    ) {
        Text(
            text = "SOS",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SafetyStatusCard(
    riskLevel: WearRiskLevel,
    safetyMode: WearSafetyMode,
    isServiceRunning: Boolean
) {
    val (statusColor, statusText) = when (riskLevel) {
        WearRiskLevel.LOW -> Pair(RiskLow, "Low Risk")
        WearRiskLevel.MEDIUM -> Pair(RiskMedium, "Medium Risk")
        WearRiskLevel.HIGH -> Pair(RiskHigh, "High Risk")
    }

    val modeText = when (safetyMode) {
        WearSafetyMode.NORMAL -> "Normal"
        WearSafetyMode.HEIGHTENED -> "Heightened"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .clip(MaterialTheme.shapes.medium)
            .background(SurfaceDark)
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (isServiceRunning) "$modeText Mode • Active" else "Monitoring Off",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun HeartRateDisplay(heartRate: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .clip(MaterialTheme.shapes.medium)
            .background(SurfaceDark)
            .padding(vertical = 6.dp, horizontal = 12.dp)
    ) {
        Text(
            text = "♥",
            fontSize = 18.sp,
            color = HeartRateRed
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (heartRate > 0) {
            Text(
                text = "$heartRate",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "BPM",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        } else {
            Text(
                text = "-- BPM",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}
