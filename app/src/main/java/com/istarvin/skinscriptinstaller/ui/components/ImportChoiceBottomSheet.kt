package com.istarvin.skinscriptinstaller.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportChoiceBottomSheet(
    onDismiss: () -> Unit,
    onImportFolder: () -> Unit,
    onImportZip: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                    onDismiss()
                    onImportFolder()
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
                    onDismiss()
                    onImportZip()
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}
