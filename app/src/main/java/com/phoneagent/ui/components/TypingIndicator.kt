package com.phoneagent.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.phoneagent.ui.theme.AgentBubble
import com.phoneagent.ui.theme.BubbleShapeAgent
import com.phoneagent.ui.theme.Primary

@Composable
fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val delays = listOf(0, 150, 300)

    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(AgentBubble, BubbleShapeAgent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            delays.forEach { delay ->
                val animatedValue by transition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = delay, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot$delay"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(animatedValue)
                        .alpha(animatedValue)
                        .background(Primary, CircleShape)
                )
            }
        }
    }
}
