package com.istarvin.skinscriptinstaller.ui.screens.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsState()
    val isPermissionGranted by viewModel.isPermissionGranted.collectAsState()
    val isServiceBound by viewModel.isServiceBound.collectAsState()

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Shizuku Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Shizuku",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    StatusRow(
                        label = "Shizuku Service",
                        status = if (isShizukuAvailable) "Running" else "Not Running",
                        isPositive = isShizukuAvailable
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    StatusRow(
                        label = "Permission",
                        status = if (isPermissionGranted) "Granted" else "Not Granted",
                        isPositive = isPermissionGranted
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    StatusRow(
                        label = "File Service",
                        status = if (isServiceBound) "Connected" else "Disconnected",
                        isPositive = isServiceBound
                    )
                }
            }

            // Action buttons
            if (!isShizukuAvailable) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Shizuku is not running",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Please install and start Shizuku from the Play Store or via ADB.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (isShizukuAvailable && !isPermissionGranted) {
                Button(
                    onClick = { viewModel.requestPermission() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Shizuku Permission")
                }
            }

            if (isPermissionGranted && !isServiceBound) {
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

            // ML target path info
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mobile Legends Path",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "/storage/emulated/{userId}/Android/data/com.mobile.legends/files/dragon2017/assets/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, status: String, isPositive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isPositive) "●" else "●",
                color = if (isPositive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.size(12.dp)
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

