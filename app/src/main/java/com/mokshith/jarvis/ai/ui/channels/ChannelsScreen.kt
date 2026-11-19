package com.mokshith.jarvis.ai.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mokshith.jarvis.ai.app.AppContainer
import com.mokshith.jarvis.ai.channels.TelegramChannelConnector
import com.mokshith.jarvis.ai.core.TimeUtils
import com.mokshith.jarvis.ai.security.KeystoreCredentialVault
import com.mokshith.jarvis.ai.services.TelegramPollingService
import com.mokshith.jarvis.ai.ui.components.HudPanel
import com.mokshith.jarvis.ai.ui.components.SectionHeader
import com.mokshith.jarvis.ai.ui.components.StatusPill
import com.mokshith.jarvis.ai.ui.theme.JarvisColors
import kotlinx.coroutines.launch

@Composable
fun ChannelsScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by container.database.channelDao().observeChannel(TelegramChannelConnector.CHANNEL_ID).collectAsState(initial = null)
    val runtime by container.telegramConnector.runtimeState.collectAsState()

    var botTokenInput by remember { mutableStateOf("") }
    var authorizedIds by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(config) {
        if (config != null) {
            authorizedIds = config!!.authorizedUserIdsCsv
            enabled = config!!.enabled
        }
    }

    val maskedToken = container.credentialVault.getMasked(KeystoreCredentialVault.KEY_TELEGRAM_BOT_TOKEN)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HudPanel(accentColor = if (runtime.running) JarvisColors.EmeraldOk else JarvisColors.PanelBorder) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Telegram Bot Channel", "Direct Bot API long polling • Zero external server")
                    StatusPill(
                        label = if (runtime.running) "POLLING" else "STOPPED",
                        color = if (runtime.running) JarvisColors.EmeraldOk else JarvisColors.TextSecondary
                    )
                }
                Text("Stored Token: $maskedToken", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = JarvisColors.TextSecondary)
                Text("Last Successful Poll: ${TimeUtils.formatTimestamp(config?.lastSuccessfulPollAt ?: runtime.lastPollAt)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = JarvisColors.CyanGlow)
                val err = runtime.lastError ?: config?.lastError
                if (!err.isNullOrBlank()) {
                    Text("Error: $err", fontSize = 12.sp, color = JarvisColors.CrimsonAccent)
                }
                if (feedback != null) {
                    Text(feedback.orEmpty(), fontSize = 12.sp, color = JarvisColors.CyanGlow)
                }
            }
        }

        item {
            HudPanel {
                SectionHeader("Configuration & Strict Allowlist")
                OutlinedTextField(
                    value = botTokenInput,
                    onValueChange = { botTokenInput = it },
                    label = { Text("Bot Token (leave blank to keep saved)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = authorizedIds,
                    onValueChange = { authorizedIds = it },
                    label = { Text("Authorized Telegram User IDs (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Explicitly Enable Telegram Channel", color = JarvisColors.TextPrimary)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                container.telegramConnector.saveConfiguration(botTokenInput, authorizedIds, enabled)
                                botTokenInput = ""
                                feedback = "Saved Telegram settings securely."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CyanGlow, contentColor = JarvisColors.OledBlack)
                    ) { Text("Save") }

                    if (!runtime.running) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                container.telegramConnector.saveConfiguration(botTokenInput, authorizedIds, enabled)
                                botTokenInput = ""
                                TelegramPollingService.start(context)
                                feedback = "Starting foreground Telegram polling service..."
                            }
                        }) { Text("Start Service") }
                    } else {
                        OutlinedButton(onClick = {
                            TelegramPollingService.stop(context)
                            feedback = "Stopped Telegram polling service."
                        }) { Text("Stop Service", color = JarvisColors.CrimsonAccent) }
                    }
                }
            }
        }

        item {
            HudPanel(accentColor = JarvisColors.AmberWarn.copy(alpha = 0.5f)) {
                SectionHeader("Android Background Execution Notice")
                Text(
                    text = "Telegram polling runs as a visible foreground service only while enabled. Android Doze, OEM battery savers, or force-stopping the app will stop background polling until you reopen JARVIS.",
                    fontSize = 12.sp,
                    color = JarvisColors.TextSecondary
                )
            }
        }
    }
}
