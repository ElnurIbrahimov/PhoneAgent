package com.phoneagent.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.phoneagent.agent.AgentController

@Composable
fun AppRoot(agentController: AgentController) {
    val context = LocalContext.current
    val navController = rememberNavController()
    var onboardingDone by remember { mutableStateOf(OnboardingManager.isCompleted(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val startDestination = if (onboardingDone) "main" else "onboarding"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("onboarding") {
            OnboardingScreen(
                onComplete = {
                    onboardingDone = true
                    navController.navigate("main") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
                onRequestPermission = { permissions ->
                    permissionLauncher.launch(permissions)
                }
            )
        }
        composable("main") {
            MainScreen(
                agentController = agentController,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPermissions = { navController.navigate("permissions") },
                onNavigateToChat = { navController.navigate("chat") }
            )
        }
        composable("settings") {
            ProviderSettingsScreen(
                agentController = agentController,
                onBack = { navController.popBackStack() }
            )
        }
        composable("permissions") {
            PermissionScreen(
                onBack = { navController.popBackStack() },
                onRequestPermissions = { permissions ->
                    permissionLauncher.launch(permissions)
                }
            )
        }
        composable("chat") {
            ChatScreen(
                agentController = agentController,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
