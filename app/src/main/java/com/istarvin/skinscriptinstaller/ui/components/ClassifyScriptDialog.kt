package com.istarvin.skinscriptinstaller.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.istarvin.skinscriptinstaller.data.db.entity.Hero
import com.istarvin.skinscriptinstaller.data.db.entity.Skin
import com.istarvin.skinscriptinstaller.ui.theme.AppAlpha
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassifyScriptDialog(
    currentHeroName: String?,
    currentOriginalSkinName: String?,
    currentReplacementSkinName: String?,
    suggestedHeroName: String?,
    allHeroes: List<Hero>,
    skinsForSelectedHero: List<Skin>,
    onHeroNameChanged: (String) -> Unit,
    onConfirm: (heroName: String, originalSkinName: String, replacementSkinName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var heroText by remember { mutableStateOf(currentHeroName ?: suggestedHeroName ?: "") }
    var heroInputTouched by remember { mutableStateOf(false) }
    val wasSuggested = currentHeroName == null && suggestedHeroName != null
    var originalSkinText by remember { mutableStateOf(currentOriginalSkinName ?: "") }
    var replacementSkinText by remember { mutableStateOf(currentReplacementSkinName ?: "") }

    var heroExpanded by remember { mutableStateOf(false) }
    var originalSkinExpanded by remember { mutableStateOf(false) }
    var replacementSkinExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(currentHeroName, suggestedHeroName) {
        if (
            !heroInputTouched &&
            currentHeroName.isNullOrBlank() &&
            heroText.isBlank() &&
            !suggestedHeroName.isNullOrBlank()
        ) {
            heroText = suggestedHeroName
        }
    }

    LaunchedEffect(heroText) {
        if (heroText.isNotBlank()) {
            onHeroNameChanged(heroText)
        }
    }

    val filteredHeroes = remember(heroText, allHeroes) {
        if (heroText.isBlank()) allHeroes
        else allHeroes.filter { it.name.contains(heroText, ignoreCase = true) }
    }

    val originalCandidateSkins = remember(replacementSkinText, skinsForSelectedHero) {
        val selectedReplacement = replacementSkinText.trim()
        if (selectedReplacement.isBlank()) {
            skinsForSelectedHero
        } else {
            skinsForSelectedHero.filterNot { it.name.equals(selectedReplacement, ignoreCase = true) }
        }
    }

    val filteredOriginalSkins = remember(originalSkinText, originalCandidateSkins) {
        if (originalSkinText.isBlank()) originalCandidateSkins
        else originalCandidateSkins.filter { it.name.contains(originalSkinText, ignoreCase = true) }
    }

    val replacementCandidateSkins = remember(originalSkinText, skinsForSelectedHero) {
        val selectedOriginal = originalSkinText.trim()
        if (selectedOriginal.isBlank()) {
            skinsForSelectedHero
        } else {
            skinsForSelectedHero.filterNot { it.name.equals(selectedOriginal, ignoreCase = true) }
        }
    }

    val filteredReplacementSkins = remember(replacementSkinText, replacementCandidateSkins) {
        if (replacementSkinText.isBlank()) replacementCandidateSkins
        else replacementCandidateSkins.filter {
            it.name.contains(
                replacementSkinText,
                ignoreCase = true
            )
        }
    }

    val canConfirm = heroText.isNotBlank() &&
            originalSkinText.isNotBlank() &&
            replacementSkinText.isNotBlank() &&
            !originalSkinText.trim().equals(replacementSkinText.trim(), ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Classify Script",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppDimens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceLg)
            ) {
                Text(
                    text = "Assign a hero and skin information to this script",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Hero field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = heroExpanded && filteredHeroes.isNotEmpty(),
                        onExpandedChange = { heroExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = heroText,
                            onValueChange = {
                                heroText = it
                                heroInputTouched = true
                                heroExpanded = true
                            },
                            label = { Text("Hero") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = heroExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = heroExpanded && filteredHeroes.isNotEmpty(),
                            onDismissRequest = { heroExpanded = false }
                        ) {
                            filteredHeroes.forEach { hero ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceSm)
                                        ) {
                                            Box(modifier = Modifier.size(AppDimens.IconHero)) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                AsyncImage(
                                                    model = hero.heroIcon,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            Text(hero.name)
                                        }
                                    },
                                    onClick = {
                                        heroText = hero.name
                                        heroInputTouched = true
                                        heroExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (wasSuggested) {
                        val suggestionLabel =
                            if (heroText.equals(suggestedHeroName, ignoreCase = true)) {
                                "Suggested from script name"
                            } else {
                                "Suggested hero: $suggestedHeroName"
                            }
                        Text(
                            text = suggestionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = AppAlpha.SecondaryText)
                        )
                    }
                }

                // Replacement Skin field
                ExposedDropdownMenuBox(
                    expanded = replacementSkinExpanded && filteredReplacementSkins.isNotEmpty(),
                    onExpandedChange = { replacementSkinExpanded = it }
                ) {
                    OutlinedTextField(
                        value = replacementSkinText,
                        onValueChange = {
                            replacementSkinText = it
                            if (originalSkinText.equals(it.trim(), ignoreCase = true)) {
                                originalSkinText = ""
                            }
                            replacementSkinExpanded = true
                        },
                        label = { Text("Replacement Skin") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = replacementSkinExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = replacementSkinExpanded && filteredReplacementSkins.isNotEmpty(),
                        onDismissRequest = { replacementSkinExpanded = false }
                    ) {
                        filteredReplacementSkins.forEach { skin ->
                            DropdownMenuItem(
                                text = { Text(skin.name) },
                                onClick = {
                                    replacementSkinText = skin.name
                                    if (originalSkinText.equals(skin.name, ignoreCase = true)) {
                                        originalSkinText = ""
                                    }
                                    replacementSkinExpanded = false
                                }
                            )
                        }
                    }
                }

                // Original Skin field
                ExposedDropdownMenuBox(
                    expanded = originalSkinExpanded && filteredOriginalSkins.isNotEmpty(),
                    onExpandedChange = { originalSkinExpanded = it }
                ) {
                    OutlinedTextField(
                        value = originalSkinText,
                        onValueChange = {
                            originalSkinText = it
                            if (replacementSkinText.equals(it.trim(), ignoreCase = true)) {
                                replacementSkinText = ""
                            }
                            originalSkinExpanded = true
                        },
                        label = { Text("Original Skin") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = originalSkinExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = originalSkinExpanded && filteredOriginalSkins.isNotEmpty(),
                        onDismissRequest = { originalSkinExpanded = false }
                    ) {
                        filteredOriginalSkins.forEach { skin ->
                            DropdownMenuItem(
                                text = { Text(skin.name) },
                                onClick = {
                                    originalSkinText = skin.name
                                    if (replacementSkinText.equals(skin.name, ignoreCase = true)) {
                                        replacementSkinText = ""
                                    }
                                    originalSkinExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(heroText, originalSkinText, replacementSkinText) },
                enabled = canConfirm
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
