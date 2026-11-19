package com.jarvis.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JarvisApp() {
    var selected by remember { mutableStateOf("Home") }
    val destinations = listOf("Home", "Chat", "Tasks", "Memory", "Gateway", "Channels", "Activity", "Settings")

    Scaffold(
        containerColor = Color(0xFF05070A),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0B1015)) {
                destinations.take(4).forEach { item ->
                    NavigationBarItem(
                        selected = selected == item,
                        onClick = { selected = item },
                        icon = { Text(item.take(1), color = if (selected == item) Color(0xFF00D9FF) else Color.Gray) },
                        label = { Text(item) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("JARVIS", color = Color(0xFF00D9FF), fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text("ALPHA • LOCAL COMMAND CENTER", color = Color(0xFF72818B), fontSize = 10.sp, letterSpacing = 2.sp)
                }
                Box(
                    modifier = Modifier.size(12.dp).background(Color(0xFF00D9FF), RoundedCornerShape(50))
                )
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101820)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SYSTEM STATUS", color = Color(0xFF8195A3), fontSize = 11.sp, letterSpacing = 2.sp)
                    Text("Core initialized", color = Color(0xFFEAF5F8), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("Foundation is online. AI provider setup is required to enable live inference.", color = Color(0xFF9AAAB4), fontSize = 14.sp)
                }
            }
            Text("COMMAND MODULES", color = Color(0xFF8195A3), fontSize = 11.sp, letterSpacing = 2.sp)
            destinations.drop(1).forEach { item ->
                Card(
                    onClick = { selected = item },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10151B)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.uppercase(), color = Color(0xFFEAF5F8), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("›", color = Color(0xFF00D9FF), fontSize = 24.sp)
                    }
                }
            }
            Text("Selected: $selected", color = Color(0xFF00D9FF), fontSize = 12.sp)
        }
    }
}
