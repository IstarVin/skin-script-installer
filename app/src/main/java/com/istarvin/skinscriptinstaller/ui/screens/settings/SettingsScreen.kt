package com.istarvin.skinscriptinstaller.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsState()
    val isPermissionGranted by viewModel.isPermissionGranted.collectAsState()
    val isServiceBound by viewModel.isServiceBound.collectAsState()
    val isBackupOperationRunning by viewModel.isBackupOperationRunning.collectAsState()
    val backupMessage by viewModel.backupMessage.collectAsState()
    val isRefreshingCatalog by viewModel.isRefreshingCatalog.collectAsState()
    val catalogRefreshMessage by viewModel.catalogRefreshMessage.collectAsState()

    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss") }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(AppDimens.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceLg)
        ) {
            // Shizuku Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.ElevationMedium)
            ) {
                Column(modifier = Modifier.padding(AppDimens.SpaceLg)) {
                    Text(
                        text = "Shizuku",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(AppDimens.SpaceMd))

                    StatusRow(
                        label = "Shizuku Service",
                        status = if (isShizukuAvailable) "Running" else "Not Running",
                        isPositive = isShizukuAvailable
                    )
                    Spacer(modifier = Modifier.height(AppDimens.SpaceSm))

                    StatusRow(
                        label = "Permission",
                        status = if (isPermissionGranted) "Granted" else "Not Granted",
                        isPositive = isPermissionGranted
                    )
                    Spacer(modifier = Modifier.height(AppDimens.SpaceSm))

                    StatusRow(
                        label = "File Service",
                        status = if (isServiceBound) "Connected" else "Disconnected",
                        isPositive = isServiceBound
                    )
                }
            }

            // Action buttons
            AnimatedVisibility(
                visible = !isShizukuAvailable,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(AppDimens.SpaceLg)) {
                        Text(
                            text = "Shizuku is not running",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(AppDimens.SpaceXs))
                        Text(
                            text = "Please install and start Shizuku from the Play Store or via ADB.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isShizukuAvailable && !isPermissionGranted,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Button(
                    onClick = { viewModel.requestPermission() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Shizuku Permission")
                }
            }

            AnimatedVisibility(
                visible = isPermissionGranted && !isServiceBound,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Button(
                    onClick = { viewModel.bindService() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect File Service")
                }
            }

            OutlinedButton(
                onClick = { viewModel.refreshStatus() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh Status")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.ElevationMedium)
            ) {
                Column(modifier = Modifier.padding(AppDimens.SpaceLg)) {
                    Text(
                        text = "Backup & Restore",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(AppDimens.SpaceSm))
                    Text(
                        text = "Backup includes database records, imported scripts, and overwrite backups.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(AppDimens.SpaceMd))

                    Button(
                        onClick = {
                            val fileName = "skinscript_backup_${LocalDateTime.now().format(dateFormatter)}.zip"
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/zip"
                                putExtra(Intent.EXTRA_TITLE, fileName)
                            }
                            exportLauncher.launch(intent)
                        },
                        enabled = !isBackupOperationRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export Backup")
                    }

                    Spacer(modifier = Modifier.height(AppDimens.SpaceSm))

                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        },
                        enabled = !isBackupOperationRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Restore Backup")
                    }

                    AnimatedVisibility(visible = isBackupOperationRunning) {
                        Column(modifier = Modifier.padding(top = AppDimens.SpaceMd)) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(AppDimens.SpaceXs))
                            Text(
                                text = "Processing backup operation...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    AnimatedVisibility(visible = backupMessage != null) {
                        Text(
                            text = backupMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (backupMessage?.contains("failed", ignoreCase = true) == true ||
                                backupMessage?.contains("incompatible", ignoreCase = true) == true
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.padding(top = AppDimens.SpaceMd)
                        )
                    }
                }
            }

            // Hero Catalog Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.ElevationMedium)
            ) {
                Column(modifier = Modifier.padding(AppDimens.SpaceLg)) {
                    Text(
                        text = "Hero Catalog",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(AppDimens.SpaceSm))
                    Text(
                        text = "Fetch the latest hero list from the MLBB stats API.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(AppDimens.SpaceMd))
                    OutlinedButton(
                        onClick = { viewModel.refreshHeroCatalog() },
                        enabled = !isRefreshingCatalog,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refresh Hero Catalog")
                    }
                    AnimatedVisibility(visible = isRefreshingCatalog) {
                        Column(modifier = Modifier.padding(top = AppDimens.SpaceMd)) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(AppDimens.SpaceXs))
                            Text(
                                text = "Fetching hero catalog...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    AnimatedVisibility(visible = catalogRefreshMessage != null) {
                        Text(
                            text = catalogRefreshMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (catalogRefreshMessage?.contains("failed", ignoreCase = true) == true ||
                                catalogRefreshMessage?.contains("error", ignoreCase = true) == true
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.padding(top = AppDimens.SpaceMd)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, status: String, isPositive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.SpaceXs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isPositive) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                contentDescription = null,
                tint = if (isPositive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.size(AppDimens.IconTiny)
            )
            Text(
                text = " $status",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPositive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        }
    }
}

