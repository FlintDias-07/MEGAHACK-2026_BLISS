package com.safepulse.ui.saferoutes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.safepulse.data.repository.SafeRoutesRepository
import com.safepulse.domain.saferoutes.VehicleRecommender

/**
 * Factory for creating SafeRoutesViewModel with dependencies
 */
class SafeRoutesViewModelFactory(
    private val safeRoutesRepository: SafeRoutesRepository,
    private val vehicleRecommender: VehicleRecommender
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SafeRoutesViewModel::class.java)) {
            return SafeRoutesViewModel(safeRoutesRepository, vehicleRecommender) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
