package com.istarvin.skinscriptinstaller.ui.screens.list

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptListScreen(
    onScriptClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ScriptListViewModel = hiltViewModel()
) {
    val scriptsWithStatus by viewModel.scriptsWithStatus.collectAsState()
    val eligibleUserIds by viewModel.eligibleUserIds.collectAsState()
    val activeUserId by viewModel.activeUserId.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val zipPasswordPrompt by viewModel.zipPasswordPrompt.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var scriptToDelete by remember { mutableStateOf<ScriptWithStatus?>(null) }
    var showImportChoiceDialog by remember { mutableStateOf(false) }
    var zipPasswordText by remember { mutableStateOf("") }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.importScript(uri)
            }
        }
    }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.importZip(uri)
            }
        }
    }

    LaunchedEffect(importError) {
        importError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearImportError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skin Script Installer") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showImportChoiceDialog = true
                }
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = "Import Script")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (eligibleUserIds.isNotEmpty()) {
                ActiveUserSelector(
                    eligibleUserIds = eligibleUserIds,
                    activeUserId = activeUserId,
                    onUserSelected = viewModel::selectActiveUser,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (scriptsWithStatus.isEmpty() && !isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "No scripts imported",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap + to import a skin script folder or ZIP",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(scriptsWithStatus, key = { it.script.id }) { item ->
                    ScriptCard(
                        item = item,
                        onClick = { onScriptClick(item.script.id) },
                        onDeleteClick = { scriptToDelete = item }
                    )
                }
            }
            }
        }

        if (showImportChoiceDialog) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { showImportChoiceDialog = false },
                sheetState = sheetState,
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, top = 24.dp)
                ) {
                    Text(
                        text = "Import Script",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    Text(
                        text = "Choose the format of your skin script",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)
                    )
                    
                    ListItem(
                        headlineContent = { Text("Import Folder") },
                        supportingContent = { Text("Select an extracted script directory") },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.DriveFolderUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable {
                            showImportChoiceDialog = false
                            val folderIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            folderPickerLauncher.launch(folderIntent)
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    ListItem(
                        headlineContent = { Text("Import ZIP") },
                        supportingContent = { Text("Select a compressed (.zip) archive") },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.FolderZip,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        },
                        modifier = Modifier.clickable {
                            showImportChoiceDialog = false
                            val zipIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/zip"
                                putExtra(
                                    Intent.EXTRA_MIME_TYPES,
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "multipart/x-zip"
                                    )
                                )
                            }
                            zipPickerLauncher.launch(zipIntent)
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }

        zipPasswordPrompt?.let { prompt ->
            AlertDialog(
                onDismissRequest = {
                    zipPasswordText = ""
                    viewModel.dismissZipPasswordPrompt()
                },
                title = { Text("ZIP Password") },
                text = {
                    Column {
                        prompt.errorMessage?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text("Enter the password for this ZIP archive")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = zipPasswordText,
                            onValueChange = { zipPasswordText = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.retryZipWithPassword(zipPasswordText)
                            zipPasswordText = ""
                        },
                        enabled = zipPasswordText.isNotBlank() && !isImporting
                    ) {
                        Text("Import")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            zipPasswordText = ""
                            viewModel.dismissZipPasswordPrompt()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Delete confirmation dialog
        scriptToDelete?.let { item ->
            if (item.status == "installed") {
                AlertDialog(
                    onDismissRequest = { scriptToDelete = null },
                    title = { Text("Delete Installed Script") },
                    text = {
                        Text(
                            "Restore original files before deleting \"${item.script.name}\"? " +
                                    "If you skip restore, current modified files stay on your device."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteScript(item.script, restoreBeforeDelete = true)
                            scriptToDelete = null
                        }) {
                            Text("Restore and Delete")
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                viewModel.deleteScript(item.script, restoreBeforeDelete = false)
                                scriptToDelete = null
                            }) {
                                Text("Delete Without Restore")
                            }
                            TextButton(onClick = { scriptToDelete = null }) {
                                Text("Cancel")
                            }
                        }
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = { scriptToDelete = null },
                    title = { Text("Delete Script") },
                    text = { Text("Delete \"${item.script.name}\"? This will remove the imported files.") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteScript(item.script)
                            scriptToDelete = null
                        }) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { scriptToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ActiveUserSelector(
    eligibleUserIds: List<Int>,
    activeUserId: Int,
    onUserSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Status Scope",
            style = MaterialTheme.typography.titleSmall
        )
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text("User $activeUserId")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                eligibleUserIds.forEach { userId ->
                    DropdownMenuItem(
                        text = { Text("User $userId") },
                        onClick = {
                            expanded = false
                            onUserSelected(userId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScriptCard(
    item: ScriptWithStatus,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.script.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (item.isClassified) {
                    Text(
                        text = item.heroName ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (item.originalSkinName != null && item.replacementSkinName != null) {
                        Text(
                            text = "${item.originalSkinName} → ${item.replacementSkinName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Uncategorized",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            StatusChip(status = item.status)

            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (text, color) = when (status) {
        "installed" -> "Installed" to MaterialTheme.colorScheme.primary
        "restored" -> "Restored" to MaterialTheme.colorScheme.tertiary
        else -> "Not Installed" to MaterialTheme.colorScheme.outline
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.12f)
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

