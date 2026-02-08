package com.safepulse.ui.saferoutes

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import com.safepulse.domain.riskmap.SafetyPlace
import com.safepulse.domain.riskmap.SafetyPlaceType
import com.safepulse.domain.saferoutes.*
import com.safepulse.ui.components.DestinationSearchDialogNew
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlinx.coroutines.launch

/**
 * Safe Routes screen with Google Maps integration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeRoutesScreenWithMap(
    viewModel: SafeRoutesViewModel,
    onNavigateBack: () -> Unit,
    safetyPlaces: List<SafetyPlace> = emptyList()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val selectedRoute by viewModel.selectedRoute.collectAsState()
    
    var mapView: MapView? by remember { mutableStateOf(null) }
    var googleMap: GoogleMap? by remember { mutableStateOf(null) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (isGranted) {
            getCurrentLocation(context) { location ->
                viewModel.updateCurrentLocation(location)
                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(location, 15f)
                )
            }
        }
    }
    
    // Request location on launch
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            getCurrentLocation(context) { location ->
                viewModel.updateCurrentLocation(location)
            }
        }
    }
    
    // Update map when routes change
    LaunchedEffect(uiState, safetyPlaces) {
        googleMap?.let { map ->
            if (uiState is SafeRoutesUiState.Success) {
                val routes = (uiState as SafeRoutesUiState.Success).routes
                // Clear existing polylines
                map.clear()
                
                // Draw routes
                routes.forEachIndexed { index, route ->
                    val points = PolyUtil.decode(route.polyline)
                    val color = when (route.riskLevel) {
                        RiskLevel.LOW -> android.graphics.Color.parseColor("#4CAF50")
                        RiskLevel.MEDIUM -> android.graphics.Color.parseColor("#FF9800")
                        RiskLevel.HIGH -> android.graphics.Color.parseColor("#F44336")
                    }
                    
                    map.addPolyline(
                        PolylineOptions()
                            .addAll(points)
                            .color(color)
                            .width(if (route.id == selectedRoute?.id) 15f else 10f)
                            .zIndex(if (route.id == selectedRoute?.id) 1f else 0f)
                    )
                }
                
                // Draw safety place markers after routes
                drawSafetyPlaceMarkers(map, safetyPlaces, currentLocation)
                
                // Fit camera to show all routes
                if (routes.isNotEmpty()) {
                    val bounds = LatLngBounds.builder()
                    routes.forEach { route ->
                        PolyUtil.decode(route.polyline).forEach { bounds.include(it) }
                    }
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
                }
            } else {
                // Even when no routes, show nearby safety places on map
                drawSafetyPlaceMarkers(map, safetyPlaces, currentLocation)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safe Routes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSearchDialog = true }
            ) {
                Icon(Icons.Default.Search, "Search destination")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Google Map
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (hasLocationPermission) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                onCreate(null)
                                onResume()
                                mapView = this
                                getMapAsync { map ->
                                    googleMap = map
                                    setupMap(map, context, currentLocation)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.LocationOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Location permission required")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    locationPermissionLauncher.launch(
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                }
                            ) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }
            }
            
            // Routes list/info
            when (uiState) {
                is SafeRoutesUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                is SafeRoutesUiState.Success -> {
                    val state = uiState as SafeRoutesUiState.Success
                    RoutesList(
                        routes = state.routes,
                        selectedRoute = selectedRoute,
                        vehicleRecommendation = state.vehicleRecommendation,
                        onRouteSelected = { viewModel.selectRoute(it) }
                    )
                }
                
                is SafeRoutesUiState.Error -> {
                    ErrorMessage((uiState as SafeRoutesUiState.Error).message)
                }
                
                SafeRoutesUiState.Idle -> {
                    SearchPrompt()
                }
            }
        }
    }

    // Destination search dialog
    if (showSearchDialog) {
        DestinationSearchDialogNew(
            currentLocation = currentLocation,
            onDismiss = { showSearchDialog = false },
            onSearch = { destination ->
                showSearchDialog = false
                viewModel.searchSafeRoutes(destination)
            }
        )
    }
    
    DisposableEffect(Unit) {
        onDispose {
            mapView?.onDestroy()
        }
    }
}

@SuppressLint("MissingPermission")
private fun setupMap(
    map: GoogleMap,
    context: android.content.Context,
    currentLocation: LatLng?
) {
    map.uiSettings.apply {
        isZoomControlsEnabled = true
        isMyLocationButtonEnabled = true
        isCompassEnabled = true
    }
    
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        map.isMyLocationEnabled = true
    }
    
    currentLocation?.let {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 15f))
    }
}

@SuppressLint("MissingPermission")
private fun getCurrentLocation(
    context: android.content.Context,
    onLocation: (LatLng) -> Unit
) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        location?.let {
            onLocation(LatLng(it.latitude, it.longitude))
        }
    }
}

/**
 * Draw nearby safety place markers (police stations and hospitals) on the map.
 * Limits to places within 30 km of current location to avoid overcrowding.
 */
