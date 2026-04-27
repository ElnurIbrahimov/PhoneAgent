package com.phoneagent.ui

import android.Manifest
import android.os.Build
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
import com.phoneagent.overlay.OverlayPermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
                navigationIcon = {
                    Button(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Required Permissions",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            val overlayGranted = OverlayPermissionManager.canDrawOverlays(context)
            Text("Overlay Permission: ${if (overlayGranted) "Granted" else "Required"}")

            if (!overlayGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (context is android.app.Activity) {
                            OverlayPermissionManager.requestOverlayPermission(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Request Overlay Permission")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text("Notification Permission: Required on Android 13+")
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (context is android.app.Activity) {
                            context.requestPermissions(
                                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                1002
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Request Notification Permission")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "PhoneAgent needs overlay permission to display the floating bubble and chat interface above other apps.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
