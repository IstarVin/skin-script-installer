package com.istarvin.skinscriptinstaller.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScriptDetailViewModel = hiltViewModel()
) {
    val script by viewModel.script.collectAsState()
    val installation by viewModel.installation.collectAsState()
    val fileTree by viewModel.fileTree.collectAsState()
    val expandedDirectoryIds by viewModel.expandedDirectoryIds.collectAsState()
    val isOperating by viewModel.isOperating.collectAsState()
    val eligibleUserIds by viewModel.eligibleUserIds.collectAsState()
    val selectedUserId by viewModel.selectedUserId.collectAsState()
    val installProgress by viewModel.installProgress.collectAsState()
    val restoreProgress by viewModel.restoreProgress.collectAsState()
    val isShizukuReady by viewModel.isShizukuReady.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Script Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val flattenedTree = remember(fileTree, expandedDirectoryIds) {
            flattenTree(fileTree, expandedDirectoryIds)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Script info card
            item {
                script?.let { s ->
                    val isInstalled = installation?.status == "installed"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isInstalled) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = s.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (isInstalled) 
                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Imported: ${formatDate(s.importedAt)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isInstalled) 
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "User scope: User $selectedUserId",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isInstalled) 
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            installation?.let { inst ->
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (inst.status == "installed")
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.tertiary
                                    ),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = inst.status.uppercase(),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (inst.status == "installed")
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onTertiary
                                    )
                                }
                                
                                if (inst.status == "installed") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Installed: ${formatDate(inst.installedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                inst.restoredAt?.let { restoredAt ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Restored: ${formatDate(restoredAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action buttons
            item {
                val isInstalled = installation?.status == "installed"
                val canPrimaryAction = !isOperating && isShizukuReady && eligibleUserIds.isNotEmpty()
                val canRestore = !isOperating && isShizukuReady &&
                    installation?.status == "installed"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (isInstalled) viewModel.reinstall() else viewModel.install()
                        },
                        enabled = canPrimaryAction,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isInstalled) "Reinstall" else "Install")
                    }

                    OutlinedButton(
                        onClick = { viewModel.restore() },
                        enabled = canRestore,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("Restore")
                    }
                }

                if (!isShizukuReady) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠ Shizuku is not ready. Open Settings to connect.",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (isShizukuReady && eligibleUserIds.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No Mobile Legends user found in /storage/emulated",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Progress indicator
            val activeProgress = installProgress ?: restoreProgress
            if (activeProgress != null && !activeProgress.isComplete && isOperating) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = {
                                activeProgress.currentIndex.toFloat() / activeProgress.total.coerceAtLeast(1)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${activeProgress.currentIndex} / ${activeProgress.total}: ${activeProgress.currentFileName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // File tree
            item {
                Text(
                    text = "File Tree",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(flattenedTree, key = { it.id }) { node ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = node.isDirectory) {
                            viewModel.toggleDirectory(node.id)
                        }
                        .padding(start = (node.depth * 24).dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when {
                        !node.isDirectory -> Icons.AutoMirrored.Filled.InsertDriveFile
                        node.id in expandedDirectoryIds -> Icons.Default.FolderOpen
                        else -> Icons.Default.Folder
                    }
                    val iconTint = if (node.isDirectory) 
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = iconTint
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

private fun flattenTree(
    nodes: List<FileTreeNode>,
    expandedDirectoryIds: Set<String>
): List<FileTreeNode> {
    val result = mutableListOf<FileTreeNode>()
    for (node in nodes) {
        result.add(node)
        if (node.isDirectory && node.id in expandedDirectoryIds) {
            result.addAll(flattenTree(node.children, expandedDirectoryIds))
        }
    }
    return result
}

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

