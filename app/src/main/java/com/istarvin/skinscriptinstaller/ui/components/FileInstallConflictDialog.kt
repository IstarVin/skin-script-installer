package com.istarvin.skinscriptinstaller.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.istarvin.skinscriptinstaller.domain.FileConflictChoice
import com.istarvin.skinscriptinstaller.ui.screens.detail.FileConflictWarningItem
import com.istarvin.skinscriptinstaller.ui.theme.AppAlpha
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens

@Composable
fun FileInstallConflictDialog(
    targetScriptName: String,
    targetUserId: Int,
    conflicts: List<FileConflictWarningItem>,
    selections: Map<String, FileConflictChoice>,
    onSelectionChange: (String, FileConflictChoice) -> Unit,
    onProceed: () -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resolve File Conflicts") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "Some files for $targetScriptName conflict with files already owned by another installed script for User $targetUserId.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(AppDimens.SpaceSm))
                Text(
                    text = "Choose which version to keep for each file. Keeping the current file skips it for this install.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(AppDimens.SpaceMd))

                conflicts.forEach { conflict ->
                    val selectedChoice = selections[conflict.destPath] ?: FileConflictChoice.USE_NEW

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AppDimens.SpaceSm)
                    ) {
                        Text(
                            text = conflict.relativePath,
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(AppDimens.SpaceXs))
                        Text(
                            text = "Current owner: ${conflict.conflictingScriptName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AppAlpha.SecondaryText)
                        )
                        Spacer(modifier = Modifier.height(AppDimens.SpaceSm))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceSm)
                        ) {
                            FilterChip(
                                selected = selectedChoice == FileConflictChoice.KEEP_CURRENT,
                                onClick = {
                                    onSelectionChange(conflict.destPath, FileConflictChoice.KEEP_CURRENT)
                                },
                                label = { Text("Keep current") }
                            )
                            FilterChip(
                                selected = selectedChoice == FileConflictChoice.USE_NEW,
                                onClick = {
                                    onSelectionChange(conflict.destPath, FileConflictChoice.USE_NEW)
                                },
                                label = { Text("Use new") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onProceed) {
                Text("Install")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}