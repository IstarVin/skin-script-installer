package com.istarvin.skinscriptinstaller.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.istarvin.skinscriptinstaller.domain.ImportConflictResolutionChoice
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens

data class ImportFileConflictDialogItem(
    val relativePath: String,
    val existingScriptNames: List<String>,
    val choice: ImportConflictResolutionChoice
)

@Composable
fun ImportFileConflictDialog(
    operationLabel: String,
    conflicts: List<ImportFileConflictDialogItem>,
    isProcessing: Boolean,
    onChoiceChange: (relativePath: String, choice: ImportConflictResolutionChoice) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isProcessing) {
                onDismiss()
            }
        },
        title = { Text("Resolve File Conflicts") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Some files already exist in other imported scripts. " +
                        "Choose which file to keep for each path.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(AppDimens.SpaceMd))

                conflicts.forEachIndexed { index, conflict ->
                    ConflictChoiceRow(
                        conflict = conflict,
                        onChoiceChange = onChoiceChange
                    )
                    if (index < conflicts.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = AppDimens.SpaceSm))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isProcessing
            ) {
                Text(operationLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConflictChoiceRow(
    conflict: ImportFileConflictDialogItem,
    onChoiceChange: (relativePath: String, choice: ImportConflictResolutionChoice) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = conflict.relativePath,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(AppDimens.SpaceXs))

        Text(
            text = "Existing in: ${conflict.existingScriptNames.joinToString()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(AppDimens.SpaceSm))

        ConflictChoiceOption(
            text = "Keep existing (skip this imported file)",
            selected = conflict.choice == ImportConflictResolutionChoice.KEEP_EXISTING,
            onClick = {
                onChoiceChange(
                    conflict.relativePath,
                    ImportConflictResolutionChoice.KEEP_EXISTING
                )
            }
        )

        ConflictChoiceOption(
            text = "Use imported (delete existing conflicting file)",
            selected = conflict.choice == ImportConflictResolutionChoice.USE_IMPORTED,
            onClick = {
                onChoiceChange(
                    conflict.relativePath,
                    ImportConflictResolutionChoice.USE_IMPORTED
                )
            }
        )
    }
}

@Composable
private fun ConflictChoiceOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(AppDimens.SpaceXs))
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}
