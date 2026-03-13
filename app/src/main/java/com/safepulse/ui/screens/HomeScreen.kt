package com.safepulse.ui.screens

import androidx.compose.ui.res.stringResource
import com.safepulse.R


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safepulse.domain.model.RiskLevel
import com.safepulse.domain.model.SafetyMode
import com.safepulse.ml.StubVoiceTriggerModule
import com.safepulse.ui.components.LiveMapCard
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import com.safepulse.ui.onboarding.TutorialTargetRegistry
import com.safepulse.ui.onboarding.tutorialTarget
import com.safepulse.ui.theme.*
import com.safepulse.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onNavigateToLogs: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSafeRoutes: () -> Unit = {},
    onNavigateToRiskMap: () -> Unit = {},
    onNavigateToEventLogs: () -> Unit = {},
    onNavigateToFullMap: () -> Unit = {},
    onNavigateToNearbySafety: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    
    // Auto-scroll for tutorial
    val requesters = remember {
        mapOf(
            "main_sos_button" to BringIntoViewRequester(),
            "voice_trigger_card" to BringIntoViewRequester(),
            "quick_actions" to BringIntoViewRequester(),
            "risk_map_card" to BringIntoViewRequester(),
            "safe_routes_card" to BringIntoViewRequester()
        )
    }
    
    val activeTargetId = TutorialTargetRegistry.activeTargetId.value
    LaunchedEffect(activeTargetId) {
        activeTargetId?.let { id ->
            requesters[id]?.bringIntoView()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = PrimaryRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.home_title),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToLogs) {
                        Icon(Icons.Default.History, contentDescription = "Event Logs")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RiskBadge(
                    riskLevel = state.riskLevel,
                    modifier = Modifier.weight(1f)
                )
                ModeBadge(
                    mode = state.safetyMode,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Live Map
            LiveMapCard(
                currentLocation = state.currentLocation,
                crimeHotspots = state.crimeHotspots,
                disasters = state.disasters,
                onLocationUpdate = { viewModel.updateLocation(it) },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Main SOS button
            SOSButton(
                isActive = state.isServiceRunning,
                onClick = { viewModel.toggleService() },
                modifier = Modifier
                    .tutorialTarget("main_sos_button")
                    .bringIntoViewRequester(requesters["main_sos_button"]!!)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = if (state.isServiceRunning) 
                    stringResource(R.string.home_protection_active)
                else 
                    stringResource(R.string.home_tap_to_start),
                style = MaterialTheme.typography.titleMedium,
                color = if (state.isServiceRunning) SafeGreen else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Voice Trigger section
            var showVoiceDemoDialog by remember { mutableStateOf(false) }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget("voice_trigger_card")
                    .bringIntoViewRequester(requesters["voice_trigger_card"]!!),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.voice_trigger),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryRed
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (state.voiceTriggerEnabled) SafeGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.event_voice_trigger), fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(R.string.voice_trigger_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = state.voiceTriggerEnabled,
                            onCheckedChange = { viewModel.setVoiceTriggerEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SafeGreen
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = { showVoiceDemoDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryRed
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.extracted_test_voice_trigger_demo))
                    }
                }
            }
            
            // Voice Demo Dialog
            if (showVoiceDemoDialog) {
                VoiceDemoDialog(
                    onDismiss = { showVoiceDemoDialog = false }
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Quick actions
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget("quick_actions")
                    .bringIntoViewRequester(requesters["quick_actions"]!!),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.extracted_quick_actions),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionButton(
                            text = stringResource(R.string.home_quick_action_manual_sos),
                            icon = Icons.Default.Sos,
                            color = DangerRed,
                            onClick = { viewModel.triggerManualSOS() },
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionButton(
                            text = stringResource(R.string.home_quick_action_fake_call),
                            icon = Icons.Default.PhoneInTalk,
                            color = Color(0xFF2196F3),
                            onClick = { viewModel.triggerFakeCall() },
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionButton(
                            text = stringResource(R.string.home_quick_action_silent_alert),
                            icon = Icons.Default.NotificationsOff,
                            color = Color(0xFF9C27B0),
                            onClick = { viewModel.triggerSilentAlert() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation cards: Risk Map & Safe Routes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToRiskMap() }
                        .tutorialTarget("risk_map_card")
                        .bringIntoViewRequester(requesters["risk_map_card"]!!),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3E0)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = "Risk Map",
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.extracted_risk_map),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFE65100)
                        )
                        Text(
                            stringResource(R.string.extracted_crime_disaster_zones),
                            fontSize = 10.sp,
                            color = Color(0xFFE65100).copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToSafeRoutes() }
                        .tutorialTarget("safe_routes_card")
                        .bringIntoViewRequester(requesters["safe_routes_card"]!!),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Directions,
                            contentDescription = "Safe Routes",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.extracted_safe_routes),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            stringResource(R.string.extracted_find_safest_path),
                            fontSize = 10.sp,
                            color = Color(0xFF2E7D32).copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Map card (full-screen Leaflet map)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToFullMap() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE3F2FD)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Explore,
                        contentDescription = "Interactive Map",
                        tint = Color(0xFF1565C0),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Interactive Map",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1565C0)
                        )
                        Text(
                            "Add markers, draw routes, explore",
                            fontSize = 11.sp,
                            color = Color(0xFF1565C0).copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF1565C0).copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Find Nearby Safety card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToNearbySafety() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE8EAF6)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocalPolice,
                        contentDescription = "Find Nearby Safety",
                        tint = Color(0xFF283593),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Find Nearby Safety",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF283593)
                        )
                        Text(
                            "Police, Hospitals, Safe Zones & Routes",
                            fontSize = 11.sp,
                            color = Color(0xFF283593).copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF283593).copy(alpha = 0.5f)
                    )
                }
            }
            
            // Footer info
            if (state.isServiceRunning) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
                    val alpha by pulseAnimation.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SafeGreen.copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.extracted_monitoring_in_background),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Bottom padding for scroll
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RiskBadge(riskLevel: RiskLevel, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        targetValue = when (riskLevel) {
            RiskLevel.LOW -> RiskLow
            RiskLevel.MEDIUM -> RiskMedium
            RiskLevel.HIGH -> RiskHigh
        },
        label = "riskColor"
    )
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.risk_level),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = riskLevel.name,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun ModeBadge(mode: SafetyMode, modifier: Modifier = Modifier) {
    val isHeightened = mode == SafetyMode.HEIGHTENED
    val color = if (isHeightened) WarningYellow else SafeGreen
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isHeightened) Icons.Default.ShieldMoon else Icons.Default.Shield,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.extracted_mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = mode.name,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun SOSButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sos")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sosScale"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (isActive) 0.5f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Outer glow
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(scale * 1.1f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                SafeGreen.copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        
        // Main button
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (isActive) listOf(
                            SafeGreen,
                            SafeGreen.copy(alpha = 0.7f)
                        ) else listOf(
                            PrimaryRed,
                            PrimaryRedDark
                        )
                    )
                )
                .clickable(onClick = onClick)
                .border(
                    width = 4.dp,
                    color = if (isActive) SafeGreen.copy(alpha = 0.5f) else PrimaryRed.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (isActive) Icons.Default.Shield else Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isActive) stringResource(R.string.home_sos_active) else stringResource(R.string.home_sos_start),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = PrimaryRed.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.linearGradient(listOf(color.copy(alpha = 0.5f), color.copy(alpha = 0.5f)))
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text, fontSize = 10.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceDemoDialog(onDismiss: () -> Unit) {
    var testInput by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    val voiceModule = remember { StubVoiceTriggerModule() }
    
    LaunchedEffect(Unit) {
        voiceModule.startListening()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.extracted_voice_trigger_demo)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.extracted_this_simulates_voice_keyword_detection_type_a_phrase_containing_trigger_words),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Keywords: \"help\", \"save me\", \"emergency\", \"bachao\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryRed
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = testInput,
                    onValueChange = { testInput = it },
                    label = { Text(stringResource(R.string.extracted_enter_phrase)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        val detected = voiceModule.processTextInput(testInput)
                        result = if (detected) "⚠️ KEYWORD DETECTED - SOS would trigger!" else "✓ No keyword detected"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.extracted_test))
                }
                
                result?.let { r ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = r,
                        fontWeight = FontWeight.Bold,
                        color = if (r.contains("DETECTED")) DangerRed else SafeGreen
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}
