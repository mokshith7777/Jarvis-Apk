package com.mokshith.jarvis.ai.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mokshith.jarvis.ai.BuildConfig
import com.mokshith.jarvis.ai.app.AppContainer
import com.mokshith.jarvis.ai.memory.JarvisAppSettings
import com.mokshith.jarvis.ai.security.KeystoreCredentialVault
import com.mokshith.jarvis.ai.ui.components.HudPanel
import com.mokshith.jarvis.ai.ui.components.SectionHeader
import com.mokshith.jarvis.ai.ui.theme.JarvisColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsStore.settingsFlow.collectAsState(initial = JarvisAppSettings())
    val toolPolicies by container.permissionPolicyManager.policies.collectAsState()

    var githubPat by remember { mutableStateOf("") }
    var statusNote by remember { mutableStateOf<String?>(null) }

    val safPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            scope.launch {
                container.settingsStore.updateSafTreeUri(uri.toString())
                statusNote = "Granted SAF folder access."
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (statusNote != null) {
            item {
                HudPanel(accentColor = JarvisColors.CyanGlow) {
                    Text(statusNote.orEmpty(), color = JarvisColors.CyanGlow, fontSize = 12.sp)
                }
            }
        }

        item {
            HudPanel {
                SectionHeader("Agent, Battery & Network")
                ToggleRow("Adaptive Battery Mode (pause tasks on low battery)", settings.adaptiveBatteryMode) {
                    scope.launch { container.settingsStore.updateAdaptiveBattery(it) }
                }
                ToggleRow("Wi-Fi Only for Background Tasks", settings.wifiOnlyBackgroundTasks) {
                    scope.launch { container.settingsStore.updateWifiOnlyBackground(it) }
                }
                ToggleRow("Task Completion Notifications", settings.notificationsEnabled) {
                    scope.launch { container.settingsStore.updateNotifications(it) }
                }
                ToggleRow("Inject Relevant Local Memory into Prompts", settings.memoryAutoInjectionEnabled) {
                    scope.launch { container.settingsStore.updateMemoryAutoInjection(it) }
                }
                ToggleRow("Strict Privacy Mode", settings.strictPrivacyMode) {
                    scope.launch { container.settingsStore.updateStrictPrivacy(it) }
                }
                Text("Max Agent Steps per Run: ${settings.maxAgentSteps}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = JarvisColors.CyanGlow)
                Slider(
                    value = settings.maxAgentSteps.toFloat(),
                    onValueChange = { v -> scope.launch { container.settingsStore.updateMaxAgentSteps(v.toInt()) } },
                    valueRange = 2f..16f,
                    steps = 13
                )
            }
        }

        item {
            HudPanel {
                SectionHeader("Tool Permissions", "Enable/disable tools or require explicit confirmation")
                container.toolRegistry.allTools().forEach { tool ->
                    val state = toolPolicies[tool.id]
                    val enabled = state?.enabled ?: container.permissionPolicyManager.isToolEnabled(tool.id)
                    val confirm = state?.requireConfirmation ?: false
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ToggleRow("${tool.name} (${tool.id})", enabled) {
                            container.permissionPolicyManager.setToolEnabled(tool.id, it)
                        }
                        ToggleRow("  Require approval before running", confirm) {
                            container.permissionPolicyManager.setRequireConfirmation(tool.id, it)
                        }
                    }
                }
            }
        }

        item {
            HudPanel {
                SectionHeader("Local Files (Storage Access Framework)")
                Text(
                    text = settings.selectedSafTreeUri ?: "No folder granted yet.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = JarvisColors.TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { safPicker.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CyanGlow, contentColor = JarvisColors.OledBlack)
                    ) { Text("Grant Folder") }
                    if (settings.selectedSafTreeUri != null) {
                        OutlinedButton(onClick = { scope.launch { container.settingsStore.updateSafTreeUri(null) } }) {
                            Text("Revoke")
                        }
                    }
                }
            }
        }

        item {
            HudPanel {
                SectionHeader("GitHub Integration (Optional PAT)")
                Text(
                    text = "Current Token: ${container.credentialVault.getMasked(KeystoreCredentialVault.KEY_GITHUB_PAT)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = JarvisColors.TextSecondary
                )
                OutlinedTextField(
                    value = githubPat,
                    onValueChange = { githubPat = it },
                    label = { Text("GitHub Personal Access Token") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (githubPat.isNotBlank()) {
                                container.credentialVault.putSecret(KeystoreCredentialVault.KEY_GITHUB_PAT, githubPat)
                                githubPat = ""
                                statusNote = "Saved encrypted GitHub PAT."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CyanGlow, contentColor = JarvisColors.OledBlack)
                    ) { Text("Save PAT") }
                    OutlinedButton(onClick = {
                        container.credentialVault.removeSecret(KeystoreCredentialVault.KEY_GITHUB_PAT)
                        statusNote = "Removed GitHub PAT."
                    }) { Text("Clear PAT") }
                }
            }
        }

        item {
            HudPanel(accentColor = JarvisColors.CrimsonAccent.copy(alpha = 0.5f)) {
                SectionHeader("Privacy & Vault")
                OutlinedButton(onClick = {
                    container.credentialVault.clearAll()
                    statusNote = "Cleared all encrypted credentials from Android Keystore vault."
                }) { Text("Wipe All Stored Credentials", color = JarvisColors.CrimsonAccent) }
            }
        }

        item {
            HudPanel {
                SectionHeader("App Information")
                Text("JARVIS Alpha • Native Android (Kotlin + Jetpack Compose)", color = JarvisColors.TextPrimary, fontSize = 13.sp)
                Text("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = JarvisColors.TextSecondary)
                Text("Package: ${BuildConfig.APPLICATION_ID}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = JarvisColors.TextSecondary)
                Text("Self-contained single APK • No external backend • Keystore AES-256-GCM", fontSize = 12.sp, color = JarvisColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = JarvisColors.TextPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
