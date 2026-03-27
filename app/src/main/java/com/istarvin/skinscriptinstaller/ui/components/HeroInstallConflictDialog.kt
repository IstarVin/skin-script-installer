package com.istarvin.skinscriptinstaller.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens

@Composable
fun HeroInstallConflictDialog(
    heroName: String,
    targetScriptName: String,
    targetUserId: Int,
    conflictingScriptNames: List<String>,
    onProceed: () -> Unit,
    onDismiss: () -> Unit
) {
    val conflictSummary = conflictingScriptNames.joinToString(separator = "\n") { "• $it" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace Installed Hero Script?") },
        text = {
            Column {
                Text(
                    text = buildString {
                        append("Another script for ")
                        append(heroName)
                        append(" is already installed for User ")
                        append(targetUserId)
                        append(".\n\n")
                        append("Proceeding will restore these installed scripts first before installing ")
                        append(targetScriptName)
                        append(".")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (conflictingScriptNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(AppDimens.SpaceMd))
                    Text(
                        text = conflictSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onProceed) {
                Text("Proceed")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
