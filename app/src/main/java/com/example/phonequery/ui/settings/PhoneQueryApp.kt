package com.example.phonequery.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.phonequery.R
import com.example.phonequery.ui.PhoneQueryScreen
import com.example.phonequery.ui.PhoneQueryViewModel
import com.example.phonequery.ui.help.HelpScreen
import com.example.phonequery.ui.setup.PermissionGuideScreen

sealed class Screen(val route: String, val titleRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", R.string.btn_query, Icons.Default.Home)
    object Settings : Screen("settings", R.string.settings_title, Icons.Default.Settings)
    object Help : Screen("help", R.string.help_title, Icons.Default.Help)
}

@Composable
fun PhoneQueryApp(
    queryViewModel: PhoneQueryViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Settings, Screen.Help)

    // 首次启动自动进入授权引导页（看过一次后不再自动弹）
    val hasSeenGuide by settingsViewModel.hasSeenSetupGuide.collectAsState(initial = true)
    LaunchedEffect(hasSeenGuide) {
        if (!hasSeenGuide) {
            navController.navigate("guide")
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.titleRes)) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                PhoneQueryScreen(viewModel = queryViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToBlocklist = {
                        navController.navigate("blocklist")
                    },
                    onNavigateToSetupGuide = {
                        navController.navigate("guide")
                    },
                    onNavigateHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable("blocklist") {
                BlocklistScreen(
                    viewModel = settingsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("guide") {
                PermissionGuideScreen(
                    onDone = { navController.popBackStack() },
                    onMarkSeen = { settingsViewModel.markSetupGuideSeen() }
                )
            }
            composable(Screen.Help.route) {
                HelpScreen()
            }
        }
    }
}