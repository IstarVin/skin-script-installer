package com.istarvin.skinscriptinstaller.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.istarvin.skinscriptinstaller.ui.components.UpdateAvailableDialog
import com.istarvin.skinscriptinstaller.ui.screens.detail.ScriptDetailScreen
import com.istarvin.skinscriptinstaller.ui.screens.list.ScriptListScreen
import com.istarvin.skinscriptinstaller.ui.screens.settings.SettingsScreen
import com.istarvin.skinscriptinstaller.ui.screens.settings.UpdateCheckViewModel
import com.istarvin.skinscriptinstaller.ui.screens.settings.UpdateState
import com.istarvin.skinscriptinstaller.ui.screens.catalogeditor.CatalogEditorScreen
import androidx.core.net.toUri

@Composable
fun AppNavHost(navController: NavHostController) {
    val context = LocalContext.current
    val updateViewModel: UpdateCheckViewModel = hiltViewModel()
    val updateState by updateViewModel.updateState.collectAsState()

    if (updateState is UpdateState.UpdateAvailable) {
        val state = updateState as UpdateState.UpdateAvailable
        UpdateAvailableDialog(
            version = state.version,
            releaseNotes = state.releaseNotes,
            onOpenReleasePage = {
                val intent = Intent(Intent.ACTION_VIEW, state.releaseUrl.toUri())
                context.startActivity(intent)
            },
            onDismiss = { updateViewModel.dismissUpdate() }
        )
    }

    NavHost(
        navController = navController,
        startDestination = Screen.ScriptList.route
    ) {
        composable(Screen.ScriptList.route) {
            ScriptListScreen(
                onScriptClick = { scriptId ->
                    navController.navigate(Screen.ScriptDetail.createRoute(scriptId))
                },
                onScriptClickAutoClassify = { scriptId ->
                    navController.navigate(Screen.ScriptDetail.createRoute(scriptId, autoClassify = true))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.ScriptDetail.route,
            arguments = listOf(
                navArgument("scriptId") { type = NavType.LongType },
                navArgument("autoClassify") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val autoClassify = backStackEntry.arguments?.getBoolean("autoClassify") ?: false
            ScriptDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                autoClassify = autoClassify
            )
        }

        composable(Screen.Settings.route) {
            val isCheckingForUpdates = updateState == UpdateState.Checking
            val updateCheckMessage = when (val s = updateState) {
                is UpdateState.Checking -> "Checking for updates..."
                is UpdateState.UpToDate -> "App is up to date"
                is UpdateState.Error -> s.message
                else -> null
            }
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onCheckForUpdates = { updateViewModel.checkForUpdate() },
                isCheckingForUpdates = isCheckingForUpdates,
                updateCheckMessage = updateCheckMessage,
                onEditCatalog = {
                    navController.navigate(Screen.CatalogEditor.route)
                }
            )
        }

        composable(Screen.CatalogEditor.route) {
            CatalogEditorScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

