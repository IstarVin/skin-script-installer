package com.istarvin.skinscriptinstaller.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    val isOperating by viewModel.isOperating.collectAsState()
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
                title = { Text(script?.name ?: "Script Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val flattenedTree = remember(fileTree) { flattenTree(fileTree) }
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = s.name,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Imported: ${formatDate(s.importedAt)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            installation?.let { inst ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Status: ${inst.status.replaceFirstChar { it.uppercase() }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (inst.status == "installed")
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.tertiary
                                )
                                if (inst.status == "installed") {
                                    Text(
                                        text = "Installed: ${formatDate(inst.installedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                inst.restoredAt?.let { restoredAt ->
                                    Text(
                                        text = "Restored: ${formatDate(restoredAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.install() },
                        enabled = !isOperating && isShizukuReady &&
                                installation?.status != "installed",
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Install")
                    }

                    OutlinedButton(
                        onClick = { viewModel.restore() },
                        enabled = !isOperating && isShizukuReady &&
                                installation?.status == "installed",
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

            items(flattenedTree) { node ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (node.depth * 16).dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (node.isDirectory) "📁" else "📄",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.width(4.dp))
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

private fun flattenTree(nodes: List<FileTreeNode>): List<FileTreeNode> {
    val result = mutableListOf<FileTreeNode>()
    for (node in nodes) {
        result.add(node)
        if (node.isDirectory) {
            result.addAll(flattenTree(node.children))
        }
    }
    return result
}

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

