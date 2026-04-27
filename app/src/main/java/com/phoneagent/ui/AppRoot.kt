package com.phoneagent.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.phoneagent.agent.AgentController

@Composable
fun AppRoot(agentController: AgentController) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
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
                onBack = { navController.popBackStack() }
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
