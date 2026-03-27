package com.istarvin.skinscriptinstaller.ui.screens.detail

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.istarvin.skinscriptinstaller.ui.components.ClassifyScriptDialog
import com.istarvin.skinscriptinstaller.ui.components.HeroInstallConflictDialog
import com.istarvin.skinscriptinstaller.ui.components.ImportChoiceBottomSheet
import com.istarvin.skinscriptinstaller.ui.components.InstallStatusChip
import com.istarvin.skinscriptinstaller.ui.components.ZipPasswordDialog
import com.istarvin.skinscriptinstaller.ui.theme.AppAlpha
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptDetailScreen(
    onNavigateBack: () -> Unit,
    autoClassify: Boolean = false,
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
    val isImporting by viewModel.isImporting.collectAsState()
    val zipPasswordPrompt by viewModel.zipPasswordPrompt.collectAsState()
    val installConflictWarning by viewModel.installConflictWarning.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Classification state
    val heroName by viewModel.heroName.collectAsState()
    val originalSkinName by viewModel.originalSkinName.collectAsState()
    val replacementSkinName by viewModel.replacementSkinName.collectAsState()
    val allHeroes by viewModel.allHeroes.collectAsState()
    val skinsForSelectedHero by viewModel.skinsForSelectedHero.collectAsState()
    val suggestedHeroName by viewModel.suggestedHeroName.collectAsState()

    var showClassifyDialog by rememberSaveable { mutableStateOf(false) }
    var pendingAutoClassify by rememberSaveable(autoClassify) { mutableStateOf(autoClassify) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showImportChoiceSheet by remember { mutableStateOf(false) }
    var zipPasswordText by remember { mutableStateOf("") }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.updateScript(uri)
            }
        }
    }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.updateZip(uri)
            }
        }
    }

    LaunchedEffect(pendingAutoClassify) {
        if (pendingAutoClassify) {
            showClassifyDialog = true
            pendingAutoClassify = false
        }
    }

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
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            enabled = !isOperating && !isImporting
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(AppDimens.IconSmall),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More actions"
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Update Script") },
                                onClick = {
                                    showOverflowMenu = false
                                    showImportChoiceSheet = true
                                },
                                enabled = !isOperating && !isImporting
                            )
                        }
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
                .padding(horizontal = AppDimens.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceLg)
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
                        elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.ElevationLow)
                    ) {
                        Column(modifier = Modifier.padding(AppDimens.SpaceXl)) {
                            Text(
                                text = s.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (isInstalled)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(AppDimens.SpaceMd))
                            Text(
                                text = "Imported: ${formatDate(s.importedAt)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isInstalled)
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = AppAlpha.SecondaryText)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AppAlpha.SecondaryText)
                            )
                            Spacer(modifier = Modifier.height(AppDimens.SpaceXs))
                            Text(
                                text = "User scope: User $selectedUserId",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isInstalled)
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = AppAlpha.SecondaryText)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AppAlpha.SecondaryText)
                            )
                            installation?.let { inst ->
                                Spacer(modifier = Modifier.height(AppDimens.SpaceMd))
                                InstallStatusChip(status = inst.status)

                                if (inst.status == "installed") {
                                    Spacer(modifier = Modifier.height(AppDimens.SpaceSm))
                                    Text(
                                        text = "Installed: ${formatDate(inst.installedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                            alpha = AppAlpha.SecondaryText
                                        )
                                    )
                                }
                                inst.restoredAt?.let { restoredAt ->
                                    Spacer(modifier = Modifier.height(AppDimens.SpaceSm))
                                    Text(
                                        text = "Restored: ${formatDate(restoredAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = AppAlpha.SecondaryText
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Classification card
            item {
                ClassificationCard(
                    heroName = heroName,
                    originalSkinName = originalSkinName,
                    replacementSkinName = replacementSkinName,
                    onEditClick = { showClassifyDialog = true },
                    onClearClick = { viewModel.clearClassification() }
                )
            }

            // Action buttons
            item {
                val isInstalled = installation?.status == "installed"
                val canPrimaryAction =
                    !isOperating && isShizukuReady && eligibleUserIds.isNotEmpty()
                val canRestore = !isOperating && isShizukuReady &&
                        installation?.status == "installed"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceMd)
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
                    Spacer(modifier = Modifier.height(AppDimens.SpaceSm))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.ElevationLow)
                    ) {
                        Text(
                            text = "⚠ Shizuku is not ready. Open Settings to connect.",
                            modifier = Modifier.padding(AppDimens.SpaceMd),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (isShizukuReady && eligibleUserIds.isEmpty()) {
                    Spacer(modifier = Modifier.height(AppDimens.SpaceSm))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.ElevationLow)
                    ) {
                        Text(
                            text = "No Mobile Legends user found in /storage/emulated",
                            modifier = Modifier.padding(AppDimens.SpaceMd),
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
                                activeProgress.currentIndex.toFloat() / activeProgress.total.coerceAtLeast(
                                    1
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(AppDimens.SpaceXs))
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
                        .padding(start = (node.depth * 24).dp, top = AppDimens.SpaceXs, bottom = AppDimens.SpaceXs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when {
                        !node.isDirectory -> Icons.AutoMirrored.Filled.InsertDriveFile
                        node.id in expandedDirectoryIds -> Icons.Default.FolderOpen
                        else -> Icons.Default.Folder
                    }
                    val iconTint = if (node.isDirectory)
                        MaterialTheme.colorScheme.primary.copy(alpha = AppAlpha.SecondaryText)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AppAlpha.MutedText)

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(AppDimens.IconSmall),
                        tint = iconTint
                    )
                    Spacer(modifier = Modifier.width(AppDimens.SpaceSm))
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(AppDimens.SpaceLg)) }
        }

        if (showClassifyDialog) {
            ClassifyScriptDialog(
                currentHeroName = heroName,
                currentOriginalSkinName = originalSkinName,
                currentReplacementSkinName = replacementSkinName,
                suggestedHeroName = suggestedHeroName,
                allHeroes = allHeroes,
                skinsForSelectedHero = skinsForSelectedHero,
                onHeroNameChanged = { viewModel.loadSkinsForHeroName(it) },
                onConfirm = { hero, original, replacement ->
                    viewModel.classifyScript(hero, original, replacement)
                    showClassifyDialog = false
                },
                onDismiss = { showClassifyDialog = false }
            )
        }

        if (showImportChoiceSheet) {
            ImportChoiceBottomSheet(
                onDismiss = { showImportChoiceSheet = false },
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

        installConflictWarning?.let { warning ->
            HeroInstallConflictDialog(
                heroName = warning.heroName,
                targetScriptName = warning.targetScriptName,
                targetUserId = warning.targetUserId,
                conflictingScriptNames = warning.conflicts.map { it.scriptName },
                onProceed = viewModel::confirmInstallConflictWarning,
                onDismiss = viewModel::dismissInstallConflictWarning
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassificationCard(
    heroName: String?,
    originalSkinName: String?,
    replacementSkinName: String?,
    onEditClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.ElevationLow)
    ) {
        Column(modifier = Modifier.padding(AppDimens.SpaceLg)) {
            if (heroName != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = heroName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (originalSkinName != null && replacementSkinName != null) {
                            Spacer(modifier = Modifier.height(AppDimens.SpaceXs))
                            Text(
                                text = "$originalSkinName → $replacementSkinName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(AppDimens.IconHero)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit classification",
                            modifier = Modifier.size(AppDimens.IconSmall),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = "Uncategorized",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AppAlpha.MutedText)
                )
                Spacer(modifier = Modifier.height(AppDimens.SpaceXs))
                OutlinedButton(onClick = onEditClick) {
                    Text("Classify this script")
                }
            }
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
