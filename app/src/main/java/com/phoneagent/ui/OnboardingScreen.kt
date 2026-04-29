package com.phoneagent.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.phoneagent.ui.theme.*

data class OnboardingStep(
    val title: String,
    val description: String,
    val actionLabel: String?,
    val action: ((Context) -> Unit)?,
    val icon: ImageVector,
    val gradient: List<androidx.compose.ui.graphics.Color>
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onRequestPermission: (Array<String>) -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }

    val steps = listOf(
        OnboardingStep(
            title = "Welcome to PhoneAgent",
            description = "An AI agent that lives on your phone. It can open apps, browse the web, read your screen, and perform actions on your behalf.",
            actionLabel = "Get Started",
            action = null,
            icon = Icons.Default.SmartToy,
            gradient = listOf(Primary.copy(alpha = 0.3f), Secondary.copy(alpha = 0.2f))
        ),
        OnboardingStep(
            title = "Floating Bubble",
            description = "PhoneAgent appears as a floating bubble on your screen for quick access. This requires overlay permission to draw over other apps.",
            actionLabel = "Enable Overlay",
            action = { ctx ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                }
                ctx.startActivity(intent)
            },
            icon = Icons.Default.Layers,
            gradient = listOf(Secondary.copy(alpha = 0.3f), Info.copy(alpha = 0.2f))
        ),
        OnboardingStep(
            title = "Screen Reading",
            description = "To read what's on your screen and perform gestures (tap, swipe, type), PhoneAgent needs the Accessibility Service.",
            actionLabel = "Enable Accessibility",
            action = { ctx ->
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            icon = Icons.Default.Accessibility,
            gradient = listOf(Success.copy(alpha = 0.3f), Secondary.copy(alpha = 0.2f))
        ),
        OnboardingStep(
            title = "Notifications",
            description = "PhoneAgent can read your notifications to provide context-aware assistance. For example, it can summarize missed messages or help you act on alerts.",
            actionLabel = "Enable Notifications",
            action = { ctx ->
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            icon = Icons.Default.Notifications,
            gradient = listOf(Warning.copy(alpha = 0.3f), Success.copy(alpha = 0.2f))
        ),
        OnboardingStep(
            title = "Microphone",
            description = "Use your voice to talk to PhoneAgent. Say what you need and the agent will execute it. Speech is processed on-device where possible.",
            actionLabel = "Enable Microphone",
            action = { onRequestPermission(arrayOf(Manifest.permission.RECORD_AUDIO)) },
            icon = Icons.Default.Mic,
            gradient = listOf(Info.copy(alpha = 0.3f), Primary.copy(alpha = 0.2f))
        ),
        OnboardingStep(
            title = "You're All Set",
            description = "PhoneAgent is ready. Tap the floating bubble any time to start a conversation. You can manage permissions later in Settings > Permissions.",
            actionLabel = "Finish",
            action = null,
            icon = Icons.Default.CheckCircle,
            gradient = listOf(Success.copy(alpha = 0.3f), Primary.copy(alpha = 0.2f))
        )
    )

    val step = steps[currentStep]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Background gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(Brush.verticalGradient(step.gradient))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step indicator
            Text(
                text = "${currentStep + 1} / ${steps.size}",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceMuted
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Surface.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = step.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = OnBackground
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Description
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = OnSurfaceMuted
            )
            Spacer(modifier = Modifier.height(48.dp))

            // Action button
            if (step.actionLabel != null && step.action != null) {
                Button(
                    onClick = { step.action.invoke(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text(
                        step.actionLabel,
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = OnBackground
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Navigation buttons
            if (currentStep < steps.size - 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (currentStep > 0) {
                        TextButton(onClick = { currentStep-- }) {
                            Text("Back", color = OnSurfaceMuted)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    TextButton(onClick = { currentStep++ }) {
                        Text("Skip", color = OnSurfaceMuted)
                    }
                }
            } else {
                Button(
                    onClick = {
                        OnboardingManager.markCompleted(context)
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Success)
                ) {
                    Text(
                        step.actionLabel ?: "Finish",
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = OnBackground
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = { currentStep-- },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back", color = OnSurfaceMuted)
                }
            }

            // Progress dots
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentStep) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentStep) Primary else SurfaceBorder
                            )
                    )
                }
            }
        }
    }
}