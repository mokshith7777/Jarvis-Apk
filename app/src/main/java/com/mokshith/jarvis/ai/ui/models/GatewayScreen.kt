package com.mokshith.jarvis.ai.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mokshith.jarvis.ai.app.AppContainer
import com.mokshith.jarvis.ai.gateway.ProviderType
import com.mokshith.jarvis.ai.memory.ProviderConfigEntity
import com.mokshith.jarvis.ai.security.KeystoreCredentialVault
import com.mokshith.jarvis.ai.ui.components.HudPanel
import com.mokshith.jarvis.ai.ui.components.SectionHeader
import com.mokshith.jarvis.ai.ui.components.StatusPill
import com.mokshith.jarvis.ai.ui.theme.JarvisColors
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun GatewayScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val providers by container.aiGateway.observeProviders().collectAsState(initial = emptyList())
    val usage by container.aiGateway.observeUsage(100).collectAsState(initial = emptyList())

    var selectedType by remember { mutableStateOf(ProviderType.GROQ) }
    var displayName by remember { mutableStateOf(ProviderType.GROQ.displayName) }
    var baseUrl by remember { mutableStateOf(ProviderType.GROQ.defaultBaseUrl) }
    var defaultModel by remember { mutableStateOf(ProviderType.GROQ.suggestedDefaultModel) }
    var priorityStr by remember { mutableStateOf("1") }
    var apiKeyInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var manualModelInput by remember { mutableStateOf("") }

    val totalCalls = usage.size
    val successCalls = usage.count { it.success }
    val avgLatency = if (usage.isEmpty()) 0L else usage.map { it.latencyMs }.average().toLong()
    val totalTokens = usage.sumOf { it.totalTokens }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HudPanel(accentColor = JarvisColors.CyanDim) {
                SectionHeader("Gateway Telemetry", "On-device failover router • Android Keystore encrypted keys")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("CALLS $totalCalls", JarvisColors.CyanGlow)
                    StatusPill("OK $successCalls", JarvisColors.EmeraldOk)
                    StatusPill("AVG ${avgLatency}ms", JarvisColors.AmberWarn)
                }
                Text("Tracked Tokens: $totalTokens", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = JarvisColors.TextSecondary)
                if (statusMessage != null) {
                    Text(statusMessage.orEmpty(), fontSize = 12.sp, color = JarvisColors.CyanGlow)
                }
            }
        }

        item {
            HudPanel {
                SectionHeader("Add / Configure AI Provider")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ProviderType.entries) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                displayName = type.displayName
                                baseUrl = type.defaultBaseUrl
                                defaultModel = type.suggestedDefaultModel
                            },
                            label = { Text(type.displayName) }
                        )
                    }
                }
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("API Base URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = defaultModel, onValueChange = { defaultModel = it }, label = { Text("Default Model ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API Key (Encrypted in Android Keystore)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(value = priorityStr, onValueChange = { priorityStr = it }, label = { Text("Failover Priority (1 = highest)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(
                    onClick = {
                        if (displayName.isNotBlank() && baseUrl.isNotBlank() && apiKeyInput.isNotBlank()) {
                            scope.launch {
                                val id = "${selectedType.name.lowercase()}_${UUID.randomUUID().toString().take(6)}"
                                container.aiGateway.saveProvider(
                                    config = ProviderConfigEntity(
                                        id = id,
                                        type = selectedType.name,
                                        displayName = displayName.trim(),
                                        baseUrl = baseUrl.trim(),
                                        defaultModelId = defaultModel.trim(),
                                        priority = priorityStr.toIntOrNull() ?: 10,
                                        enabled = true,
                                        isDefault = providers.isEmpty()
                                    ),
                                    apiKey = apiKeyInput.trim(),
                                    setAsDefault = providers.isEmpty()
                                )
                                apiKeyInput = ""
                                statusMessage = "Saved ${displayName.trim()} with encrypted credential."
                            }
                        } else {
                            statusMessage = "Display Name, Base URL, and API Key are required."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CyanGlow, contentColor = JarvisColors.OledBlack)
                ) { Text("Save Provider") }
            }
        }

        items(providers, key = { it.id }) { p ->
            val masked = container.credentialVault.getMasked(KeystoreCredentialVault.providerKey(p.id))
            val models = container.aiGateway.decodeModels(p.cachedModelsJson)
            HudPanel(accentColor = if (p.isDefault) JarvisColors.CyanGlow else JarvisColors.PanelBorder) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${p.displayName}${if (p.isDefault) " ★" else ""}", fontWeight = FontWeight.Bold, color = JarvisColors.TextPrimary)
                    StatusPill(
                        label = p.lastHealthStatus,
                        color = when (p.lastHealthStatus) {
                            "HEALTHY" -> JarvisColors.EmeraldOk
                            "ERROR" -> JarvisColors.CrimsonAccent
                            else -> JarvisColors.TextSecondary
                        }
                    )
                }
                Text("Model: ${p.defaultModelId} • Priority: ${p.priority} • Latency: ${p.lastLatencyMs}ms", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = JarvisColors.CyanGlow)
                Text("Key: $masked • Cached models: ${models.size}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = JarvisColors.TextSecondary)
                if (!p.lastError.isNullOrBlank()) {
                    Text("Last Error: ${p.lastError}", fontSize = 12.sp, color = JarvisColors.CrimsonAccent)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            statusMessage = "Testing ${p.displayName}..."
                            val res = container.aiGateway.testConnection(p.id)
                            statusMessage = "${p.displayName}: ${res.message}"
                        }
                    }) { Text("Test & Discover") }
                    if (!p.isDefault) {
                        OutlinedButton(onClick = { scope.launch { container.aiGateway.setDefaultProvider(p.id) } }) {
                            Text("Set Default")
                        }
                    }
                    OutlinedButton(onClick = { scope.launch { container.aiGateway.deleteProvider(p.id) } }) {
                        Text("Remove", color = JarvisColors.CrimsonAccent)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = manualModelInput,
                        onValueChange = { manualModelInput = it },
                        label = { Text("Add manual model ID") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedButton(onClick = {
                        if (manualModelInput.isNotBlank()) {
                            scope.launch {
                                container.aiGateway.addManualModel(p.id, manualModelInput)
                                manualModelInput = ""
                                statusMessage = "Added manual model to ${p.displayName}."
                            }
                        }
                    }) { Text("Add") }
                }
            }
        }
    }
}
