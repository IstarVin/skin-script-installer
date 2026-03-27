package com.istarvin.skinscriptinstaller.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

object SettingsTestTags {
    const val ShizukuBlockingNotice = "settings-shizuku-blocking-notice"
    const val ShizukuPrimaryAction = "settings-shizuku-primary-action"
    const val MaintenanceBackup = "settings-maintenance-backup"
    const val MaintenanceCatalog = "settings-maintenance-catalog"
    const val MaintenanceUpdates = "settings-maintenance-updates"
}

private const val UpToDateMessage = "App is up to date"
private const val UpdateMessageDismissDelayMillis = 4_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onCheckForUpdates: () -> Unit = {},
    isCheckingForUpdates: Boolean = false,
    updateCheckMessage: String? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsState()
    val isPermissionGranted by viewModel.isPermissionGranted.collectAsState()
    val isServiceBound by viewModel.isServiceBound.collectAsState()
    val isBackupOperationRunning by viewModel.isBackupOperationRunning.collectAsState()
    val backupMessage by viewModel.backupMessage.collectAsState()
    val isRefreshingCatalog by viewModel.isRefreshingCatalog.collectAsState()
    val catalogRefreshMessage by viewModel.catalogRefreshMessage.collectAsState()
    var visibleUpdateCheckMessage by remember { mutableStateOf<String?>(null) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss") }

    LaunchedEffect(updateCheckMessage, isCheckingForUpdates) {
        visibleUpdateCheckMessage = updateCheckMessage
        if (!isCheckingForUpdates && updateCheckMessage == UpToDateMessage) {
            delay(UpdateMessageDismissDelayMillis)
            visibleUpdateCheckMessage = null
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            viewModel.exportBackup(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreBackup(uri)
        }
    }

    SettingsContent(
        onNavigateBack = onNavigateBack,
        isShizukuAvailable = isShizukuAvailable,
        isPermissionGranted = isPermissionGranted,
        isServiceBound = isServiceBound,
        onRequestPermission = viewModel::requestPermission,
        onBindService = viewModel::bindService,
        onRefreshStatus = viewModel::refreshStatus,
        onExportBackup = {
            val fileName = "skinscript_backup_${LocalDateTime.now().format(dateFormatter)}.zip"
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
            exportLauncher.launch(intent)
        },
        onRestoreBackup = {
            importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        },
        isBackupOperationRunning = isBackupOperationRunning,
        backupMessage = backupMessage,
        onRefreshHeroCatalog = viewModel::refreshHeroCatalog,
        isRefreshingCatalog = isRefreshingCatalog,
        catalogRefreshMessage = catalogRefreshMessage,
        onCheckForUpdates = onCheckForUpdates,
        isCheckingForUpdates = isCheckingForUpdates,
        updateCheckMessage = visibleUpdateCheckMessage
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    onNavigateBack: () -> Unit,
    isShizukuAvailable: Boolean,
    isPermissionGranted: Boolean,
    isServiceBound: Boolean,
    onRequestPermission: () -> Unit,
    onBindService: () -> Unit,
    onRefreshStatus: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    isBackupOperationRunning: Boolean,
    backupMessage: String?,
    onRefreshHeroCatalog: () -> Unit,
    isRefreshingCatalog: Boolean,
    catalogRefreshMessage: String?,
    onCheckForUpdates: () -> Unit,
    isCheckingForUpdates: Boolean,
    updateCheckMessage: String?
) {
    val setupState = remember(isShizukuAvailable, isPermissionGranted, isServiceBound) {
        ShizukuSetupState.from(
            isShizukuAvailable = isShizukuAvailable,
            isPermissionGranted = isPermissionGranted,
            isServiceBound = isServiceBound
        )
    }
    val maintenanceBlocks = listOf(
        MaintenanceBlockState(
            title = "Backup & Restore",
            description = "Backup includes database records, imported scripts, and overwrite backups.",
            primaryAction = SettingsActionButton(
                label = "Export Backup",
                onClick = onExportBackup,
                enabled = !isBackupOperationRunning
            ),
            secondaryAction = SettingsActionButton(
                label = "Restore Backup",
                onClick = onRestoreBackup,
                enabled = !isBackupOperationRunning,
                style = SettingsActionStyle.Outlined
            ),
            isInProgress = isBackupOperationRunning,
            progressLabel = "Processing backup operation...",
            message = backupMessage,
            messageTone = feedbackTone(
                message = backupMessage,
                negativeKeywords = listOf("failed", "incompatible")
            ),
            testTag = SettingsTestTags.MaintenanceBackup
        ),
        MaintenanceBlockState(
            title = "Hero Catalog",
            description = "Fetch the latest hero list from the MLBB stats API.",
            primaryAction = SettingsActionButton(
                label = "Refresh Hero Catalog",
                onClick = onRefreshHeroCatalog,
                enabled = !isRefreshingCatalog,
                style = SettingsActionStyle.Outlined
            ),
            isInProgress = isRefreshingCatalog,
            progressLabel = "Fetching hero catalog...",
            message = catalogRefreshMessage,
            messageTone = feedbackTone(
                message = catalogRefreshMessage,
                negativeKeywords = listOf("failed", "error")
            ),
            testTag = SettingsTestTags.MaintenanceCatalog
        ),
        MaintenanceBlockState(
            title = "App Updates",
            description = "Check for a newer version on GitHub.",
            primaryAction = SettingsActionButton(
                label = "Check for Updates",
                onClick = onCheckForUpdates,
                enabled = !isCheckingForUpdates,
                style = SettingsActionStyle.Outlined
            ),
            isInProgress = isCheckingForUpdates,
            progressLabel = "Checking for updates...",
            message = updateCheckMessage?.takeUnless { isCheckingForUpdates },
            messageTone = feedbackTone(
                message = updateCheckMessage?.takeUnless { isCheckingForUpdates },
                negativeKeywords = listOf("failed", "error")
            ),
            testTag = SettingsTestTags.MaintenanceUpdates
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceLg),
            contentPadding = PaddingValues(
                start = AppDimens.ScreenHorizontal,
                top = AppDimens.SpaceLg,
                end = AppDimens.ScreenHorizontal,
                bottom = AppDimens.ScreenVertical
            )
        ) {
            item {
                SettingsSectionHeader(
                    title = "Shizuku setup",
                    subtitle = "Finish the connection flow here before installing or restoring scripts."
                )
            }

            item {
                ShizukuSetupCard(
                    setupState = setupState,
                    isShizukuAvailable = isShizukuAvailable,
                    isPermissionGranted = isPermissionGranted,
                    isServiceBound = isServiceBound,
                    onRequestPermission = onRequestPermission,
                    onBindService = onBindService,
                    onRefreshStatus = onRefreshStatus
                )
            }

            item {
                SettingsSectionHeader(
                    title = "Maintenance",
                    subtitle = "Manage backups, refresh app data, and check for new releases."
                )
            }

            item {
                SettingsSectionCard {
                    maintenanceBlocks.forEachIndexed { index, block ->
                        SettingsActionBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(block.testTag),
                            title = block.title,
                            description = block.description,
                            primaryAction = block.primaryAction,
                            secondaryAction = block.secondaryAction,
                            isInProgress = block.isInProgress,
                            progressLabel = block.progressLabel,
                            message = block.message,
                            messageTone = block.messageTone
                        )

                        if (index < maintenanceBlocks.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = AppDimens.SpaceXs),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShizukuSetupCard(
    setupState: ShizukuSetupState,
    isShizukuAvailable: Boolean,
    isPermissionGranted: Boolean,
    isServiceBound: Boolean,
    onRequestPermission: () -> Unit,
    onBindService: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    SettingsSectionCard {
        SettingsCardHeader(
            title = setupState.title,
            subtitle = setupState.subtitle,
            badgeLabel = setupState.badgeLabel,
            badgeTone = setupState.badgeTone
        )

        SettingsStatusItem(
            label = "Shizuku Service",
            value = if (isShizukuAvailable) "Running" else "Not running",
            tone = if (isShizukuAvailable) SettingsTone.Positive else SettingsTone.Critical
        )
        SettingsStatusItem(
            label = "Permission",
            value = if (isPermissionGranted) "Granted" else "Not granted",
            tone = if (isPermissionGranted) SettingsTone.Positive else SettingsTone.Caution
        )
        SettingsStatusItem(
            label = "File Service",
            value = if (isServiceBound) "Connected" else "Disconnected",
            tone = if (isServiceBound) SettingsTone.Positive else SettingsTone.Caution
        )

        if (setupState.noticeTitle != null && setupState.noticeMessage != null) {
            SettingsInlineNotice(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsTestTags.ShizukuBlockingNotice),
                title = setupState.noticeTitle,
                message = setupState.noticeMessage,
                tone = SettingsTone.Critical
            )
        }

        when (setupState.primaryAction) {
            SetupPrimaryAction.RequestPermission -> {
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SettingsTestTags.ShizukuPrimaryAction)
                ) {
                    Text("Grant Shizuku Permission")
                }
            }

            SetupPrimaryAction.ConnectService -> {
                Button(
                    onClick = onBindService,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SettingsTestTags.ShizukuPrimaryAction)
                ) {
                    Text("Connect File Service")
                }
            }

            null -> Unit
        }

        OutlinedButton(
            onClick = onRefreshStatus,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Status")
        }
    }
}

private data class MaintenanceBlockState(
    val title: String,
    val description: String,
    val primaryAction: SettingsActionButton,
    val secondaryAction: SettingsActionButton? = null,
    val isInProgress: Boolean,
    val progressLabel: String,
    val message: String?,
    val messageTone: SettingsTone,
    val testTag: String
)

private enum class SetupPrimaryAction {
    RequestPermission,
    ConnectService
}

private data class ShizukuSetupState(
    val title: String,
    val subtitle: String,
    val badgeLabel: String,
    val badgeTone: SettingsTone,
    val primaryAction: SetupPrimaryAction?,
    val noticeTitle: String? = null,
    val noticeMessage: String? = null
) {
    companion object {
        fun from(
            isShizukuAvailable: Boolean,
            isPermissionGranted: Boolean,
            isServiceBound: Boolean
        ): ShizukuSetupState = when {
            !isShizukuAvailable -> ShizukuSetupState(
                title = "Start Shizuku first",
                subtitle = "The service needs to be running before the app can finish setup.",
                badgeLabel = "Blocked",
                badgeTone = SettingsTone.Critical,
                primaryAction = null,
                noticeTitle = "Shizuku is not running",
                noticeMessage = "Please install and start Shizuku from the Play Store or via ADB."
            )

            !isPermissionGranted -> ShizukuSetupState(
                title = "Finish permission setup",
                subtitle = "Grant access so the app can request the Shizuku file bridge.",
                badgeLabel = "Action needed",
                badgeTone = SettingsTone.Caution,
                primaryAction = SetupPrimaryAction.RequestPermission
            )

            !isServiceBound -> ShizukuSetupState(
                title = "Connect the file service",
                subtitle = "Permission is ready. Connect the file bridge to complete setup.",
                badgeLabel = "Action needed",
                badgeTone = SettingsTone.Caution,
                primaryAction = SetupPrimaryAction.ConnectService
            )

            else -> ShizukuSetupState(
                title = "Shizuku ready",
                subtitle = "Everything is connected and ready for installs, restores, and backups.",
                badgeLabel = "Ready",
                badgeTone = SettingsTone.Positive,
                primaryAction = null
            )
        }
    }
}

private fun feedbackTone(message: String?, negativeKeywords: List<String>): SettingsTone {
    if (message.isNullOrBlank()) return SettingsTone.Neutral
    return if (negativeKeywords.any { keyword ->
            message.contains(keyword, ignoreCase = true)
        }
    ) {
        SettingsTone.Critical
    } else {
        SettingsTone.Positive
    }
}
