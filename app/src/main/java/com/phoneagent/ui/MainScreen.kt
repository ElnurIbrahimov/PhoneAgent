package com.phoneagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.AgentController
import com.phoneagent.overlay.OverlayService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    agentController: AgentController,
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PhoneAgent") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PhoneAgent Control Center",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    OverlayService.start(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Overlay Service")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    OverlayService.stop(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop Overlay Service")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNavigateToChat,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Chat")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Provider Settings")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNavigateToPermissions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Permissions")
            }
        }
    }
}
