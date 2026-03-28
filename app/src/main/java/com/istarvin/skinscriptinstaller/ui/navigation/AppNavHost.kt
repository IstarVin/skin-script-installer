package com.istarvin.skinscriptinstaller.ui.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.istarvin.skinscriptinstaller.ui.components.UpdateAvailableDialog
import com.istarvin.skinscriptinstaller.ui.screens.catalogeditor.CatalogEditorScreen
import com.istarvin.skinscriptinstaller.ui.screens.detail.ScriptDetailScreen
import com.istarvin.skinscriptinstaller.ui.screens.list.ScriptListScreen
import com.istarvin.skinscriptinstaller.ui.screens.settings.SettingsScreen
import com.istarvin.skinscriptinstaller.ui.screens.settings.UpdateCheckViewModel
import com.istarvin.skinscriptinstaller.ui.screens.settings.UpdateEvent
import com.istarvin.skinscriptinstaller.ui.screens.settings.UpdateState

@Composable
fun AppNavHost(navController: NavHostController) {
    val context = LocalContext.current
    val updateViewModel: UpdateCheckViewModel = hiltViewModel()
    val updateState by updateViewModel.updateState.collectAsState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            updateViewModel.startUpdate()
        } else {
            updateViewModel.onNotificationPermissionDenied()
        }
    }

    LaunchedEffect(updateViewModel, context) {
        updateViewModel.events.collect { event ->
            when (event) {
                is UpdateEvent.OpenReleasePage -> {
                    context.startActivity(Intent(Intent.ACTION_VIEW, event.releaseUrl.toUri()))
                }
            }
        }
    }

    if (updateState is UpdateState.UpdateAvailable) {
        val state = updateState as UpdateState.UpdateAvailable
        UpdateAvailableDialog(
            version = state.version,
            releaseNotes = state.releaseNotes,
            isDirectInstallSupported = !state.apkUrl.isNullOrBlank(),
            onPrimaryAction = {
                if (state.apkUrl.isNullOrBlank()) {
                    updateViewModel.startUpdate()
                } else if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    updateViewModel.startUpdate()
                }
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
            val activeDownload = updateState as? UpdateState.Downloading
            val isUpdateActionRunning =
                updateState == UpdateState.Checking || activeDownload != null
            val updateActionLabel = when (updateState) {
                is UpdateState.Downloading -> {
                    val progressPercent = activeDownload?.progressPercent
                    if (progressPercent != null) {
                        "Downloading update... $progressPercent%"
                    } else {
                        "Downloading update..."
                    }
                }

                else -> "Checking for updates..."
            }
            val updateCheckMessage = when (val s = updateState) {
                is UpdateState.Checking -> "Checking for updates..."
                is UpdateState.Downloading -> {
                    val progressPercent = s.progressPercent
                    if (progressPercent != null) {
                        "Downloading version ${s.version} in the background... $progressPercent%"
                    } else {
                        "Downloading version ${s.version} in the background..."
                    }
                }

                is UpdateState.Downloaded -> {
                    "Version ${s.version} downloaded. Tap the notification to install."
                }

                is UpdateState.UpToDate -> "App is up to date"
                is UpdateState.Error -> s.message
                else -> null
            }
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onCheckForUpdates = { updateViewModel.checkForUpdate() },
                isUpdateActionRunning = isUpdateActionRunning,
                updateActionLabel = updateActionLabel,
                updateCheckMessage = updateCheckMessage,
                updateProgress = activeDownload?.progressFraction,
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
