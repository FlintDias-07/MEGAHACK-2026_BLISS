package com.safepulse.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safepulse.R
import com.safepulse.SafePulseApplication
import com.safepulse.data.db.entity.EmergencyContactEntity
import com.safepulse.data.db.entity.HotspotEntity
import com.safepulse.data.db.entity.UnsafeZoneEntity
import com.safepulse.data.prefs.UserPreferences
import com.safepulse.data.repository.EmergencyContactRepository
import com.safepulse.data.repository.HotspotRepository
import com.safepulse.data.repository.UnsafeZoneRepository
import com.safepulse.domain.model.Gender
import com.safepulse.ml.StubVoiceTriggerModule
import com.safepulse.ui.theme.*
import com.safepulse.utils.LocaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Settings ViewModel
data class SettingsState(
    val gender: Gender = Gender.UNSPECIFIED,
    val languageCode: String = LocaleManager.SYSTEM_LANGUAGE_CODE,
    val voiceTriggerEnabled: Boolean = false,
    val darkModeEnabled: Boolean = false,
    val contacts: List<EmergencyContactEntity> = emptyList(),
    val hotspots: List<HotspotEntity> = emptyList(),
    val unsafeZones: List<UnsafeZoneEntity> = emptyList()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SafePulseApplication
    private val userPreferences = UserPreferences(application)
    private val contactRepository = EmergencyContactRepository(app.database.emergencyContactDao())
    private val hotspotRepository = HotspotRepository(app.database.hotspotDao())
    private val unsafeZoneRepository = UnsafeZoneRepository(app.database.unsafeZoneDao())
    
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            val settings = userPreferences.userSettingsFlow.first()
            _state.value = _state.value.copy(
                gender = settings.gender,
                languageCode = userPreferences.appLanguageCodeFlow.first(),
                voiceTriggerEnabled = settings.voiceTriggerEnabled,
                darkModeEnabled = settings.darkModeEnabled
            )
        }
        
        viewModelScope.launch {
            contactRepository.getAllContacts().collect { contacts ->
                _state.value = _state.value.copy(contacts = contacts)
            }
        }
        
        viewModelScope.launch {
            hotspotRepository.getAllHotspots().collect { hotspots ->
                _state.value = _state.value.copy(hotspots = hotspots)
            }
        }
        
        viewModelScope.launch {
            unsafeZoneRepository.getAllUnsafeZones().collect { zones ->
                _state.value = _state.value.copy(unsafeZones = zones)
            }
        }
    }
    
    fun setVoiceTriggerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setVoiceTriggerEnabled(enabled)
            _state.value = _state.value.copy(voiceTriggerEnabled = enabled)
        }
    }
    
    fun setDarkModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setDarkModeEnabled(enabled)
            _state.value = _state.value.copy(darkModeEnabled = enabled)
        }
    }

    fun setAppLanguage(languageCode: String) {
        val normalized = LocaleManager.normalize(languageCode)
        LocaleManager.applyLanguage(normalized)
        viewModelScope.launch {
            userPreferences.setAppLanguageCode(normalized)
            _state.value = _state.value.copy(languageCode = normalized)
        }
    }
    
    fun addContact(name: String, phone: String, isPrimary: Boolean) {
        viewModelScope.launch {
            val contact = EmergencyContactEntity(
                name = name,
                phone = phone,
                isPrimary = isPrimary
            )
            contactRepository.insert(contact)
        }
    }
    
    fun deleteContact(contact: EmergencyContactEntity) {
        viewModelScope.launch {
            contactRepository.delete(contact)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToRiskMap: () -> Unit = {},
    onNavigateToUserManual: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val localizedGender = when (state.gender) {
        Gender.MALE -> stringResource(R.string.gender_male)
        Gender.FEMALE -> stringResource(R.string.gender_female)
        else -> stringResource(R.string.gender_other)
    }
    val systemDefaultLabel = stringResource(R.string.language_system_default)
    val selectedLanguageLabel = remember(state.languageCode) {
        LocaleManager.supportedLanguages.firstOrNull { it.code == state.languageCode }?.label ?: systemDefaultLabel
    }
    var showZonesDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Profile section
            item {
                SectionHeader(stringResource(R.string.settings_profile))
            }
            
            item {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Default.Person,
                        title = stringResource(R.string.settings_gender),
                        subtitle = localizedGender,
                        iconColor = PrimaryRed
                    )
                }
            }

            // Appearance section
            item {
                SectionHeader(stringResource(R.string.settings_appearance))
            }

            item {
                SettingsCard {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLanguageDialog = true }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = PrimaryRed)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_language), fontWeight = FontWeight.Medium)
                                Text(
                                    selectedLanguageLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = if (state.darkModeEnabled) WarningYellow else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.dark_mode), fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(R.string.dark_mode_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = state.darkModeEnabled,
                                onCheckedChange = { viewModel.setDarkModeEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }

            if (showLanguageDialog) {
                item {
                    AlertDialog(
                        onDismissRequest = { showLanguageDialog = false },
                        title = { Text(stringResource(R.string.settings_select_language)) },
                        text = {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(LocaleManager.supportedLanguages) { language ->
                                    val isSelected = language.code == state.languageCode
                                    TextButton(
                                        onClick = {
                                            viewModel.setAppLanguage(language.code)
                                            showLanguageDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = language.label,
                                                modifier = Modifier.weight(1f),
                                                color = if (isSelected) PrimaryRed else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryRed)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showLanguageDialog = false }) {
                                Text(stringResource(R.string.action_close))
                            }
                        }
                    )
                }
            }
            
            // Emergency Contacts section
            item {
                SectionHeader(stringResource(R.string.emergency_contacts))
            }
            
            item {
                SettingsCard {
                    Column {
                        if (state.contacts.isEmpty()) {
                            // Empty state
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.PersonOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.settings_no_contacts),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.settings_add_contacts_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        } else {
                            state.contacts.forEachIndexed { index, contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (contact.isPrimary) PrimaryRed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(contact.name, fontWeight = FontWeight.Medium)
                                            if (contact.isPrimary) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = PrimaryRed.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        stringResource(R.string.primary_badge),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = PrimaryRed,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            contact.phone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteContact(contact) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                if (index < state.contacts.size - 1) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                                }
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        
                        // Add Contact Button
                        var showAddContactDialog by remember { mutableStateOf(false) }
                        
                        TextButton(
                            onClick = { showAddContactDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.add_emergency_contact))
                        }
                        
                        if (showAddContactDialog) {
                            AddContactDialog(
                                onDismiss = { showAddContactDialog = false },
                                onAdd = { name, phone, isPrimary ->
                                    viewModel.addContact(name, phone, isPrimary)
                                    showAddContactDialog = false
                                }
                            )
                        }
                    }
                }
            }
            
            // Statistics section
            item {
                SectionHeader(stringResource(R.string.settings_statistics))
            }
            
            item {
                SettingsCard {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = PrimaryRed.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(stringResource(R.string.total_events), fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(R.string.settings_events_detected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                "6",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryRed
                            )
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = null,
                                tint = PrimaryRed.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(stringResource(R.string.sos_sent), fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(R.string.settings_emergency_messages_sent),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                "6",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryRed
                            )
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.People,
                                contentDescription = null,
                                tint = PrimaryRed.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(stringResource(R.string.emergency_contacts), fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(R.string.settings_contacts_configured, state.contacts.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                state.contacts.size.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryRed
                            )
                        }
                    }
                }
            }
            
            // Zones section
            item {
                SectionHeader(stringResource(R.string.settings_safety_zones))
            }
            
            item {
                SettingsCard {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.Warning,
                            title = stringResource(R.string.settings_accident_hotspots),
                            subtitle = stringResource(R.string.settings_zones_loaded, state.hotspots.size),
                            iconColor = RiskHigh
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        SettingsRow(
                            icon = Icons.Default.LocationOff,
                            title = stringResource(R.string.settings_unsafe_zones),
                            subtitle = stringResource(R.string.settings_zones_loaded, state.unsafeZones.size),
                            iconColor = WarningYellow
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        TextButton(
                            onClick = { onNavigateToRiskMap() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_view_on_risk_map))
                        }
                    }
                }
            }
            
            // About section
            item {
                SectionHeader(stringResource(R.string.settings_about))
            }
            
            item {
                SettingsCard {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.Info,
                            title = stringResource(R.string.app_version),
                            subtitle = "1.0.0 (Prototype)",
                            iconColor = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        SettingsRow(
                            icon = Icons.Default.Shield,
                            title = "SafePulse",
                            subtitle = stringResource(R.string.settings_offline_first),
                            iconColor = PrimaryRed
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = SafeGreen
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_user_manual), fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(R.string.settings_user_manual_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(onClick = onNavigateToUserManual) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = stringResource(R.string.settings_open_user_manual),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Zone Details Dialog
    if (showZonesDialog) {
        ZoneDetailsDialog(
            hotspots = state.hotspots,
            unsafeZones = state.unsafeZones,
            onDismiss = { showZonesDialog = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconColor)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ZoneDetailsDialog(
    hotspots: List<HotspotEntity>,
    unsafeZones: List<UnsafeZoneEntity>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_safety_zones)) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.settings_accident_hotspots),
                        fontWeight = FontWeight.Bold,
                        color = RiskHigh
                    )
                }
                items(hotspots) { hotspot ->
                    Text(
                        "• ${String.format("%.4f", hotspot.lat)}, ${String.format("%.4f", hotspot.lng)} - Risk: ${(hotspot.baseRisk * 100).toInt()}% (${hotspot.roadType})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_unsafe_zones),
                        fontWeight = FontWeight.Bold,
                        color = WarningYellow
                    )
                }
                items(unsafeZones) { zone ->
                    Text(
                        "• ${String.format("%.4f", zone.lat)}, ${String.format("%.4f", zone.lng)} - Crime: ${(zone.crimeScore * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, phone: String, isPrimary: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var isPrimary by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_emergency_contact)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.contact_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorMessage != null && name.isBlank()
                )
                
                OutlinedTextField(
                    value = phone,
                    onValueChange = { newValue ->
                        // Only allow digits, max 10 characters
                        if (newValue.all { it.isDigit() } && newValue.length <= 10) {
                            phone = newValue
                            errorMessage = null
                        }
                    },
                    label = { Text(stringResource(R.string.contact_phone_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = phone.isNotEmpty() && phone.length != 10,
                    supportingText = {
                        val color = if (phone.length == 10) SafeGreen else if (phone.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.error
                        Text(
                            "${phone.length}/10 digits",
                            color = color,
                            fontWeight = if (phone.length == 10) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isPrimary,
                        onCheckedChange = { isPrimary = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.extracted_set_as_primary_contact))
                }
                
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            val isValid = name.isNotBlank() && phone.length == 10 && phone.all { it.isDigit() }
            
            TextButton(
                onClick = {
                    when {
                        name.isBlank() -> errorMessage = "Please enter a name"
                        phone.isBlank() -> errorMessage = "Please enter a phone number"
                        phone.length != 10 -> errorMessage = "Phone number must be exactly 10 digits"
                        !phone.all { it.isDigit() } -> errorMessage = "Phone number must contain only digits"
                        else -> onAdd(name.trim(), phone.trim(), isPrimary)
                    }
                },
                enabled = isValid
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
