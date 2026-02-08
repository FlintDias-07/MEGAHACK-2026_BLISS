package com.safepulse.ui.riskmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.safepulse.data.repository.RiskZoneRepository
import com.safepulse.domain.riskmap.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RiskMapViewModel(
    private val riskZoneRepository: RiskZoneRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RiskMapUiState>(RiskMapUiState.Loading)
    val uiState: StateFlow<RiskMapUiState> = _uiState.asStateFlow()

    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    private val _safeRoutes = MutableStateFlow<List<SafeRouteOption>>(emptyList())
    val safeRoutes: StateFlow<List<SafeRouteOption>> = _safeRoutes.asStateFlow()

    private val _selectedFilter = MutableStateFlow(RiskFilter.ALL)
    val selectedFilter: StateFlow<RiskFilter> = _selectedFilter.asStateFlow()

    private val _locationRisk = MutableStateFlow<LocationRiskInfo?>(null)
    val locationRisk: StateFlow<LocationRiskInfo?> = _locationRisk.asStateFlow()

    private val _destination = MutableStateFlow<LatLng?>(null)
    val destination: StateFlow<LatLng?> = _destination.asStateFlow()

    private val _safetyPlaces = MutableStateFlow<List<SafetyPlace>>(emptyList())
    val safetyPlaces: StateFlow<List<SafetyPlace>> = _safetyPlaces.asStateFlow()

    private val _showSafetyPlaces = MutableStateFlow(true)
    val showSafetyPlaces: StateFlow<Boolean> = _showSafetyPlaces.asStateFlow()

    fun loadRiskData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = riskZoneRepository.loadAllRiskData()
                _uiState.value = RiskMapUiState.Success(data)
                // Load safety places (limited to nearby when location available)
                val loc = _currentLocation.value
                _safetyPlaces.value = if (loc != null) {
                    riskZoneRepository.getSafetyPlacesNear(loc, 30.0)
                } else {
                    riskZoneRepository.getSafetyPlacesNear(LatLng(28.6139, 77.2090), 30.0)
                }
            } catch (e: Exception) {
                _uiState.value = RiskMapUiState.Error(e.message ?: "Failed to load risk data")
            }
        }
    }

    fun toggleSafetyPlaces() {
        _showSafetyPlaces.value = !_showSafetyPlaces.value
    }

    fun updateCurrentLocation(location: LatLng) {
        _currentLocation.value = location
        viewModelScope.launch(Dispatchers.IO) {
            val crimeRisk = riskZoneRepository.computeCrimeRisk(location)
            val disasterRisk = riskZoneRepository.computeDisasterRisk(location)
            val overallRisk = riskZoneRepository.computeRiskAtLocation(location)

            val nearbyCrime = riskZoneRepository.getCrimeZonesNear(location, 15.0)
            val nearbyDisaster = riskZoneRepository.getDisasterZonesNear(location, 15.0)

            _locationRisk.value = LocationRiskInfo(
                overallRisk = overallRisk,
                crimeRisk = crimeRisk,
                disasterRisk = disasterRisk,
                riskLevel = when {
                    overallRisk >= 0.7f -> "HIGH"
                    overallRisk >= 0.4f -> "MEDIUM"
                    else -> "LOW"
                },
                nearbyCrimeZones = nearbyCrime.take(5),
                nearbyDisasterZones = nearbyDisaster.take(5)
            )

            // Refresh nearby safety places for the new location
            _safetyPlaces.value = riskZoneRepository.getSafetyPlacesNear(location, 30.0)
        }
    }

    fun setFilter(filter: RiskFilter) {
        _selectedFilter.value = filter
    }

    fun searchSafeRoute(destinationLatLng: LatLng) {
        // Use current location or default to Delhi if unavailable
        val origin = _currentLocation.value ?: LatLng(28.6139, 77.2090).also {
            _currentLocation.value = it
        }
        _destination.value = destinationLatLng

        viewModelScope.launch(Dispatchers.IO) {
            val routes = riskZoneRepository.suggestSafeWaypoints(origin, destinationLatLng)
            _safeRoutes.value = routes
        }
    }

    fun clearRoutes() {
        _safeRoutes.value = emptyList()
        _destination.value = null
    }
}

sealed class RiskMapUiState {
    object Loading : RiskMapUiState()
    data class Success(val riskData: CombinedRiskData) : RiskMapUiState()
    data class Error(val message: String) : RiskMapUiState()
}

enum class RiskFilter {
    ALL, CRIME_ONLY, DISASTER_ONLY
}

data class LocationRiskInfo(
    val overallRisk: Float,
    val crimeRisk: Float,
    val disasterRisk: Float,
    val riskLevel: String,
    val nearbyCrimeZones: List<CrimeRiskZone>,
    val nearbyDisasterZones: List<DisasterRiskZone>
)

class RiskMapViewModelFactory(
    private val riskZoneRepository: RiskZoneRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RiskMapViewModel::class.java)) {
            return RiskMapViewModel(riskZoneRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}