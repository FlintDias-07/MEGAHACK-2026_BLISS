package com.safepulse.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safepulse.SafePulseApplication
import com.safepulse.data.repository.SafeRoutesRepository
import com.safepulse.domain.saferoutes.RouteRiskAnalyzer
import com.safepulse.domain.saferoutes.VehicleRecommender
import com.safepulse.ui.saferoutes.SafeRoutesScreenWithMap
import com.safepulse.ui.saferoutes.SafeRoutesViewModel
import com.safepulse.ui.saferoutes.SafeRoutesViewModelFactory

/**
 * Wrapper to provide dependencies to SafeRoutesScreen
 */
@Composable
fun SafeRoutesScreenWrapper(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as SafePulseApplication
    
    // Create dependencies
    val hotspotRepository = application.database.hotspotDao().let { dao ->
        com.safepulse.data.repository.HotspotRepository(dao)
    }
    
    val riskAnalyzer = RouteRiskAnalyzer(hotspotRepository)
    val vehicleRecommender = VehicleRecommender()
    val safeRoutesRepository = SafeRoutesRepository(context, riskAnalyzer)
    
    // Create ViewModel with factory
    val viewModel: SafeRoutesViewModel = viewModel(
        factory = SafeRoutesViewModelFactory(safeRoutesRepository, vehicleRecommender)
    )
    
    SafeRoutesScreenWithMap(
        viewModel = viewModel,
        onNavigateBack = onBack
    )
}
