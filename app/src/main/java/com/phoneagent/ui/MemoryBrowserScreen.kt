package com.phoneagent.ui

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
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.AgentController
import com.phoneagent.memory.AppDatabase
import com.phoneagent.ui.components.StatusChip
import com.phoneagent.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryBrowserScreen(
    agentController: AgentController,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }

    val beliefs by db.beliefDao().getTopBeliefs(0.3, 30).collectAsState(initial = emptyList())
    val memories by db.somaMemoryDao().getTopMemories(30).collectAsState(initial = emptyList())
    val scenes by db.memSceneDao().getAll().collectAsState(initial = emptyList())
    val daemonLogs by db.daemonLogDao().getRecent(20).collectAsState(initial = emptyList())
    val lpm by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            scope.launch {
                lpm = db.lpmDao().getLpm()?.raw_profile?.takeLast(2000)
            }
        }
    }

    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory Browser", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background, titleContentColor = OnBackground
                )
            )
        },
        containerColor = Background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = Surface) {
                Tab(selected = tab == 0, onClick = { tab = 0 }) { Text("Beliefs", modifier = Modifier.padding(12.dp)) }
                Tab(selected = tab == 1, onClick = { tab = 1 }) { Text("Memories", modifier = Modifier.padding(12.dp)) }
                Tab(selected = tab == 2, onClick = { tab = 2 }) { Text("Scenes", modifier = Modifier.padding(12.dp)) }
                Tab(selected = tab == 3, onClick = { tab = 3 }) { Text("Daemon", modifier = Modifier.padding(12.dp)) }
                Tab(selected = tab == 4, onClick = { tab = 4 }) { Text("LPM", modifier = Modifier.padding(12.dp)) }
            }

            when (tab) {
                0 -> BeliefsTab(beliefs)
                1 -> MemoriesTab(memories)
                2 -> ScenesTab(scenes)
                3 -> DaemonTab(daemonLogs)
                4 -> LpmTab(lpm)
            }
        }
    }
}

@Composable
private fun BeliefsTab(beliefs: List<com.phoneagent.soma.entities.BeliefEntity>) {
    if (beliefs.isEmpty()) {
        EmptyState("No beliefs yet")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(beliefs) { belief ->
            BeliefCard(belief)
        }
    }
}

@Composable
private fun BeliefCard(belief: com.phoneagent.soma.entities.BeliefEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = CardShape
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusChip(text = belief.dimension, isActive = true)
                Text(
                    "${"%.0f".format(belief.confidence * 100)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (belief.confidence > 0.7) Success else Warning
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(belief.statement, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
            Text(
                "${belief.evidence_count} observations | source: ${belief.source}",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun MemoriesTab(memories: List<com.phoneagent.soma.entities.SomaMemoryEntity>) {
    if (memories.isEmpty()) {
        EmptyState("No memories yet")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(memories) { mem ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = CardShape
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(mem.content.take(300), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(text = "imp:${"%.1f".format(mem.importance)}", isActive = mem.importance > 0.5f)
                        StatusChip(text = "�${"%.1f".format(mem.emotional_weight)}", isActive = mem.emotional_weight > 0.5f)
                        StatusChip(text = "x${mem.activation_count}", isActive = mem.activation_count > 1)
                        if (mem.theme != null) StatusChip(text = mem.theme.take(15), isActive = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenesTab(scenes: List<com.phoneagent.soma.entities.MemSceneEntity>) {
    if (scenes.isEmpty()) {
        EmptyState("No scenes yet")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(scenes) { scene ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = CardShape
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(scene.theme, style = MaterialTheme.typography.titleSmall, color = Primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(scene.summary.take(500), style = MaterialTheme.typography.bodySmall, color = OnSurface)
                }
            }
        }
    }
}

@Composable
private fun DaemonTab(logs: List<com.phoneagent.soma.entities.DaemonLogEntity>) {
    if (logs.isEmpty()) {
        EmptyState("Daemon hasn't thought yet")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(logs) { log ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (log.pushed_to_user) SurfaceElevated else Surface),
                shape = CardShape
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatusChip(
                            text = if (log.pushed_to_user) "PUSHED" else "thought",
                            isActive = log.pushed_to_user
                        )
                        StatusChip(
                            text = "sig:${"%.2f".format(log.significance)}",
                            isActive = log.significance > 0.5f
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(log.thought.take(500), style = MaterialTheme.typography.bodySmall, color = OnSurface)
                }
            }
        }
    }
}

@Composable
private fun LpmTab(profile: String?) {
    if (profile.isNullOrBlank()) {
        EmptyState("LPM not yet formed. Have more conversations.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = CardShape
            ) {
                Text(
                    profile,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = OnSurfaceMuted, style = MaterialTheme.typography.bodyLarge)
    }
}
