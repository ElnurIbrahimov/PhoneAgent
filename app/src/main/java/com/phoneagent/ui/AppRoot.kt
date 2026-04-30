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
import com.phoneagent.ui.theme.*

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
        composable(
            "onboarding",
            exitTransition = { PhoneAgentExitTransition },
            popEnterTransition = { PhoneAgentPopEnterTransition }
        ) {
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
        composable(
            "main",
            enterTransition = { PhoneAgentEnterTransition },
            exitTransition = { PhoneAgentExitTransition },
            popEnterTransition = { PhoneAgentPopEnterTransition },
            popExitTransition = { PhoneAgentPopExitTransition }
        ) {
            MainScreen(
                agentController = agentController,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPermissions = { navController.navigate("permissions") },
                onNavigateToChat = { navController.navigate("chat") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToMemory = { navController.navigate("memory") }
            )
        }
        composable(
            "settings",
            enterTransition = { PhoneAgentEnterTransition },
            exitTransition = { PhoneAgentExitTransition },
            popEnterTransition = { PhoneAgentPopEnterTransition },
            popExitTransition = { PhoneAgentPopExitTransition }
        ) {
            ProviderSettingsScreen(
                agentController = agentController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "permissions",
            enterTransition = { PhoneAgentEnterTransition },
            exitTransition = { PhoneAgentExitTransition },
            popEnterTransition = { PhoneAgentPopEnterTransition },
            popExitTransition = { PhoneAgentPopExitTransition }
        ) {
            PermissionScreen(
                onBack = { navController.popBackStack() },
                onRequestPermissions = { permissions ->
                    permissionLauncher.launch(permissions)
                }
            )
        }
        composable(
            "chat",
            enterTransition = { PhoneAgentEnterTransition },
            exitTransition = { PhoneAgentExitTransition },
            popEnterTransition = { PhoneAgentPopEnterTransition },
            popExitTransition = { PhoneAgentPopExitTransition }
        ) {
            ChatScreen(
                agentController = agentController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "history",
            enterTransition = { PhoneAgentEnterTransition },
            exitTransition = { PhoneAgentExitTransition },
            popEnterTransition = { PhoneAgentPopEnterTransition },
            popExitTransition = { PhoneAgentPopExitTransition }
        ) {
            TaskHistoryScreen(
                agentController = agentController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "memory",
            enterTransition = { PhoneAgentEnterTransition },
            exitTransition = { PhoneAgentExitTransition },
            popEnterTransition = { PhoneAgentPopEnterTransition },
            popExitTransition = { PhoneAgentPopExitTransition }
        ) {
            MemoryBrowserScreen(
                agentController = agentController,
                onBack = { navController.popBackStack() }
            )
        }
    }
}