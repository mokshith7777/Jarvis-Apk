package com.mokshith.jarvis.ai.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mokshith.jarvis.ai.app.AppContainer
import com.mokshith.jarvis.ai.core.TimeUtils
import com.mokshith.jarvis.ai.ui.components.HudPanel
import com.mokshith.jarvis.ai.ui.components.SectionHeader
import com.mokshith.jarvis.ai.ui.components.StatusPill
import com.mokshith.jarvis.ai.ui.theme.JarvisColors
import kotlinx.coroutines.launch

@Composable
fun ActivityScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val records by container.database.toolExecutionDao().observeRecent(100).collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionHeader("Tool Execution Audit Log", "Credentials automatically redacted")
                if (records.isNotEmpty()) {
                    OutlinedButton(onClick = { scope.launch { container.database.toolExecutionDao().clearAll() } }) {
                        Text("Clear")
                    }
                }
            }
        }
        if (records.isEmpty()) {
            item {
                HudPanel { Text("No tool activity recorded yet.", color = JarvisColors.TextSecondary) }
            }
        } else {
            items(records, key = { it.id }) { rec ->
                HudPanel(accentColor = if (rec.success) JarvisColors.PanelBorder else JarvisColors.CrimsonAccent.copy(alpha = 0.6f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(rec.toolName, fontWeight = FontWeight.Bold, color = JarvisColors.TextPrimary)
                        StatusPill(
                            label = if (rec.success) "OK ${rec.durationMs}ms" else "FAILED",
                            color = if (rec.success) JarvisColors.EmeraldOk else JarvisColors.CrimsonAccent
                        )
                    }
                    Text("Args: ${rec.argumentsSummary}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = JarvisColors.CyanGlow)
                    Text(rec.resultSummary, fontSize = 12.sp, color = JarvisColors.TextSecondary)
                    Text(TimeUtils.formatTimestamp(rec.createdAt), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = JarvisColors.TextSecondary)
                }
            }
        }
    }
}
