package com.safepulse.ui.riskmap

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.*
import com.safepulse.domain.riskmap.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

// Risk level colors
private val HighRiskColor = Color(0xFFF44336)
private val MediumRiskColor = Color(0xFFFF9800)
private val LowRiskColor = Color(0xFF4CAF50)
private val FloodColor = Color(0xFF2196F3)
private val LandslideColor = Color(0xFF795548)
private val SafeRouteColor = Color(0xFF4CAF50)
private val UnsafeRouteColor = Color(0xFFF44336)
private val AltRouteColor = Color(0xFFFF9800)
private val PoliceColor = Color(0xFF1565C0)
private val HospitalColor = Color(0xFFD32F2F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskMapScreen(
    viewModel: RiskMapViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val locationRisk by viewModel.locationRisk.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val safeRoutes by viewModel.safeRoutes.collectAsState()
    val destination by viewModel.destination.collectAsState()
    val safetyPlaces by viewModel.safetyPlaces.collectAsState()
    val showSafetyPlaces by viewModel.showSafetyPlaces.collectAsState()

    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var showDestinationDialog by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(true) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) {
            fetchCurrentLocation(context) { loc ->
                viewModel.updateCurrentLocation(loc)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 12f))
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadRiskData()
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            fetchCurrentLocation(context) { loc ->
                viewModel.updateCurrentLocation(loc)
            }
        }
    }

    // Update map overlays when data or filter changes
    LaunchedEffect(uiState, selectedFilter, safeRoutes, destination, showSafetyPlaces, safetyPlaces) {
        val map = googleMap ?: return@LaunchedEffect
        val state = uiState as? RiskMapUiState.Success ?: return@LaunchedEffect
        drawRiskOverlays(
            map, state.riskData, selectedFilter, safeRoutes,
            currentLocation, destination,
            if (showSafetyPlaces) safetyPlaces else emptyList()
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Risk Map", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        locationRisk?.let { risk ->
                            Text(
                                text = "Risk Level: ${risk.riskLevel}",
                                fontSize = 12.sp,
                                color = when (risk.riskLevel) {
                                    "HIGH" -> HighRiskColor
                                    "MEDIUM" -> MediumRiskColor
                                    else -> LowRiskColor
                                }
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleSafetyPlaces() }) {
                        Icon(
                            Icons.Default.LocalHospital,
                            "Toggle Safety Places",
                            tint = if (showSafetyPlaces) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(onClick = { showDestinationDialog = true }) {
                        Icon(Icons.Default.Directions, "Safe Route")
                    }
                    IconButton(onClick = { showBottomSheet = !showBottomSheet }) {
                        Icon(Icons.Default.Info, "Risk Info")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (safeRoutes.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { viewModel.clearRoutes() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, "Clear Routes", modifier = Modifier.size(20.dp))
                    }
                }
                FloatingActionButton(
                    onClick = {
                        currentLocation?.let { loc ->
                            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 14f))
                        }
                    }
                ) {
                    Icon(Icons.Default.MyLocation, "My Location")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Map
            if (hasLocationPermission) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            onCreate(null)
                            onResume()
                            mapView = this
                            getMapAsync { map ->
                                googleMap = map
                                setupRiskMap(map, ctx, currentLocation)
                                currentLocation?.let {
                                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 12f))
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LocationOff, null, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Location permission required")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }) { Text("Grant Permission") }
                    }
                }
            }

            // Filter chips at top
            FilterChipsRow(
                selectedFilter = selectedFilter,
                onFilterChanged = { viewModel.setFilter(it) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )

            // Loading
            if (uiState is RiskMapUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // Bottom risk info panel
            AnimatedVisibility(
                visible = showBottomSheet && (locationRisk != null || safeRoutes.isNotEmpty()),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                RiskInfoPanel(
                    locationRisk = locationRisk,
                    safeRoutes = safeRoutes,
                    onRouteSelect = { route ->
                        googleMap?.let { map ->
                            if (route.waypoints.isNotEmpty()) {
                                val bounds = LatLngBounds.builder()
                                route.waypoints.forEach { bounds.include(it) }
                                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
                            }
                        }
                    }
                )
            }

            // Legend
            MapLegend(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 8.dp)
            )
        }
    }

    // Destination dialog
    if (showDestinationDialog) {
        DestinationInputDialog(
            onDismiss = { showDestinationDialog = false },
            onSearch = { destLat, destLng ->
                showDestinationDialog = false
                viewModel.searchSafeRoute(LatLng(destLat, destLng))
            },
            currentLocation = currentLocation
        )
    }

    DisposableEffect(Unit) {
        onDispose { mapView?.onDestroy() }
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: RiskFilter,
    onFilterChanged: (RiskFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RiskFilter.values().forEach { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterChanged(filter) },
                label = {
                    Text(
                        when (filter) {
                            RiskFilter.ALL -> "All Risks"
                            RiskFilter.CRIME_ONLY -> "🔴 Crime"
                            RiskFilter.DISASTER_ONLY -> "🌊 Disaster"
                        },
                        fontSize = 12.sp
                    )
                }
            )
        }
    }
}

