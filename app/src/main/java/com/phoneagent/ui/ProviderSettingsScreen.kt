package com.phoneagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.phoneagent.agent.AgentController
import com.phoneagent.providers.CrofAiDefaults
import com.phoneagent.providers.ProviderConfig
import com.phoneagent.providers.ProviderRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsScreen(
    agentController: AgentController,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val providerRepository = remember { ProviderRepository(context) }
    val providers by providerRepository.providers.collectAsState(initial = emptyList())

    var selectedProviderId by remember { mutableStateOf(CrofAiDefaults.ID) }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(CrofAiDefaults.BASE_URL) }
    var streamEnabled by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(CrofAiDefaults.MODELS.first()) }

    LaunchedEffect(providers) {
        val provider = providers.find { it.id == selectedProviderId }
            ?: providers.firstOrNull()
            ?: return@LaunchedEffect
        baseUrl = provider.baseUrl
        streamEnabled = provider.streamEnabled
        selectedModel = provider.defaultModel ?: CrofAiDefaults.MODELS.first()
        apiKey = agentController.getApiKey(provider.id) ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Provider Settings") },
                navigationIcon = {
                    Button(onClick = onBack) {
                        Text("Back")
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Provider",
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            var providerExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = providerExpanded,
                onExpandedChange = { providerExpanded = it }
            ) {
                OutlinedTextField(
                    value = providers.find { it.id == selectedProviderId }?.name ?: "CrofAI",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false }
                ) {
                    providers.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.name) },
                            onClick = {
                                selectedProviderId = provider.id
                                baseUrl = provider.baseUrl
                                streamEnabled = provider.streamEnabled
                                selectedModel = provider.defaultModel ?: CrofAiDefaults.MODELS.first()
                                providerExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Default Model",
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            var modelExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = modelExpanded,
                onExpandedChange = { modelExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = modelExpanded,
                    onDismissRequest = { modelExpanded = false }
                ) {
                    CrofAiDefaults.MODELS.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                selectedModel = model
                                modelExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Streaming")
                Switch(
                    checked = streamEnabled,
                    onCheckedChange = { streamEnabled = it }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    scope.launch {
                        val updatedConfig = ProviderConfig(
                            id = selectedProviderId,
                            name = providers.find { it.id == selectedProviderId }?.name ?: CrofAiDefaults.NAME,
                            type = com.phoneagent.providers.ProviderType.OPENAI_COMPATIBLE,
                            baseUrl = baseUrl,
                            defaultModel = selectedModel,
                            availableModels = CrofAiDefaults.MODELS,
                            isEnabled = true,
                            streamEnabled = streamEnabled
                        )
                        providerRepository.saveProvider(updatedConfig)
                        agentController.storeApiKey(selectedProviderId, apiKey)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }
        }
    }
}
