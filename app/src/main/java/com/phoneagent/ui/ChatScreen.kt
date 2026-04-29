package com.phoneagent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.AgentAction
import com.phoneagent.agent.AgentController
import com.phoneagent.agent.AgentStep
import com.phoneagent.agent.ToolResultParser

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

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    uiState.pendingConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { agentController.cancelPendingAction() },
            title = { Text("Confirm sensitive action") },
            text = {
                Column {
                    Text("The agent wants to run a potentially sensitive action.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tool: ${pending.toolName}", style = MaterialTheme.typography.bodyMedium)
                    Text("Args: ${pending.args}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Reason: ${pending.reason}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = { agentController.approvePendingAction() }) {
                    Text("Approve and run")
                }
            },
            dismissButton = {
                Button(onClick = { agentController.cancelPendingAction() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    Button(onClick = onBack) {
                        Text("Back")
                    }
                },
                actions = {
                    Button(onClick = { agentController.clearChat() }) {
                        Text("Clear")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg.role.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = msg.content,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    uiState.agentStepStatus?.let { status ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uiState.currentSteps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSteps = !showSteps },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (showSteps) "Hide steps" else "Show steps (${uiState.currentSteps.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (showSteps) {
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.currentSteps.forEach { step ->
                                StepRow(step = step)
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    enabled = !uiState.isLoading && uiState.pendingConfirmation == null
                )
                Button(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            agentController.sendMessage(messageText)
                            messageText = ""
                        }
                    },
                    enabled = messageText.isNotBlank() && !uiState.isLoading && uiState.pendingConfirmation == null
                ) {
                    Text("Send")
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
                Text(
                    text = "Step ${step.stepNumber}: ${step.action.tool}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "Args: ${step.action.args}",
                    style = MaterialTheme.typography.bodySmall
                )
                val parsed = ToolResultParser.parse(step.observation.orEmpty())
                val success = parsed.success
                val error = !parsed.success && parsed.error != null
                Text(
                    text = when {
                        success -> "Result: succeeded"
                        error -> "Result: failed"
                        else -> "Result: pending"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        success -> MaterialTheme.colorScheme.primary
                        error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (error) {
                    val short = parsed.error.orEmpty().take(200)
                    if (short.isNotBlank()) {
                        Text(
                            text = "Error: $short",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            is AgentAction.FinalAnswer -> {
                Text(
                    text = "Step ${step.stepNumber}: final answer",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = step.action.content.take(200),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is AgentAction.ParseError -> {
                Text(
                    text = "Step ${step.stepNumber}: parse error",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = step.action.reason.take(200),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
