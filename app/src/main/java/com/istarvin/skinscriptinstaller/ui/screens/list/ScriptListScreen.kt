package com.istarvin.skinscriptinstaller.ui.screens.list

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.istarvin.skinscriptinstaller.ui.components.AppEmptyState
import com.istarvin.skinscriptinstaller.ui.components.InstallStatusChip
import com.istarvin.skinscriptinstaller.ui.theme.AppAlpha
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptListScreen(
    onScriptClick: (Long) -> Unit,
    onScriptClickAutoClassify: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ScriptListViewModel = hiltViewModel()
) {
    val heroScriptSections by viewModel.heroScriptSections.collectAsState()
    val eligibleUserIds by viewModel.eligibleUserIds.collectAsState()
    val activeUserId by viewModel.activeUserId.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val zipPasswordPrompt by viewModel.zipPasswordPrompt.collectAsState()
    val pendingClassificationScriptId by viewModel.pendingClassificationScriptId.collectAsState()
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

    LaunchedEffect(pendingClassificationScriptId) {
        pendingClassificationScriptId?.let { scriptId ->
            viewModel.dismissPendingClassification()
            onScriptClickAutoClassify(scriptId)
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
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = AppDimens.ElevationMedium
                )
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(AppDimens.SpaceSm)
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
                        .padding(
                            horizontal = AppDimens.ScreenHorizontal,
                            vertical = AppDimens.SpaceSm
                        )
                )
            }

            if (heroScriptSections.isEmpty() && !isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppDimens.ScreenHorizontal),
                    contentAlignment = Alignment.Center
                ) {
                    AppEmptyState(
                        icon = Icons.Outlined.FolderOpen,
                        title = "No scripts imported",
                        subtitle = "Tap + to import a skin script folder or ZIP"
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = AppDimens.SpaceXs),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceSm),
                    contentPadding = PaddingValues(AppDimens.ScreenHorizontal)
                ) {
                    items(heroScriptSections, key = { it.key }) { section ->
                        HeroScriptAccordionSection(
                            section = section,
                            onToggle = viewModel::toggleSection,
                            onScriptClick = onScriptClick,
                            onDeleteClick = { scriptToDelete = it }
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
                        .padding(bottom = AppDimens.SpaceXxl, top = AppDimens.SpaceXl)
                ) {
                    Text(
                        text = "Import Script",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(
                            horizontal = AppDimens.SpaceXl,
                            vertical = AppDimens.SpaceSm
                        )
                    )
                    Text(
                        text = "Choose the format of your skin script",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = AppDimens.SpaceXl)
                            .padding(bottom = AppDimens.SpaceLg)
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
                            Spacer(modifier = Modifier.height(AppDimens.SpaceSm))
                        }
                        Text("Enter the password for this ZIP archive")
                        Spacer(modifier = Modifier.height(AppDimens.SpaceSm))
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
    modifier: Modifier = Modifier,
    showHeroName: Boolean = true,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.ElevationLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.SpaceLg),
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
                if (item.isClassified && showHeroName) {
                    Text(
                        text = item.heroName ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (!item.isClassified && showHeroName) {
                    Text(
                        text = "Uncategorized",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AppAlpha.MutedText)
                    )
                }

                if (item.originalSkinName != null && item.replacementSkinName != null) {
                    Text(
                        text = "${item.originalSkinName} → ${item.replacementSkinName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(AppDimens.SpaceSm))

            InstallStatusChip(status = item.status)

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
private fun HeroScriptAccordionSection(
    section: HeroScriptSection,
    onToggle: (String) -> Unit,
    onScriptClick: (Long) -> Unit,
    onDeleteClick: (ScriptWithStatus) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (section.isExpanded) AppDimens.ElevationMedium else AppDimens.ElevationLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(section.key) }
                .padding(horizontal = AppDimens.SpaceLg, vertical = AppDimens.SpaceMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!section.isFlat) {
                    HeroSectionIcon(
                        heroName = section.title,
                        heroIcon = section.heroIcon
                    )
                    Spacer(modifier = Modifier.width(AppDimens.SpaceMd))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AppDimens.Space2))
                    Text(
                        text = if (section.count == 1) "1 skin script" else "${section.count} skin scripts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = if (section.isExpanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
                contentDescription = if (section.isExpanded) {
                    "Collapse ${section.title}"
                } else {
                    "Expand ${section.title}"
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (section.isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AppDimens.SpaceMd,
                        end = AppDimens.SpaceMd,
                        bottom = AppDimens.SpaceMd
                    ),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceSm)
            ) {
                if (section.isFlat) {
                    section.flatScripts.forEach { item ->
                        ScriptCard(
                            item = item,
                            modifier = Modifier.padding(start = AppDimens.SpaceSm),
                            showHeroName = false,
                            onClick = { onScriptClick(item.script.id) },
                            onDeleteClick = { onDeleteClick(item) }
                        )
                    }
                } else {
                    section.skinReplacementSections.forEach { replSection ->
                        SkinReplacementAccordionSection(
                            section = replSection,
                            onToggle = onToggle,
                            onScriptClick = onScriptClick,
                            onDeleteClick = onDeleteClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSectionIcon(
    heroName: String,
    heroIcon: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(AppDimens.IconHero)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = AppAlpha.SubtleContainer)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AppAlpha.SecondaryText)
        )
        if (!heroIcon.isNullOrBlank()) {
            AsyncImage(
                model = heroIcon,
                contentDescription = "$heroName icon",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
@Composable
private fun SkinReplacementAccordionSection(
    section: SkinReplacementSection,
    onToggle: (String) -> Unit,
    onScriptClick: (Long) -> Unit,
    onDeleteClick: (ScriptWithStatus) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.ElevationLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(section.key) }
                .padding(horizontal = AppDimens.SpaceLg, vertical = AppDimens.SpaceMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(AppDimens.Space2))
                Text(
                    text = if (section.count == 1) "1 skin script" else "${section.count} skin scripts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = if (section.isExpanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
                contentDescription = if (section.isExpanded) {
                    "Collapse ${section.title}"
                } else {
                    "Expand ${section.title}"
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (section.isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AppDimens.SpaceSm,
                        end = AppDimens.SpaceSm,
                        bottom = AppDimens.SpaceSm
                    ),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceXs)
            ) {
                section.scripts.forEach { item ->
                    OldSkinItem(
                        item = item,
                        onClick = { onScriptClick(item.script.id) },
                        onDeleteClick = { onDeleteClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OldSkinItem(
    item: ScriptWithStatus,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.ElevationLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.SpaceLg, vertical = AppDimens.SpaceSm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.originalSkinName ?: "Unknown",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(AppDimens.SpaceSm))

            InstallStatusChip(status = item.status)

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

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