private fun drawSafetyPlaceMarkers(
    map: GoogleMap,
    safetyPlaces: List<SafetyPlace>,
    currentLocation: LatLng?
) {
    val center = currentLocation ?: LatLng(28.6139, 77.2090)
    val nearbyPlaces = safetyPlaces.filter { place ->
        val dist = floatArrayOf(0f)
        android.location.Location.distanceBetween(
            center.latitude, center.longitude,
            place.location.latitude, place.location.longitude,
            dist
        )
        dist[0] <= 30_000f // 30 km radius
    }

    for (place in nearbyPlaces) {
        val icon = when (place.type) {
            SafetyPlaceType.POLICE -> createSRPoliceIcon()
            SafetyPlaceType.HOSPITAL -> createSRHospitalIcon()
        }
        val emoji = when (place.type) {
            SafetyPlaceType.POLICE -> "🚔"
            SafetyPlaceType.HOSPITAL -> "🏥"
        }
        map.addMarker(
            MarkerOptions()
                .position(place.location)
                .title("$emoji ${place.name}")
                .icon(icon)
                .anchor(0.5f, 0.5f)
                .alpha(0.9f)
                .zIndex(3f)
        )
    }
}

/** Blue shield with "P" for police */
private fun createSRPoliceIcon(): BitmapDescriptor {
    val size = 44
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1565C0.toInt()
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    val rect = android.graphics.RectF(3f, 2f, 41f, 40f)
    canvas.drawRoundRect(rect, 7f, 7f, bgPaint)
    canvas.drawRoundRect(rect, 7f, 7f, borderPaint)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("P", size / 2f, size / 2f + 8f, textPaint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/** Red cross on white circle for hospital */
private fun createSRHospitalIcon(): BitmapDescriptor {
    val size = 44
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD32F2F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    canvas.drawCircle(size / 2f, size / 2f, 19f, bgPaint)
    canvas.drawCircle(size / 2f, size / 2f, 19f, borderPaint)
    val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD32F2F.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRect(19f, 9f, 25f, 35f, crossPaint)
    canvas.drawRect(9f, 19f, 35f, 25f, crossPaint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

@Composable
fun RoutesList(
    routes: List<SafeRoute>,
    selectedRoute: SafeRoute?,
    vehicleRecommendation: VehicleRecommendation,
    onRouteSelected: (SafeRoute) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Vehicle recommendation
        item {
            VehicleRecommendationCard(vehicleRecommendation)
        }
        
        // Routes
        items(routes) { route ->
            RouteCard(
                route = route,
                isSelected = route.id == selectedRoute?.id,
                onClick = { onRouteSelected(route) }
            )
        }
    }
}

@Composable
fun RouteCard(
    route: SafeRoute,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (route.isRecommended) "✅ ${route.summary}" else route.summary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (route.isRecommended) FontWeight.Bold else FontWeight.Normal
                )
                RiskBadge(route.riskLevel)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${String.format("%.1f", route.distance / 1000f)} km",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${route.duration / 60} min",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun RiskBadge(riskLevel: RiskLevel) {
    val color = when (riskLevel) {
        RiskLevel.LOW -> Color(0xFF4CAF50)
        RiskLevel.MEDIUM -> Color(0xFFFF9800)
        RiskLevel.HIGH -> Color(0xFFF44336)
    }
    
    val icon = when (riskLevel) {
        RiskLevel.LOW -> "🟢"
        RiskLevel.MEDIUM -> "🟠"
        RiskLevel.HIGH -> "🔴"
    }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = riskLevel.name,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
fun VehicleRecommendationCard(recommendation: VehicleRecommendation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "🚗 ${recommendation.vehicle.displayName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = recommendation.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SearchPrompt() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Tap 🔍 to search destination",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}