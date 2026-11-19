package com.mokshith.jarvis.ai.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

enum class JarvisRoute(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val inBottomBar: Boolean
) {
    HOME("home", "Home", Icons.Default.Home, true),
    CHAT("chat", "Chat", Icons.Default.Chat, true),
    TASKS("tasks", "Agent", Icons.Default.AccountTree, true),
    MEMORY("memory", "Memory", Icons.Default.Memory, true),
    MODELS("models", "Gateway", Icons.Default.Hub, true),
    CHANNELS("channels", "Channels", Icons.Default.Send, false),
    ACTIVITY("activity", "Activity", Icons.Default.Terminal, false),
    SETTINGS("settings", "Settings", Icons.Default.Settings, false)
}
