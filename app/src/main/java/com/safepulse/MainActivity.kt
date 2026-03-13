package com.safepulse

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.safepulse.data.prefs.UserPreferences
import com.safepulse.ui.onboarding.OnboardingOverlayScreen
import com.safepulse.ui.screens.*
import com.safepulse.ui.theme.SafePulseTheme
import com.safepulse.utils.LocaleManager
import com.safepulse.utils.PermissionHelper
import com.safepulse.worker.DataRefreshWorker
import com.safepulse.worker.SafetyCheckWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    private val userPreferences by lazy { UserPreferences(this) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            // Some permissions denied - app will work with limited functionality
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedLanguage()
        super.onCreate(savedInstanceState)

        // Request permissions
        requestPermissions()

        // Schedule background workers
        SafetyCheckWorker.schedule(this)
        DataRefreshWorker.schedule(this)

        setContent {
            var darkMode by remember { mutableStateOf(false) }
            
            LaunchedEffect(Unit) {
                userPreferences.darkModeEnabledFlow.collect {
                    darkMode = it
                }
            }
            
            SafePulseTheme(darkTheme = darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var onboardingComplete by remember { mutableStateOf<Boolean?>(null) }
                    var tutorialComplete by remember { mutableStateOf<Boolean?>(null) }

                    LaunchedEffect(Unit) {
                        onboardingComplete = userPreferences.onboardingCompleteFlow.first()
                        tutorialComplete = userPreferences.onboardingTutorialCompleteFlow.first()
                    }

                    when {
                        onboardingComplete == null || tutorialComplete == null -> {
                            // Loading state - could show splash
                        }
                        onboardingComplete == false -> {
                            // Show permission onboarding first
                            OnboardingFlow(
                                onComplete = {
                                    onboardingComplete = true
                                }
                            )
                        }
                        tutorialComplete == false -> {
                            // Show voice-guided tutorial after permissions are done
                            MainNavigationWithTutorial(
                                onTutorialComplete = {
                                    tutorialComplete = true
                                }
                            )
                        }
                        else -> {
                            // Both complete, show normal navigation
                            MainNavigation()
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val missingPermissions = PermissionHelper.getMissingPermissions(this)
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun applySavedLanguage() {
        if (LocaleManager.hasActiveAppLocale()) return
        val languageCode = runBlocking { userPreferences.appLanguageCodeFlow.first() }
        LocaleManager.applyLanguage(languageCode)
    }
}

@Composable
fun OnboardingFlow(onComplete: () -> Unit) {
    OnboardingScreen(onComplete = onComplete)
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToLogs = { navController.navigate("logs") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToSafeRoutes = { navController.navigate("safe_routes") },
                onNavigateToRiskMap = { navController.navigate("risk_map") }
            )
        }

        composable("logs") {
            EventLogsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToRiskMap = { navController.navigate("risk_map") },
                onNavigateToUserManual = { navController.navigate("user_manual") }
            )
        }
        
        composable("user_manual") {
            UserManualScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable("safe_routes") {
            SafeRoutesScreenWrapper(
                onBack = { navController.popBackStack() }
            )
        }

        composable("risk_map") {
            RiskMapScreenWrapper(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun MainNavigationWithTutorial(onTutorialComplete: () -> Unit) {
    var showTutorial by remember { mutableStateOf(true) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Main navigation as background
        MainNavigation()
        
        // Tutorial overlay on top
        if (showTutorial) {
            OnboardingOverlayScreen(
                onComplete = {
                    showTutorial = false
                    onTutorialComplete()
                }
            )
        }
    }
}
