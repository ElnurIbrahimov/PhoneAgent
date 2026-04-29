package com.phoneagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.AgentAction
import com.phoneagent.agent.AgentController
import com.phoneagent.agent.AgentStep
import com.phoneagent.agent.ToolResultParser
import com.phoneagent.ui.components.ConfirmationDialog
import com.phoneagent.ui.components.MessageBubble
import com.phoneagent.ui.components.StatusChip
import com.phoneagent.ui.components.TypingIndicator
import com.phoneagent.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    agentController: AgentController,
    onBack: () -> Unit
) {
    val uiState by agentController.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showSteps by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Chat",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (uiState.currentModel.isNotBlank()) {
                            Text(
                                text = uiState.currentModel,
                                style = MaterialTheme.typography.labelMedium,
                                color = OnSurfaceMuted
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OnBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { agentController.clearChat() }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear chat",
                            tint = OnBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = OnBackground
                )
            )
        },
        containerColor = Background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (uiState.messages.isEmpty() && !uiState.isLoading) {
                        EmptyChatState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(
                                items = uiState.messages,
                                key = { it.hashCode() }
                            ) { message ->
                                MessageBubble(message = message)
                            }
                            if (uiState.isLoading) {
                                item {
                                    TypingIndicator()
                                }
                            }
                        }
                    }
                }

                if (uiState.currentSteps.isNotEmpty()) {
                    StepViewer(
                        steps = uiState.currentSteps,
                        showSteps = showSteps,
                        onToggle = { showSteps = !showSteps }
                    )
                }

                AnimatedVisibility(
                    visible = uiState.error != null,
                    enter = fadeIn() + slideInVertically { it }
                ) {
                    uiState.error?.let { error ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Error.copy(alpha = 0.15f))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = Error
                            )
                        }
                    }
                }

                if (uiState.isLoading && uiState.agentStepStatus != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.agentStepStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceMuted
                        )
                    }
                }

                ChatInputBar(
                    value = messageText,
                    onValueChange = { messageText = it },
                    onSend = {
                        if (messageText.isNotBlank()) {
                            agentController.sendMessage(messageText)
                            messageText = ""
                        }
                    },
                    onVoice = { agentController.startVoiceInput() },
                    enabled = !uiState.isLoading && uiState.pendingConfirmation == null
                )
            }

            uiState.pendingConfirmation?.let { pending ->
                ConfirmationDialog(
                    pending = pending,
                    onApprove = { agentController.approvePendingAction() },
                    onCancel = { agentController.cancelPendingAction() }
                )
            }
        }
    }
}

@Composable
private fun EmptyChatState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Primary.copy(alpha = 0.3f), Secondary.copy(alpha = 0.3f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PA",
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnBackground,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "PhoneAgent",
                style = MaterialTheme.typography.titleLarge,
                color = OnBackground,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Start a conversation to begin",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    "Type a message...",
                    color = OnSurfaceDim
                )
            },
            enabled = enabled,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
                disabledContainerColor = Surface,
                focusedBorderColor = Primary,
                unfocusedBorderColor = SurfaceBorder,
                disabledBorderColor = SurfaceBorder,
                focusedTextColor = OnBackground,
                unfocusedTextColor = OnBackground,
                disabledTextColor = OnSurfaceDim
            ),
            maxLines = 4
        )

        IconButton(
            onClick = onVoice,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Surface)
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardVoice,
                contentDescription = "Voice input",
                tint = if (enabled) OnSurface else OnSurfaceDim
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(listOf(Primary, Secondary))
                )
                .clickable(enabled = enabled && value.isNotBlank()) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = OnBackground
            )
        }
    }
}

@Composable
private fun StepViewer(
    steps: List<AgentStep>,
    showSteps: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = SurfaceElevated
        ),
        shape = CardShape
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showSteps) "Hide steps" else "Show steps (${steps.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary
                )
                StatusChip(
                    text = "${steps.size} steps",
                    isActive = true
                )
            }
            if (showSteps) {
                Spacer(modifier = Modifier.height(12.dp))
                steps.forEachIndexed { index, step ->
                    StepRow(step = step)
                    if (index < steps.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = SurfaceBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: AgentStep) {
    Column {
        when (step.action) {
            is AgentAction.ToolCall -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Step ${step.stepNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceMuted
                    )
                    Text(
                        text = step.action.tool,
                        style = MaterialTheme.typography.labelMedium,
                        color = Secondary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (step.action.args.isNotEmpty()) {
                    Text(
                        text = step.action.args.entries.joinToString(", ") { "${it.key}=${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim,
                        maxLines = 2
                    )
                }
                val parsed = ToolResultParser.parse(step.observation.orEmpty())
                val success = parsed.success
                val error = !parsed.success && parsed.error != null
                Text(
                    text = when {
                        success -> "Completed"
                        error -> "Failed"
                        else -> "Running..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        success -> Success
                        error -> Error
                        else -> OnSurfaceDim
                    }
                )
                if (error) {
                    val short = parsed.error.orEmpty().take(200)
                    if (short.isNotBlank()) {
                        Text(
                            text = short,
                            style = MaterialTheme.typography.bodySmall,
                            color = Error,
                            maxLines = 2
                        )
                    }
                }
            }
            is AgentAction.FinalAnswer -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Step ${step.stepNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceMuted
                    )
                    Text(
                        text = "Final Answer",
                        style = MaterialTheme.typography.labelMedium,
                        color = Success,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = step.action.content.take(200),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface
                )
            }
            is AgentAction.ParseError -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Step ${step.stepNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceMuted
                    )
                    Text(
                        text = "Parse Error",
                        style = MaterialTheme.typography.labelMedium,
                        color = Error,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = step.action.reason.take(200),
                    style = MaterialTheme.typography.bodySmall,
                    color = Error
                )
            }
        }
    }
}
