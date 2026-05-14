package com.phoneagent.ui.streaming

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.phoneagent.streaming.ReasoningChunk
import com.phoneagent.streaming.ReasoningType
import com.phoneagent.streaming.StreamingState
import com.phoneagent.streaming.StreamingStatus
import com.phoneagent.streaming.ToolCallState
import kotlin.math.roundToInt

@Composable
fun ReasoningCard(
    state: StreamingState,
    position: ReasoningCardPosition,
    onPositionChange: (ReasoningCardPosition) -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(position.x) }
    var offsetY by remember { mutableFloatStateOf(position.y) }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        onPositionChange(ReasoningCardPosition(offsetX, offsetY))
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
    ) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.width(300.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
        ) {
            Column {
                CardHeader(
                    status = state.status,
                    onMinimize = onMinimize,
                    onClose = onClose
                )
                HorizontalDivider(color = Color(0xFF333344))
                if (state.reasoningChunks.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .padding(8.dp)
                    ) {
                        items(state.reasoningChunks.takeLast(20)) { chunk ->
                            ReasoningChunkRow(chunk)
                        }
                    }
                } else {
                    Text(
                        "Waiting for reasoning...",
                        style = MaterialTheme.typography.Body2,
                        color = Color(0xFF888888),
                        modifier = Modifier.padding(12.dp)
                    )
                }
                state.toolCallInProgress?.let { tool ->
                    HorizontalDivider(color = Color(0xFF333344))
                    ToolCallRow(tool)
                }
            }
        }
    }
}

@Composable
private fun CardHeader(
    status: StreamingStatus,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    val statusColor = when (status) {
        StreamingStatus.THINKING -> Color(0xFF64B5F6)
        StreamingStatus.ACTING -> Color(0xFF81C784)
        StreamingStatus.OBSERVING -> Color(0xFFFFB74D)
        StreamingStatus.DONE -> Color(0xFF9E9E9E)
        StreamingStatus.ERROR -> Color(0xFFE57373)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2A3C))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Text(
                "Reasoning",
                style = MaterialTheme.typography.Subtitle2,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Row {
            IconButton(onClick = onMinimize, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Minimize,
                    contentDescription = "Minimize",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ReasoningChunkRow(chunk: ReasoningChunk) {
    val (color, label) = when (chunk.type) {
        ReasoningType.THOUGHT -> Pair(Color(0xFF90CAF9), "THK")
        ReasoningType.ACTION -> Pair(Color(0xFFA5D6A7), "ACT")
        ReasoningType.OBSERVATION -> Pair(Color(0xFFFFCC80), "OBS")
        ReasoningType.PLAN -> Pair(Color(0xFFCE93D8), "PLN")
    }
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            "[$label]",
            style = MaterialTheme.typography.Body2,
            color = color
        )
        Text(
            " ${chunk.content}",
            style = MaterialTheme.typography.Body2,
            color = Color(0xFFE0E0E0)
        )
    }
}

@Composable
private fun ToolCallRow(tool: ToolCallState) {
    val color = when (tool.status) {
        com.phoneagent.streaming.ToolCallStatus.STARTED -> Color(0xFFFFEB3B)
        com.phoneagent.streaming.ToolCallStatus.EXECUTING -> Color(0xFF81C784)
        com.phoneagent.streaming.ToolCallStatus.RESULT -> Color(0xFF90CAF9)
    }
    Text(
        text = "→ ${tool.toolName}",
        style = MaterialTheme.typography.Body2,
        color = color,
        modifier = Modifier.padding(12.dp)
    )
}

@Composable
fun MinimizedReasoningBadge(
    state: StreamingState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (state.status) {
        StreamingStatus.THINKING -> Color(0xFF1976D2)
        StreamingStatus.ACTING -> Color(0xFF388E3C)
        StreamingStatus.OBSERVING -> Color(0xFFFF9800)
        StreamingStatus.DONE -> Color(0xFF616161)
        StreamingStatus.ERROR -> Color(0xFFD32F2F)
    }

    Card(
        onClick = onExpand,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.White, CircleShape)
            )
            Text(
                " Step ${state.currentStep}",
                style = MaterialTheme.typography.Caption,
                color = Color.White
            )
        }
    }
}