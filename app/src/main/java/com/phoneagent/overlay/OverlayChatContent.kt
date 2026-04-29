package com.phoneagent.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.AgentController
import com.phoneagent.ui.components.MessageBubble
import com.phoneagent.ui.theme.*

@Composable
fun OverlayChatContent(
    agentController: AgentController,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    val uiState by agentController.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Surface.copy(alpha = 0.98f),
                            SurfaceElevated.copy(alpha = 0.95f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Primary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PA",
                            style = MaterialTheme.typography.labelMedium,
                            color = Primary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "PhoneAgent",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnBackground,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                        Text(
                            text = uiState.currentModel.takeIf { it.isNotBlank() } ?: "Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row {
                    IconButton(onClick = onMinimize, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Minimize,
                            contentDescription = "Minimize",
                            tint = OnSurfaceDim,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = OnSurfaceDim,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Messages
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Background.copy(alpha = 0.6f))
                    .padding(8.dp)
            ) {
                if (uiState.messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Tap the mic or type to start",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDim
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.messages) { msg ->
                            MessageBubble(message = msg)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status
            if (uiState.isLoading) {
                Text(
                    text = uiState.agentStepStatus ?: "Thinking...",
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            uiState.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = Error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Input
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Ask...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceDim
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary.copy(alpha = 0.4f),
                        unfocusedBorderColor = SurfaceBorder,
                        focusedContainerColor = Background.copy(alpha = 0.5f),
                        unfocusedContainerColor = Background.copy(alpha = 0.5f)
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
                    enabled = !uiState.isLoading
                )

                IconButton(
                    onClick = { agentController.startVoiceInput() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Secondary.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.KeyboardVoice,
                        contentDescription = "Voice",
                        tint = Secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                val sendGradient = AccentGradient
                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            agentController.sendMessage(messageText)
                            messageText = ""
                        }
                    },
                    enabled = messageText.isNotBlank() && !uiState.isLoading,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (messageText.isNotBlank() && !uiState.isLoading) sendGradient else SurfaceBorder.copy(alpha = 0.3f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (messageText.isNotBlank() && !uiState.isLoading) OnBackground else OnSurfaceDim,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}