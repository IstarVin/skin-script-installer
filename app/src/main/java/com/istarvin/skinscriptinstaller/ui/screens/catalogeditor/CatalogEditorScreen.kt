package com.istarvin.skinscriptinstaller.ui.screens.catalogeditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import com.istarvin.skinscriptinstaller.data.db.entity.Skin
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: CatalogEditorViewModel = hiltViewModel()
) {
    val heroes by viewModel.filteredHeroes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val expandedHeroId by viewModel.expandedHeroId.collectAsState()
    val skinsForExpandedHero by viewModel.skinsForExpandedHero.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddHeroDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Hero Catalog") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddHeroDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Hero")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppDimens.ScreenHorizontal,
                        vertical = AppDimens.SpaceSm
                    ),
                placeholder = { Text("Search heroes...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                singleLine = true
            )

            if (heroes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AppDimens.SpaceXl),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No heroes in catalog"
                        else "No heroes matching \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppDimens.ScreenHorizontal,
                        top = AppDimens.SpaceSm,
                        end = AppDimens.ScreenHorizontal,
                        bottom = 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceSm)
                ) {
                    items(heroes, key = { it.id }) { hero ->
                        HeroCard(
                            hero = hero,
                            isExpanded = expandedHeroId == hero.id,
                            skins = if (expandedHeroId == hero.id) skinsForExpandedHero else emptyList(),
                            onToggleExpand = { viewModel.toggleHeroExpansion(hero.id) },
                            onEditHero = viewModel::updateHeroName,
                            onDeleteHero = viewModel::deleteHero,
                            onCountScriptsByHero = viewModel::countScriptsByHeroId,
                            onAddSkin = { name -> viewModel.addSkin(hero.id, name) },
                            onEditSkin = { skinId, name -> viewModel.updateSkinName(skinId, hero.id, name) },
                            onDeleteSkin = { skinId -> viewModel.deleteSkin(skinId, hero.id) },
                            onCountScriptsBySkin = viewModel::countScriptsBySkinId
                        )
                    }
                }
            }
        }
    }

    if (showAddHeroDialog) {
        TextInputDialog(
            title = "Add Hero",
            label = "Hero name",
            confirmLabel = "Add",
            onConfirm = { name ->
                viewModel.addHero(name)
                showAddHeroDialog = false
            },
            onDismiss = { showAddHeroDialog = false }
        )
    }
}

@Composable
private fun HeroCard(
    hero: Hero,
    isExpanded: Boolean,
    skins: List<Skin>,
    onToggleExpand: () -> Unit,
    onEditHero: (Long, String) -> Unit,
    onDeleteHero: (Long) -> Unit,
    onCountScriptsByHero: (Long, (Int) -> Unit) -> Unit,
    onAddSkin: (String) -> Unit,
    onEditSkin: (Long, String) -> Unit,
    onDeleteSkin: (Long) -> Unit,
    onCountScriptsBySkin: (Long, (Int) -> Unit) -> Unit
) {
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var affectedScriptCount by remember { mutableIntStateOf(0) }
    var showAddSkinDialog by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = AppDimens.SpaceLg, vertical = AppDimens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hero.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { showEditDialog = true }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit hero",
                        modifier = Modifier.size(AppDimens.IconSmall)
                    )
                }
                IconButton(onClick = {
                    onCountScriptsByHero(hero.id) { count ->
                        affectedScriptCount = count
                        showDeleteDialog = true
                    }
                }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete hero",
                        modifier = Modifier.size(AppDimens.IconSmall)
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = AppDimens.SpaceLg,
                        end = AppDimens.SpaceLg,
                        bottom = AppDimens.SpaceMd
                    )
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                    )
                    Spacer(modifier = Modifier.height(AppDimens.SpaceSm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Skins (${skins.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showAddSkinDialog = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(AppDimens.IconSmall)
                            )
                            Spacer(modifier = Modifier.width(AppDimens.SpaceXs))
                            Text("Add Skin")
                        }
                    }

                    if (skins.isEmpty()) {
                        Text(
                            text = "No skins for this hero",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = AppDimens.SpaceSm)
                        )
                    } else {
                        skins.forEach { skin ->
                            SkinRow(
                                skin = skin,
                                heroId = hero.id,
                                onEditSkin = onEditSkin,
                                onDeleteSkin = onDeleteSkin,
                                onCountScriptsBySkin = onCountScriptsBySkin
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        TextInputDialog(
            title = "Edit Hero",
            label = "Hero name",
            initialValue = hero.name,
            confirmLabel = "Save",
            onConfirm = { name ->
                onEditHero(hero.id, name)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = "Delete Hero",
            message = buildString {
                append("Delete \"${hero.name}\" and all its skins?")
                if (affectedScriptCount > 0) {
                    append("\n\nThis will unclassify $affectedScriptCount script(s).")
                }
            },
            onConfirm = {
                onDeleteHero(hero.id)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showAddSkinDialog) {
        TextInputDialog(
            title = "Add Skin",
            label = "Skin name",
            confirmLabel = "Add",
            onConfirm = { name ->
                onAddSkin(name)
                showAddSkinDialog = false
            },
            onDismiss = { showAddSkinDialog = false }
        )
    }
}

@Composable
private fun SkinRow(
    skin: Skin,
    heroId: Long,
    onEditSkin: (Long, String) -> Unit,
    onDeleteSkin: (Long) -> Unit,
    onCountScriptsBySkin: (Long, (Int) -> Unit) -> Unit
) {
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var affectedScriptCount by remember { mutableIntStateOf(0) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.Space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = skin.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = { showEditDialog = true }) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit skin",
                modifier = Modifier.size(AppDimens.IconSmall)
            )
        }
        IconButton(onClick = {
            onCountScriptsBySkin(skin.id) { count ->
                affectedScriptCount = count
                showDeleteDialog = true
            }
        }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete skin",
                modifier = Modifier.size(AppDimens.IconSmall)
            )
        }
    }

    if (showEditDialog) {
        TextInputDialog(
            title = "Edit Skin",
            label = "Skin name",
            initialValue = skin.name,
            confirmLabel = "Save",
            onConfirm = { name ->
                onEditSkin(skin.id, name)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = "Delete Skin",
            message = buildString {
                append("Delete skin \"${skin.name}\"?")
                if (affectedScriptCount > 0) {
                    append("\n\nThis will unclassify $affectedScriptCount script(s).")
                }
            },
            onConfirm = {
                onDeleteSkin(skin.id)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String = "",
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
