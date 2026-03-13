package com.safepulse.ui.nearbysafety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.safepulse.data.repository.RiskZoneRepository
import com.safepulse.domain.riskmap.SafeRouteOption
import com.safepulse.domain.riskmap.SafetyPlaceType
import com.safepulse.domain.saferoutes.*
import com.safepulse.ui.map.HospitalData
import com.safepulse.ui.map.PoliceStationData
import com.safepulse.ui.map.SafeZoneData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SafetyCategory { ALL, POLICE, HOSPITAL, SAFE_ZONE }

data class NearbySafetyItem(
    val id: String,
    val name: String,
    val category: SafetyCategory,
    val location: LatLng,
    val distanceKm: Double,
    val riskScore: Float,
    val riskLabel: String,
    val subtitle: String = ""
)

data class SelectedDestinationDetail(
    val item: NearbySafetyItem,
    val routeOptions: List<SafeRouteOption>,
    val vehicleRecommendation: VehicleRecommendation
)

sealed class NearbySafetyUiState {
    object Loading : NearbySafetyUiState()
    data class Success(
        val items: List<NearbySafetyItem>,
        val selectedCategory: SafetyCategory = SafetyCategory.ALL,
        val selectedDetail: SelectedDestinationDetail? = null
    ) : NearbySafetyUiState()
    data class Error(val message: String) : NearbySafetyUiState()
}

class NearbySafetyViewModel(
    private val riskZoneRepository: RiskZoneRepository,
    private val vehicleRecommender: VehicleRecommender
) : ViewModel() {

    private val _uiState = MutableStateFlow<NearbySafetyUiState>(NearbySafetyUiState.Loading)
    val uiState: StateFlow<NearbySafetyUiState> = _uiState.asStateFlow()

    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    private val _policeStations = MutableStateFlow<List<PoliceStationData>>(emptyList())
    val policeStations: StateFlow<List<PoliceStationData>> = _policeStations.asStateFlow()

    private val _hospitals = MutableStateFlow<List<HospitalData>>(emptyList())
    val hospitals: StateFlow<List<HospitalData>> = _hospitals.asStateFlow()

    private val _safeZones = MutableStateFlow<List<SafeZoneData>>(emptyList())
    val safeZones: StateFlow<List<SafeZoneData>> = _safeZones.asStateFlow()

    private var allIndiaDataLoaded = false

    fun updateCurrentLocation(location: LatLng) {
        _currentLocation.value = location
        loadNearbyPlaces(location)
        // Load all-India data once for the map
        if (!allIndiaDataLoaded) {
            allIndiaDataLoaded = true
            viewModelScope.launch(Dispatchers.IO) {
                _policeStations.value = riskZoneRepository.getAllPoliceStations()
                _hospitals.value = riskZoneRepository.getAllHospitals()
                _safeZones.value = riskZoneRepository.getSafeZonesForMap()
            }
        }
    }

    private fun loadNearbyPlaces(location: LatLng) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = mutableListOf<NearbySafetyItem>()

                // Police stations & hospitals — load all India
                val safetyPlaces = riskZoneRepository.loadSafetyPlaces()
                safetyPlaces.forEach { place ->
                    val dist = RiskZoneRepository.distanceKm(location, place.location)
                    val risk = riskZoneRepository.computeRiskAtLocation(place.location)
                    items.add(NearbySafetyItem(
                        id = "place_${place.id}",
                        name = place.name,
                        category = when (place.type) {
                            SafetyPlaceType.POLICE -> SafetyCategory.POLICE
                            SafetyPlaceType.HOSPITAL -> SafetyCategory.HOSPITAL
                        },
                        location = place.location,
                        distanceKm = dist,
                        riskScore = risk,
                        riskLabel = riskLabel(risk),
                        subtitle = place.address.ifEmpty { place.city }
                    ))
                }

                // Safe zones: crime zones with low risk — all India
                val crimeZones = riskZoneRepository.loadCrimeRiskZones()
                crimeZones.filter { it.crimeRiskScore < 0.3f }.forEach { zone ->
                    val dist = RiskZoneRepository.distanceKm(location, zone.location)
                    val risk = riskZoneRepository.computeRiskAtLocation(zone.location)
                    items.add(NearbySafetyItem(
                        id = "safezone_${zone.city}",
                        name = "${zone.city} Safe Area",
                        category = SafetyCategory.SAFE_ZONE,
                        location = zone.location,
                        distanceKm = dist,
                        riskScore = risk,
                        riskLabel = riskLabel(risk),
                        subtitle = "${zone.state} - Low crime area"
                    ))
                }

                _uiState.value = NearbySafetyUiState.Success(items = items.sortedBy { it.distanceKm })
            } catch (e: Exception) {
                _uiState.value = NearbySafetyUiState.Error(
                    e.message ?: "Failed to load nearby safety places"
                )
            }
        }
    }

    fun setCategory(category: SafetyCategory) {
        val current = _uiState.value
        if (current is NearbySafetyUiState.Success) {
            _uiState.value = current.copy(selectedCategory = category, selectedDetail = null)
        }
    }

    fun selectItem(item: NearbySafetyItem) {
        val origin = _currentLocation.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val routes = riskZoneRepository.suggestSafeWaypoints(origin, item.location)
            val safest = routes.minByOrNull { it.totalRiskScore }
            val recommendation = if (safest != null) {
                val syntheticRoute = SafeRoute(
                    id = "route_${item.id}",
                    polyline = "",
                    distance = (safest.distanceKm * 1000).toLong(),
                    duration = (safest.distanceKm * 3 * 60).toLong(),
                    riskScore = safest.totalRiskScore,
                    riskLevel = when {
                        safest.totalRiskScore >= 0.7f -> RiskLevel.HIGH
                        safest.totalRiskScore >= 0.4f -> RiskLevel.MEDIUM
                        else -> RiskLevel.LOW
                    },
                    summary = "Route to ${item.name}",
                    isRecommended = safest.isSafest
                )
                vehicleRecommender.recommendVehicle(syntheticRoute)
            } else {
                VehicleRecommendation(
                    vehicle = RecommendedVehicle.TRACKED_CAB,
                    reason = "Use tracked transport for safety"
                )
            }

            val detail = SelectedDestinationDetail(item, routes, recommendation)
            val current = _uiState.value
            if (current is NearbySafetyUiState.Success) {
                _uiState.value = current.copy(selectedDetail = detail)
            }
        }
    }

    fun clearSelection() {
        val current = _uiState.value
        if (current is NearbySafetyUiState.Success) {
            _uiState.value = current.copy(selectedDetail = null)
        }
    }

    private fun riskLabel(risk: Float): String = when {
        risk >= 0.7f -> "HIGH"
        risk >= 0.4f -> "MEDIUM"
        else -> "LOW"
    }
}

class NearbySafetyViewModelFactory(
    private val riskZoneRepository: RiskZoneRepository,
    private val vehicleRecommender: VehicleRecommender
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NearbySafetyViewModel::class.java)) {
            return NearbySafetyViewModel(riskZoneRepository, vehicleRecommender) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}