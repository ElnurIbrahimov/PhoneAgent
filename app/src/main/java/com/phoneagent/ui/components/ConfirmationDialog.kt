package com.phoneagent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.PendingConfirmation
import com.phoneagent.ui.theme.*

@Composable
fun ConfirmationDialog(
    pending: PendingConfirmation,
    onApprove: () -> Unit,
    onCancel: () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(initialScale = 0.9f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .clip(CardShape)
                    .background(Surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Warning.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "!",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Warning,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Confirm Action",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The agent wants to perform a sensitive action:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceMuted
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .padding(16.dp)
                ) {
                    Row {
                        Text(
                            text = "Tool: ",
                            style = MaterialTheme.typography.labelMedium,
                            color = OnSurfaceMuted
                        )
                        Text(
                            text = pending.toolName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (pending.args.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        pending.args.forEach { (key, value) ->
                            Row {
                                Text(
                                    text = "$key: ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OnSurfaceMuted
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurface,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = pending.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = Warning
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = ButtonShape
                    ) {
                        Text("Deny")
                    }
                    Button(
                        onClick = onApprove,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                brush = Brush.horizontalGradient(listOf(Primary, Secondary)),
                                shape = ButtonShape
                            ),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = ButtonShape
                    ) {
                        Text("Approve")
                    }
                }
            }
        }
    }
}
