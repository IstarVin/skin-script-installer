package com.istarvin.skinscriptinstaller.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.istarvin.skinscriptinstaller.ui.screens.detail.ScriptDetailScreen
import com.istarvin.skinscriptinstaller.ui.screens.list.ScriptListScreen
import com.istarvin.skinscriptinstaller.ui.screens.settings.SettingsScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.ScriptList.route
    ) {
        composable(Screen.ScriptList.route) {
            ScriptListScreen(
                onScriptClick = { scriptId ->
                    navController.navigate(Screen.ScriptDetail.createRoute(scriptId))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.ScriptDetail.route,
            arguments = listOf(
                navArgument("scriptId") { type = NavType.LongType }
            )
        ) {
            ScriptDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

