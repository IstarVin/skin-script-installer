package com.istarvin.skinscriptinstaller.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.istarvin.skinscriptinstaller.ui.theme.AppAlpha
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens

@Composable
fun RestoreAllScriptsAction(
    activeUserId: Int,
    restorableCount: Int,
    enabled: Boolean,
    isRestoring: Boolean,
    onConfirmRestoreAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmationDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceSm)
    ) {
        Text(
            text = "Restore all installed scripts for User $activeUserId",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AppAlpha.SecondaryText)
        )

        OutlinedButton(
            onClick = { showConfirmationDialog = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRestoring) "Restoring..." else "Restore All")
        }
    }

    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text("Restore All Scripts") },
            text = {
                Text(
                    "Restore $restorableCount installed scripts for User $activeUserId? " +
                        "This will revert all currently installed script changes for that user."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmationDialog = false
                        onConfirmRestoreAll()
                    }
                ) {
                    Text("Confirm Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
