package com.mokshith.jarvis.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mokshith.jarvis.ai.ui.theme.JarvisColors

@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    accentColor: Color = JarvisColors.PanelBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisColors.CharcoalSurface),
        border = BorderStroke(1.dp, accentColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun StatusPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = color,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = JarvisColors.CyanGlow,
            fontWeight = FontWeight.Bold
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = JarvisColors.TextSecondary
            )
        }
    }
}

/**
 * Renders assistant markdown cleanly including fenced code blocks in monospace containers.
 */
@Composable
fun MarkdownTextBlock(content: String, modifier: Modifier = Modifier) {
    val segments = splitCodeBlocks(content)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { (isCode, body) ->
            if (isCode) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF070B10),
                    border = BorderStroke(1.dp, JarvisColors.CyanDim)
                ) {
                    Text(
                        text = body.trim(),
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = JarvisColors.CyanGlow
                    )
                }
            } else if (body.isNotBlank()) {
                Text(
                    text = body.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisColors.TextPrimary
                )
            }
        }
    }
}

private fun splitCodeBlocks(text: String): List<Pair<Boolean, String>> {
    val parts = text.split("```")
    return parts.mapIndexed { index, part ->
        val isCode = index % 2 == 1
        val cleaned = if (isCode) part.substringAfter("\n", part) else part
        isCode to cleaned
    }
}
