package com.safepulse.data.repository

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.model.TravelMode
import com.safepulse.BuildConfig
import com.safepulse.domain.saferoutes.RouteRiskAnalyzer
import com.safepulse.domain.saferoutes.SafeRoute
import com.safepulse.domain.saferoutes.RiskLevel
import com.safepulse.domain.saferoutes.VoiceNavigationRoute
import com.safepulse.domain.saferoutes.VoiceNavigationStep
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository for fetching and analyzing safe routes
 */
class SafeRoutesRepository(
    private val context: Context,
    private val riskAnalyzer: RouteRiskAnalyzer
) {
    companion object {
        private const val TAG = "SafeRoutesRepository"
    }
    
    private val geoApiContext: GeoApiContext by lazy {
        GeoApiContext.Builder()
            .apiKey(BuildConfig.MAPS_API_KEY)
            .build()
    }
    
    /**
     * Get safe routes from origin to destination
     * Returns routes sorted by safety (safest first)
     */
    suspend fun getSafeRoutes(
        origin: LatLng,
        destination: LatLng
    ): List<SafeRoute> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching routes from $origin to $destination")
            
            // Request routes from Google Directions API
            val request = DirectionsApi.newRequest(geoApiContext)
                .origin(com.google.maps.model.LatLng(origin.latitude, origin.longitude))
                .destination(com.google.maps.model.LatLng(destination.latitude, destination.longitude))
                .alternatives(true)  // Get alternative routes
                .mode(TravelMode.DRIVING)
            
            val result = request.await()
            
            Log.d(TAG, "Got ${result.routes.size} routes from Google")
            
            // Analyze each route for safety
            val safeRoutes = result.routes.mapIndexed { index, route ->
                val points = decodePolyline(route.overviewPolyline.encodedPath)
                val risk = riskAnalyzer.analyzeRouteRisk(points)
                
                SafeRoute(
                    id = UUID.randomUUID().toString(),
                    polyline = route.overviewPolyline.encodedPath,
                    distance = route.legs[0].distance.inMeters,
                    duration = route.legs[0].duration.inSeconds,
                    riskScore = risk.score,
                    riskLevel = risk.level,
                    summary = route.summary ?: "Route ${index + 1}",
                    isRecommended = false  // Will set after sorting
                )
            }.sortedBy { it.riskScore }  // Safest first
            
            // Mark safest route as recommended and return
            if (safeRoutes.isNotEmpty()) {
                val updatedRoutes = safeRoutes.mapIndexed { index, route ->
                    if (index == 0) route.copy(isRecommended = true) else route
                }
                Log.i(TAG, "Safest route: ${updatedRoutes[0].summary} (Risk: ${updatedRoutes[0].riskLevel})")
                updatedRoutes
            } else {
                safeRoutes
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching routes", e)
            emptyList()
        }
    }

    suspend fun getWalkingNavigationRoute(
        origin: LatLng,
        destination: LatLng,
        destinationName: String
    ): VoiceNavigationRoute? = withContext(Dispatchers.IO) {
        try {
            val result = DirectionsApi.newRequest(geoApiContext)
                .origin(com.google.maps.model.LatLng(origin.latitude, origin.longitude))
                .destination(com.google.maps.model.LatLng(destination.latitude, destination.longitude))
                .mode(TravelMode.WALKING)
                .alternatives(false)
                .await()

            val route = result.routes.firstOrNull() ?: return@withContext null
            val leg = route.legs.firstOrNull() ?: return@withContext null

            val steps = leg.steps.mapNotNull { step ->
                val instruction = step.htmlInstructions
                    ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() }
                    ?.replace("\\s+".toRegex(), " ")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null

                VoiceNavigationStep(
                    instruction = instruction,
                    distanceMeters = step.distance?.inMeters?.toInt() ?: 0,
                    startLocation = LatLng(step.startLocation.lat, step.startLocation.lng),
                    endLocation = LatLng(step.endLocation.lat, step.endLocation.lng)
                )
            }

            if (steps.isEmpty()) return@withContext null

            VoiceNavigationRoute(
                destinationName = destinationName,
                destination = destination,
                totalDistanceMeters = leg.distance?.inMeters?.toInt() ?: 0,
                totalDurationSeconds = leg.duration?.inSeconds?.toInt() ?: 0,
                steps = steps
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching walking navigation route", e)
            null
        }
    }
    
    /**
     * Decode Google's encoded polyline into lat/lng points
     */
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            
            val p = LatLng(
                lat.toDouble() / 1E5,
                lng.toDouble() / 1E5
            )
            poly.add(p)
        }
        
        return poly
    }
}