@Composable
private fun MapLegend(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Legend", fontWeight = FontWeight.Bold, fontSize = 10.sp)
            LegendItem(HighRiskColor, "High Crime Risk")
            LegendItem(MediumRiskColor, "Medium Crime Risk")
            LegendItem(LowRiskColor, "Low Crime Risk")
            LegendItem(FloodColor, "Flood Risk")
            LegendItem(LandslideColor, "Landslide Risk")
            LegendItem(PoliceColor, "🚔 Police Station")
            LegendItem(HospitalColor, "🏥 Hospital")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.6f))
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun RiskInfoPanel(
    locationRisk: LocationRiskInfo?,
    safeRoutes: List<SafeRouteOption>,
    onRouteSelect: (SafeRouteOption) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .heightIn(max = 280.dp)
        ) {
            // Location risk summary
            locationRisk?.let { risk ->
                Text("📍 Your Area Risk Analysis", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RiskScoreChip("Crime", risk.crimeRisk, HighRiskColor)
                    RiskScoreChip("Disaster", risk.disasterRisk, FloodColor)
                    RiskScoreChip("Overall", risk.overallRisk,
                        when {
                            risk.overallRisk >= 0.7f -> HighRiskColor
                            risk.overallRisk >= 0.4f -> MediumRiskColor
                            else -> LowRiskColor
                        }
                    )
                }

                // Nearby zones
                if (risk.nearbyCrimeZones.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Nearby Crime Zones:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    risk.nearbyCrimeZones.take(3).forEach { zone ->
                        Text(
                            "• ${zone.city}: ${zone.totalCrimes} crimes (${(zone.crimeRiskScore * 100).toInt()}% risk)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (risk.nearbyDisasterZones.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Nearby Disaster Zones:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    risk.nearbyDisasterZones.take(3).forEach { zone ->
                        val factors = zone.riskFactors.take(2).joinToString(", ")
                        Text(
                            "• ${zone.city}: $factors",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Safe routes section
            if (safeRoutes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                Text("🛣️ Safe Route Suggestions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))

                safeRoutes.forEach { route ->
                    SafeRouteCard(route = route, onClick = { onRouteSelect(route) })
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun RiskScoreChip(label: String, score: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${(score * 100).toInt()}%",
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun SafeRouteCard(route: SafeRouteOption, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (route.isSafest)
                LowRiskColor.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (route.isSafest) {
                        Text("✅ ", fontSize = 14.sp)
                    }
                    Text(
                        route.name,
                        fontWeight = if (route.isSafest) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
                val routeColor = when {
                    route.totalRiskScore < 0.3f -> LowRiskColor
                    route.totalRiskScore < 0.6f -> MediumRiskColor
                    else -> HighRiskColor
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(routeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Risk: ${(route.totalRiskScore * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = routeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📏 ${String.format("%.1f", route.distanceKm)} km", fontSize = 11.sp)
                Text("🔴 Crime: ${(route.crimeRisk * 100).toInt()}%", fontSize = 11.sp)
                Text("🌊 Disaster: ${(route.disasterRisk * 100).toInt()}%", fontSize = 11.sp)
            }
            if (route.warnings.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                route.warnings.take(2).forEach { warning ->
                    Text(
                        warning,
                        fontSize = 10.sp,
                        color = MediumRiskColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationInputDialog(
    onDismiss: () -> Unit,
    onSearch: (Double, Double) -> Unit,
    currentLocation: LatLng?
) {
    var destinationText by remember { mutableStateOf("") }
    val presetDestinations = remember {
        listOf(
            "Delhi - Connaught Place" to LatLng(28.6315, 77.2167),
            "Mumbai - CST" to LatLng(19.0760, 72.8777),
            "Bangalore - MG Road" to LatLng(12.9758, 77.6045),
            "Chennai - T. Nagar" to LatLng(13.0418, 80.2341),
            "Hyderabad - Charminar" to LatLng(17.3616, 78.4747),
            "Kolkata - Park Street" to LatLng(22.5520, 88.3520),
            "Pune - Deccan" to LatLng(18.5167, 73.8415),
            "Jaipur - Pink City" to LatLng(26.9239, 75.8267),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🛣️ Find Safe Route", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Select a destination to get the safest route based on crime and disaster data:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                // Preset city destinations
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())
                ) {
                    presetDestinations.forEach { (name, latLng) ->
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSearch(latLng.latitude, latLng.longitude) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(name, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Also allow nearby offset destination
                currentLocation?.let {
                    Spacer(Modifier.height(8.dp))
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSearch(it.latitude + 0.02, it.longitude + 0.02)
                            },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.NearMe,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("📍 Nearby (~2km away)", fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// --- Map setup helpers ---

@SuppressLint("MissingPermission")
private fun setupRiskMap(
    map: GoogleMap,
    context: android.content.Context,
    currentLocation: LatLng?
) {
    map.uiSettings.apply {
        isZoomControlsEnabled = true
        isMyLocationButtonEnabled = false
        isCompassEnabled = true
        isMapToolbarEnabled = false
    }

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        == PackageManager.PERMISSION_GRANTED
    ) {
        map.isMyLocationEnabled = true
    }

    map.setMapStyle(
        MapStyleOptions("[{\"featureType\":\"poi\",\"stylers\":[{\"visibility\":\"off\"}]}]")
    )

    // Default to India view
    val indiaCenter = currentLocation ?: LatLng(20.5937, 78.9629)
    val zoom = if (currentLocation != null) 12f else 5f
    map.moveCamera(CameraUpdateFactory.newLatLngZoom(indiaCenter, zoom))
}

private fun drawRiskOverlays(
    map: GoogleMap,
    riskData: CombinedRiskData,
    filter: RiskFilter,
    safeRoutes: List<SafeRouteOption>,
    currentLocation: LatLng?,
    destination: LatLng?,
    safetyPlaces: List<SafetyPlace> = emptyList()
) {
    map.clear()

    // Draw crime risk circles
    if (filter == RiskFilter.ALL || filter == RiskFilter.CRIME_ONLY) {
        for (zone in riskData.crimeZones) {
            val color = when {
                zone.crimeRiskScore >= 0.5f -> 0x40F44336.toInt() // Red
                zone.crimeRiskScore >= 0.2f -> 0x40FF9800.toInt() // Orange
                else -> 0x404CAF50.toInt() // Green
            }
            val strokeColor = when {
                zone.crimeRiskScore >= 0.5f -> 0x80F44336.toInt()
                zone.crimeRiskScore >= 0.2f -> 0x80FF9800.toInt()
                else -> 0x804CAF50.toInt()
            }

            map.addCircle(
                CircleOptions()
                    .center(zone.location)
                    .radius(zone.radiusMeters.toDouble())
                    .fillColor(color)
                    .strokeColor(strokeColor)
                    .strokeWidth(2f)
            )

            // Add crime hotspot markers
            for (hotspot in zone.hotspots) {
                val hue = when {
                    hotspot.risk >= 0.7f -> BitmapDescriptorFactory.HUE_RED
                    hotspot.risk >= 0.4f -> BitmapDescriptorFactory.HUE_ORANGE
                    else -> BitmapDescriptorFactory.HUE_GREEN
                }
                map.addMarker(
                    MarkerOptions()
                        .position(hotspot.location)
                        .title("🔴 ${hotspot.label}")
                        .snippet("Crime Risk: ${(hotspot.risk * 100).toInt()}% | ${zone.city}")
                        .icon(BitmapDescriptorFactory.defaultMarker(hue))
                        .alpha(0.85f)
                )
            }
        }
    }

    // Draw disaster risk circles
    if (filter == RiskFilter.ALL || filter == RiskFilter.DISASTER_ONLY) {
        for (zone in riskData.disasterZones) {
            // Flood risk circle
            if (zone.floodRisk >= 0.4f) {
                map.addCircle(
                    CircleOptions()
                        .center(zone.location)
                        .radius(zone.radiusMeters.toDouble() * 0.8)
                        .fillColor(0x302196F3)
                        .strokeColor(0x602196F3)
                        .strokeWidth(1.5f)
                )
            }

            // Landslide risk circle
            if (zone.landslideRisk >= 0.4f) {
                map.addCircle(
                    CircleOptions()
                        .center(zone.location)
                        .radius(zone.radiusMeters.toDouble() * 0.6)
                        .fillColor(0x30795548)
                        .strokeColor(0x60795548)
                        .strokeWidth(1.5f)
                )
            }

            // Disaster marker
            if (zone.combinedDisasterRisk >= 0.4f) {
                val icon = when {
                    zone.floodRisk > zone.landslideRisk -> BitmapDescriptorFactory.HUE_AZURE
                    else -> BitmapDescriptorFactory.HUE_YELLOW
                }
                val typeLabel = when {
                    zone.floodRisk >= 0.5f && zone.landslideRisk >= 0.5f -> "🌊⛰️ Multi-hazard"
                    zone.floodRisk >= 0.5f -> "🌊 Flood Risk"
                    zone.landslideRisk >= 0.5f -> "⛰️ Landslide Risk"
                    else -> "⚠️ Disaster Risk"
                }
                map.addMarker(
                    MarkerOptions()
                        .position(zone.location)
                        .title("$typeLabel - ${zone.city}")
                        .snippet(zone.riskFactors.take(2).joinToString(" • "))
                        .icon(BitmapDescriptorFactory.defaultMarker(icon))
                        .alpha(0.8f)
                )
            }
        }
    }

    // Draw safe routes
    if (safeRoutes.isNotEmpty()) {
        safeRoutes.forEachIndexed { index, route ->
            val color = when {
                route.isSafest -> 0xFF4CAF50.toInt()
                route.totalRiskScore < 0.5f -> 0xFFFF9800.toInt()
                else -> 0xFFF44336.toInt()
            }
            map.addPolyline(
                PolylineOptions()
                    .addAll(route.waypoints)
                    .color(color)
                    .width(if (route.isSafest) 12f else 8f)
                    .zIndex(if (route.isSafest) 2f else 1f)
                    .pattern(if (route.isSafest) null else listOf(Dash(20f), Gap(10f)))
            )
        }

        // Destination marker
        destination?.let {
            map.addMarker(
                MarkerOptions()
                    .position(it)
                    .title("🎯 Destination")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
            )
        }

        // Zoom to fit all routes
        val bounds = LatLngBounds.builder()
        safeRoutes.flatMap { it.waypoints }.forEach { bounds.include(it) }
        currentLocation?.let { bounds.include(it) }
        try {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
        } catch (_: Exception) {}
    }

    // Draw police stations and hospitals with distinct custom icons
    for (place in safetyPlaces) {
        val icon = when (place.type) {
            SafetyPlaceType.POLICE -> createPoliceMarkerIcon()
            SafetyPlaceType.HOSPITAL -> createHospitalMarkerIcon()
        }
        val emoji = when (place.type) {
            SafetyPlaceType.POLICE -> "🚔"
            SafetyPlaceType.HOSPITAL -> "🏥"
        }
        val snippet = if (place.address.isNotEmpty() && place.phoneNumber.isNotEmpty())
            "${place.address} • ${place.phoneNumber}"
        else if (place.address.isNotEmpty()) place.address
        else "Police Station"
        map.addMarker(
            MarkerOptions()
                .position(place.location)
                .title("$emoji ${place.name}")
                .snippet(snippet)
                .icon(icon)
                .anchor(0.5f, 0.5f)
                .alpha(0.95f)
                .zIndex(3f)
        )
    }
}

/**
 * Creates a blue shield-shaped marker icon for police stations
 */
private fun createPoliceMarkerIcon(): BitmapDescriptor {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Blue filled shield/badge shape
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1565C0.toInt() // Dark blue
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    // Draw a shield-like rounded rect
    val rect = android.graphics.RectF(4f, 2f, 44f, 42f)
    canvas.drawRoundRect(rect, 8f, 8f, bgPaint)
    canvas.drawRoundRect(rect, 8f, 8f, borderPaint)

    // Draw "P" text in white
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("P", size / 2f, size / 2f + 9f, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/**
 * Creates a red cross marker icon for hospitals
 */
private fun createHospitalMarkerIcon(): BitmapDescriptor {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // White circle background
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD32F2F.toInt() // Dark red
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(size / 2f, size / 2f, 20f, bgPaint)
    canvas.drawCircle(size / 2f, size / 2f, 20f, borderPaint)

    // Draw red cross
    val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD32F2F.toInt()
        style = Paint.Style.FILL
    }
    // Vertical bar of cross
    canvas.drawRect(20f, 10f, 28f, 38f, crossPaint)
    // Horizontal bar of cross
    canvas.drawRect(10f, 20f, 38f, 28f, crossPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

@SuppressLint("MissingPermission")
private fun fetchCurrentLocation(
    context: android.content.Context,
    onLocation: (LatLng) -> Unit
) {
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    fusedClient.lastLocation.addOnSuccessListener { location ->
        location?.let {
            onLocation(LatLng(it.latitude, it.longitude))
        } ?: run {
            // Default to Delhi if location unavailable
            onLocation(LatLng(28.6139, 77.2090))
        }
    }.addOnFailureListener {
        onLocation(LatLng(28.6139, 77.2090))
    }
}