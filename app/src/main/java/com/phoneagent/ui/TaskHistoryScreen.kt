package com.phoneagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.AgentController
import com.phoneagent.memory.TaskEntity
import com.phoneagent.ui.components.StatusChip
import com.phoneagent.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHistoryScreen(
    agentController: AgentController,
    onBack: () -> Unit
) {
    val tasks by agentController.taskHistoryManager.tasks.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var expandedTaskId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task History", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { agentController.taskHistoryManager.clearHistory() }
                    }) {
                        Icon(Icons.Filled.Delete, "Clear all", tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background, titleContentColor = OnBackground
                )
            )
        },
        containerColor = Background
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No tasks yet.", color = OnSurfaceMuted, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        isExpanded = expandedTaskId == task.id,
                        onToggle = {
                            expandedTaskId = if (expandedTaskId == task.id) null else task.id
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = CardShape
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.description.take(80),
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnBackground,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusChip(
                    text = task.status.replaceFirstChar { it.uppercase() },
                    isActive = task.status == "running"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateFormat.format(Date(task.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceMuted
                )
                if (task.result != null) {
                    Text(
                        text = task.result.take(60).replace("\n", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (task.result != null) {
                        Text("Result:", style = MaterialTheme.typography.labelMedium, color = OnSurfaceMuted)
                        Text(
                            text = task.result,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (!task.stepsJson.isNullOrBlank()) {
                        Text("Steps:", style = MaterialTheme.typography.labelMedium, color = OnSurfaceMuted)
                        Text(
                            text = task.stepsJson.take(1000),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDim
                        )
                    }
                }
            }
        }
    }
}
