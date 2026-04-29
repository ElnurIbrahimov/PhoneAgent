package com.phoneagent.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.phoneagent.ui.components.StatusChip
import com.phoneagent.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    onBack: () -> Unit,
    onRequestPermissions: (Array<String>) -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions", color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Required Permissions",
                style = MaterialTheme.typography.titleMedium,
                color = OnBackground,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "PhoneAgent needs these permissions to operate effectively.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            val permissions = listOf(
                PermissionItem(
                    icon = Icons.Default.Layers,
                    label = "Overlay",
                    description = "Draw over other apps",
                    granted = android.provider.Settings.canDrawOverlays(context),
                    onRequest = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                ),
                PermissionItem(
                    icon = Icons.Default.Accessibility,
                    label = "Accessibility",
                    description = "Read screen & perform gestures",
                    granted = isAccessibilityServiceEnabled(context),
                    onRequest = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                ),
                PermissionItem(
                    icon = Icons.Default.Mic,
                    label = "Microphone",
                    description = "Voice input",
                    granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED,
                    onRequest = { onRequestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO)) }
                ),
                PermissionItem(
                    icon = Icons.Default.Sms,
                    label = "SMS",
                    description = "Send messages",
                    granted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PermissionChecker.PERMISSION_GRANTED,
                    onRequest = { onRequestPermissions(arrayOf(Manifest.permission.SEND_SMS)) }
                ),
                PermissionItem(
                    icon = Icons.Default.Phone,
                    label = "Phone",
                    description = "Make calls",
                    granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PermissionChecker.PERMISSION_GRANTED,
                    onRequest = { onRequestPermissions(arrayOf(Manifest.permission.CALL_PHONE)) }
                ),
                PermissionItem(
                    icon = Icons.Default.Notifications,
                    label = "Notifications",
                    description = "Read notification listener",
                    granted = isNotificationListenerEnabled(context),
                    onRequest = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            )

            permissions.forEach { item ->
                PermissionCard(item = item)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "After enabling a permission, you may need to restart the app.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim
            )
        }
    }
}

data class PermissionItem(
    val icon: ImageVector,
    val label: String,
    val description: String,
    val granted: Boolean,
    val onRequest: () -> Unit
)

@Composable
private fun PermissionCard(item: PermissionItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = CardShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (item.granted) Success.copy(alpha = 0.15f) else SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (item.granted) Success else OnSurfaceDim,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnBackground,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted
                )
            }

            if (item.granted) {
                StatusChip(text = "Granted", isActive = true)
            } else {
                Button(
                    onClick = item.onRequest,
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Enable", color = OnBackground)
                }
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val service = "${context.packageName}/com.phoneagent.accessibility.AgentAccessibilityService"
    return try {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        enabledServices.contains(service)
    } catch (e: Exception) {
        false
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: ""
    return enabledListeners.contains(context.packageName)
}