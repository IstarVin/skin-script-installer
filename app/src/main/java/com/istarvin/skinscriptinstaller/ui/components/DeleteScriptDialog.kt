package com.istarvin.skinscriptinstaller.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun DeleteScriptDialog(
    scriptName: String,
    isInstalled: Boolean,
    onDeleteWithRestore: () -> Unit,
    onDeleteWithoutRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isInstalled) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete Installed Script") },
            text = {
                Text(
                    "Restore original files before deleting \"$scriptName\"? " +
                            "If you skip restore, current modified files stay on your device."
                )
            },
            confirmButton = {
                TextButton(onClick = onDeleteWithRestore) {
                    Text("Restore and Delete")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onDeleteWithoutRestore) {
                        Text("Delete Without Restore")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete Script") },
            text = { Text("Delete \"$scriptName\"? This will remove the imported files.") },
            confirmButton = {
                TextButton(onClick = onDeleteWithoutRestore) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}
