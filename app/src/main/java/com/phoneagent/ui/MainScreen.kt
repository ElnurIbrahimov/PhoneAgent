package com.phoneagent.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.AgentController
import com.phoneagent.browser.AgentBrowserActivity
import com.phoneagent.overlay.OverlayService
import com.phoneagent.ui.components.StatusChip
import com.phoneagent.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    agentController: AgentController,
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val context = LocalContext.current
    var overlayRunning by remember { mutableStateOf(OverlayService.isRunning) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PhoneAgent", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "AI Agent for Android",
                            style = MaterialTheme.typography.labelMedium,
                            color = OnSurfaceMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = OnBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeroCard(
                    isRunning = overlayRunning,
                    onStart = {
                        OverlayService.start(context)
                        overlayRunning = true
                    },
                    onStop = {
                        OverlayService.stop(context)
                        overlayRunning = false
                    }
                )
            }

            item {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnBackground,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            val actions = listOf(
                ActionCardData(
                    icon = Icons.Default.Chat,
                    title = "Open Chat",
                    subtitle = "Full-screen conversation",
                    gradient = listOf(Primary, Secondary),
                    onClick = onNavigateToChat
                ),
                ActionCardData(
                    icon = Icons.Default.Language,
                    title = "Agent Browser",
                    subtitle = "Built-in web browser",
                    gradient = listOf(Secondary, Info),
                    onClick = {
                        context.startActivity(Intent(context, AgentBrowserActivity::class.java))
                    }
                ),
                ActionCardData(
                    icon = Icons.Default.Settings,
                    title = "Provider Settings",
                    subtitle = "API keys & models",
                    gradient = null,
                    onClick = onNavigateToSettings
                ),
                ActionCardData(
                    icon = Icons.Default.Security,
                    title = "Permissions",
                    subtitle = "Manage access",
                    gradient = null,
                    onClick = onNavigateToPermissions
                )
            )

            items(actions.size) { index ->
                ActionCard(data = actions[index])
            }
        }
    }
}

@Composable
private fun HeroCard(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val gradient = Brush.linearGradient(listOf(Primary.copy(alpha = 0.3f), Secondary.copy(alpha = 0.2f)))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(gradient)
            .padding(24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Overlay Service",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusChip(
                        text = if (isRunning) "Running" else "Stopped",
                        isActive = isRunning
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isRunning) Success.copy(alpha = 0.2f) else SurfaceBorder,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isRunning) Success else OnSurfaceDim,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = if (isRunning) onStop else onStart,
                modifier = Modifier.fillMaxWidth(),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Error.copy(alpha = 0.2f) else Primary
                )
            ) {
                Text(
                    text = if (isRunning) "Stop Service" else "Start Service",
                    color = if (isRunning) Error else OnBackground
                )
            }
        }
    }
}

data class ActionCardData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val gradient: List<androidx.compose.ui.graphics.Color>?,
    val onClick: () -> Unit
)

@Composable
private fun ActionCard(data: ActionCardData) {
    Card(
        onClick = data.onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = CardShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (data.gradient != null) data.gradient[0].copy(alpha = 0.2f) else SurfaceElevated,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = data.icon,
                    contentDescription = null,
                    tint = if (data.gradient != null) data.gradient[0] else Primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnBackground,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = data.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = OnSurfaceDim,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}