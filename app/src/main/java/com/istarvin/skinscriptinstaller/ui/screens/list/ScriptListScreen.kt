package com.istarvin.skinscriptinstaller.ui.screens.list

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FolderOpen
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.istarvin.skinscriptinstaller.ui.components.AppEmptyState
import com.istarvin.skinscriptinstaller.ui.components.CollapsibleSection
import com.istarvin.skinscriptinstaller.ui.components.DeleteScriptDialog
import com.istarvin.skinscriptinstaller.ui.components.ImportChoiceBottomSheet
import com.istarvin.skinscriptinstaller.ui.components.InstallStatusChip
import com.istarvin.skinscriptinstaller.ui.components.ZipPasswordDialog
import com.istarvin.skinscriptinstaller.ui.theme.AppAlpha
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptListScreen(
    onScriptClick: (Long) -> Unit,
    onScriptClickAutoClassify: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ScriptListViewModel = hiltViewModel()
) {
    val heroScriptSections by viewModel.heroScriptSections.collectAsState()
    val scriptsWithStatus by viewModel.scriptsWithStatus.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val eligibleUserIds by viewModel.eligibleUserIds.collectAsState()
    val activeUserId by viewModel.activeUserId.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val zipPasswordPrompt by viewModel.zipPasswordPrompt.collectAsState()
    val pendingClassificationScriptId by viewModel.pendingClassificationScriptId.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var scriptToDelete by remember { mutableStateOf<ScriptWithStatus?>(null) }
    var showImportChoiceDialog by remember { mutableStateOf(false) }
    var showSearchField by remember { mutableStateOf(searchQuery.isNotBlank()) }
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
                    IconButton(
                        onClick = {
                            if (showSearchField) {
                                showSearchField = false
                                viewModel.updateSearchQuery("")
                            } else {
                                showSearchField = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (showSearchField) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (showSearchField) "Close search" else "Search"
                        )
                    }
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
            if (eligibleUserIds.size > 1) {
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

            if (showSearchField) {
                HeroSearchField(
                    query = searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = AppDimens.ScreenHorizontal,
                            vertical = AppDimens.SpaceSm
                        )
                )
            }

            if (heroScriptSections.isEmpty() && !isImporting && scriptsWithStatus.isEmpty()) {
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
            } else if (heroScriptSections.isEmpty() && !isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppDimens.ScreenHorizontal),
                    contentAlignment = Alignment.Center
                ) {
                    AppEmptyState(
                        icon = Icons.Default.Search,
                        title = "No heroes found",
                        subtitle = "Try a different hero name"
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
            ImportChoiceBottomSheet(
                onDismiss = { showImportChoiceDialog = false },
                onImportFolder = {
                    val folderIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    folderPickerLauncher.launch(folderIntent)
                },
                onImportZip = {
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
                }
            )
        }

        zipPasswordPrompt?.let { prompt ->
            ZipPasswordDialog(
                errorMessage = prompt.errorMessage,
                passwordText = zipPasswordText,
                onPasswordChange = { zipPasswordText = it },
                isImporting = isImporting,
                onConfirm = {
                    viewModel.retryZipWithPassword(zipPasswordText)
                    zipPasswordText = ""
                },
                onDismiss = {
                    zipPasswordText = ""
                    viewModel.dismissZipPasswordPrompt()
                }
            )
        }

        scriptToDelete?.let { item ->
            DeleteScriptDialog(
                scriptName = item.script.name,
                isInstalled = item.status == "installed",
                onDeleteWithRestore = {
                    viewModel.deleteScript(item.script, restoreBeforeDelete = true)
                    scriptToDelete = null
                },
                onDeleteWithoutRestore = {
                    viewModel.deleteScript(item.script, restoreBeforeDelete = false)
                    scriptToDelete = null
                },
                onDismiss = { scriptToDelete = null }
            )
        }
    }
}

@Composable
private fun HeroSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        label = { Text("Search heroes") },
        placeholder = { Text("Search heroes") }
    )
}

@Composable
private fun ActiveUserSelector(
    eligibleUserIds: List<Int>,
    activeUserId: Int,
    onUserSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (eligibleUserIds.size <= 1) return

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
                Text(userLabel(activeUserId))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                eligibleUserIds.forEach { userId ->
                    DropdownMenuItem(
                        text = { Text(userLabel(userId)) },
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

private fun userLabel(userId: Int): String {
    return when (userId) {
        0 -> "Main"
        999 -> "Smurf"
        else -> "User $userId"
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
    val subtitle = if (section.count == 1) "1 skin script" else "${section.count} skin scripts"

    CollapsibleSection(
        title = section.title,
        subtitle = subtitle,
        isExpanded = section.isExpanded,
        onToggle = { onToggle(section.key) },
        leadingIcon = if (!section.isFlat) {
            {
                HeroSectionIcon(
                    heroName = section.title,
                    heroIcon = section.heroIcon
                )
                Spacer(modifier = Modifier.width(AppDimens.SpaceMd))
            }
        } else null,
        titleSuffix = if (!section.isFlat && section.hasInstalledScript) {
            { HeroInstalledIndicator() }
        } else null
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
private fun HeroInstalledIndicator(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = AppAlpha.ChipContainer)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppDimens.SpaceSm,
                vertical = AppDimens.SpaceXs
            ),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
            )
            Text(
                text = "Installed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
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
    val subtitle = if (section.count == 1) "1 skin script" else "${section.count} skin scripts"

    CollapsibleSection(
        title = section.title,
        subtitle = subtitle,
        isExpanded = section.isExpanded,
        onToggle = { onToggle(section.key) },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        expandedElevation = AppDimens.ElevationLow,
        collapsedElevation = AppDimens.ElevationLow,
        contentPadding = Modifier.padding(
            start = AppDimens.SpaceSm,
            end = AppDimens.SpaceSm,
            bottom = AppDimens.SpaceSm
        ),
        contentSpacing = AppDimens.SpaceXs,
        titleStyle = MaterialTheme.typography.titleSmall
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
