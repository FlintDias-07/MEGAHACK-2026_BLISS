package com.safepulse.wear.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.safepulse.wear.presentation.WearHomeViewModel
import com.safepulse.wear.ui.theme.*

/**
 * Quick actions screen providing one-tap access to:
 * - Silent Alert (SMS only, no sound)
 * - Fake Call (triggers on phone)
 * - Share Location
 */
@Composable
fun WearQuickActionsScreen(
    viewModel: WearHomeViewModel,
    onBack: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    var silentAlertSent by remember { mutableStateOf(false) }
    var fakeCallSent by remember { mutableStateOf(false) }
    var locationShared by remember { mutableStateOf(false) }

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
            // Title
            item {
                ListHeader {
                    Text(
                        text = "Quick Actions",
                        fontSize = 14.sp,
                        color = MaterialTheme.colors.primary
                    )
                }
            }

            // Silent Alert
            item {
                Chip(
                    onClick = {
                        viewModel.triggerSilentAlert()
                        silentAlertSent = true
                    },
                    label = {
                        Text(
                            text = if (silentAlertSent) "✓ Alert Sent" else "Silent Alert",
                            fontSize = 13.sp
                        )
                    },
                    secondaryLabel = {
                        Text("SMS only, no sound", fontSize = 10.sp)
                    },
                    colors = if (silentAlertSent) {
                        ChipDefaults.chipColors(backgroundColor = SafeGreen.copy(alpha = 0.3f))
                    } else {
                        ChipDefaults.chipColors(backgroundColor = WarningYellow.copy(alpha = 0.3f))
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }

            // Fake Call
            item {
                Chip(
                    onClick = {
                        viewModel.triggerFakeCall()
                        fakeCallSent = true
                    },
                    label = {
                        Text(
                            text = if (fakeCallSent) "✓ Call Triggered" else "Fake Call",
                            fontSize = 13.sp
                        )
                    },
                    secondaryLabel = {
                        Text("Incoming call on phone", fontSize = 10.sp)
                    },
                    colors = if (fakeCallSent) {
                        ChipDefaults.chipColors(backgroundColor = SafeGreen.copy(alpha = 0.3f))
                    } else {
                        ChipDefaults.secondaryChipColors()
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }

            // Share Location
            item {
                Chip(
                    onClick = {
                        viewModel.shareLocation()
                        locationShared = true
                    },
                    label = {
                        Text(
                            text = if (locationShared) "✓ Location Shared" else "Share Location",
                            fontSize = 13.sp
                        )
                    },
                    secondaryLabel = {
                        Text("Send via phone", fontSize = 10.sp)
                    },
                    colors = if (locationShared) {
                        ChipDefaults.chipColors(backgroundColor = SafeGreen.copy(alpha = 0.3f))
                    } else {
                        ChipDefaults.secondaryChipColors()
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }
        }
    }
}
