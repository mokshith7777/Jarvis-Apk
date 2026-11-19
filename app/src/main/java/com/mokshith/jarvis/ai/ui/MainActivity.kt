package com.mokshith.jarvis.ai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mokshith.jarvis.ai.app.AppContainer
import com.mokshith.jarvis.ai.app.JarvisApplication
import com.mokshith.jarvis.ai.ui.activity.ActivityScreen
import com.mokshith.jarvis.ai.ui.channels.ChannelsScreen
import com.mokshith.jarvis.ai.ui.chat.ChatScreen
import com.mokshith.jarvis.ai.ui.home.HomeScreen
import com.mokshith.jarvis.ai.ui.memory.MemoryScreen
import com.mokshith.jarvis.ai.ui.models.GatewayScreen
import com.mokshith.jarvis.ai.ui.navigation.JarvisRoute
import com.mokshith.jarvis.ai.ui.settings.SettingsScreen
import com.mokshith.jarvis.ai.ui.tasks.TasksScreen
import com.mokshith.jarvis.ai.ui.theme.JarvisColors
import com.mokshith.jarvis.ai.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as JarvisApplication).container
        setContent {
            JarvisTheme {
                JarvisAppShell(container)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JarvisAppShell(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: JarvisRoute.HOME.route

    fun navigateTo(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = JarvisColors.OledBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "J.A.R.V.I.S  //  ALPHA",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = JarvisColors.CyanGlow
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JarvisColors.OledBlack,
                    titleContentColor = JarvisColors.CyanGlow
                ),
                actions = {
                    IconButton(onClick = { navigateTo(JarvisRoute.CHANNELS.route) }) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Channels",
                            tint = if (currentRoute == JarvisRoute.CHANNELS.route) JarvisColors.CyanGlow else JarvisColors.TextSecondary
                        )
                    }
                    IconButton(onClick = { navigateTo(JarvisRoute.ACTIVITY.route) }) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = "Activity Log",
                            tint = if (currentRoute == JarvisRoute.ACTIVITY.route) JarvisColors.CyanGlow else JarvisColors.TextSecondary
                        )
                    }
                    IconButton(onClick = { navigateTo(JarvisRoute.SETTINGS.route) }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (currentRoute == JarvisRoute.SETTINGS.route) JarvisColors.CyanGlow else JarvisColors.TextSecondary
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = JarvisColors.CharcoalSurface) {
                JarvisRoute.entries.filter { it.inBottomBar }.forEach { dest ->
                    val selected = currentRoute == dest.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTo(dest.route) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JarvisColors.CyanGlow,
                            selectedTextColor = JarvisColors.CyanGlow,
                            indicatorColor = JarvisColors.CyanDim.copy(alpha = 0.4f),
                            unselectedIconColor = JarvisColors.TextSecondary,
                            unselectedTextColor = JarvisColors.TextSecondary
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = JarvisRoute.HOME.route) {
                composable(JarvisRoute.HOME.route) {
                    HomeScreen(container = container, onNavigate = ::navigateTo)
                }
                composable(JarvisRoute.CHAT.route) {
                    ChatScreen(container = container, onNavigate = ::navigateTo)
                }
                composable(JarvisRoute.TASKS.route) {
                    TasksScreen(container = container)
                }
                composable(JarvisRoute.MEMORY.route) {
                    MemoryScreen(container = container)
                }
                composable(JarvisRoute.MODELS.route) {
                    GatewayScreen(container = container)
                }
                composable(JarvisRoute.CHANNELS.route) {
                    ChannelsScreen(container = container)
                }
                composable(JarvisRoute.ACTIVITY.route) {
                    ActivityScreen(container = container)
                }
                composable(JarvisRoute.SETTINGS.route) {
                    SettingsScreen(container = container)
                }
            }
        }
    }
}
