package com.phoneagent.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class OnboardingStep(
    val title: String,
    val description: String,
    val actionLabel: String?,
    val action: ((Context) -> Unit)?
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
            description = "An AI agent that lives on your phone. It can open apps, browse the web, read your screen, and perform actions on your behalf.\n\nLet's set up the permissions it needs.",
            actionLabel = "Get Started",
            action = null
        ),
        OnboardingStep(
            title = "Floating Bubble",
            description = "PhoneAgent appears as a floating bubble on your screen for quick access. This requires overlay permission to draw over other apps.\n\nYou can move the bubble anywhere on screen.",
            actionLabel = "Enable Overlay",
            action = { ctx ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                }
                ctx.startActivity(intent)
            }
        ),
        OnboardingStep(
            title = "Screen Reading",
            description = "To read what's on your screen and perform gestures (tap, swipe, type), PhoneAgent needs the Accessibility Service.\n\nWithout this, it can only control the built-in browser.",
            actionLabel = "Enable Accessibility",
            action = { ctx ->
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        ),
        OnboardingStep(
            title = "Notifications",
            description = "PhoneAgent can read your notifications to provide context-aware assistance. For example, it can summarize missed messages or help you act on alerts.",
            actionLabel = "Enable Notifications",
            action = { ctx ->
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        ),
        OnboardingStep(
            title = "Microphone",
            description = "Use your voice to talk to PhoneAgent. Say what you need and the agent will execute it.\n\nSpeech is processed on-device where possible.",
            actionLabel = "Enable Microphone",
            action = { onRequestPermission(arrayOf(Manifest.permission.RECORD_AUDIO)) }
        ),
        OnboardingStep(
            title = "You're All Set",
            description = "PhoneAgent is ready. Tap the floating bubble any time to start a conversation.\n\nYou can manage permissions later in Settings > Permissions.",
            actionLabel = "Finish",
            action = null
        )
    )

    val step = steps[currentStep]

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${currentStep + 1} of ${steps.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = step.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = step.description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(48.dp))

            if (step.actionLabel != null && step.action != null) {
                Button(
                    onClick = { step.action.invoke(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(step.actionLabel, modifier = Modifier.padding(vertical = 4.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (currentStep < steps.size - 1) {
                Button(
                    onClick = { currentStep++ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Skip", modifier = Modifier.padding(vertical = 4.dp))
                }
            } else {
                Button(
                    onClick = {
                        OnboardingManager.markCompleted(context)
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(step.actionLabel ?: "Finish", modifier = Modifier.padding(vertical = 4.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { currentStep-- },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text("Back", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}
