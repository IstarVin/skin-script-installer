package com.istarvin.skinscriptinstaller.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.istarvin.skinscriptinstaller.data.db.entity.InstallationStatus
import com.istarvin.skinscriptinstaller.ui.theme.AppAlpha
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens

@Composable
fun InstallStatusChip(
    status: String,
    modifier: Modifier = Modifier
) {
    val normalized = status.trim().lowercase()
    val (text, color) = when (normalized) {
        InstallationStatus.INSTALLED -> "Installed" to MaterialTheme.colorScheme.primary
        InstallationStatus.REPLACED -> "Replaced" to MaterialTheme.colorScheme.error
        InstallationStatus.RESTORED -> "Restored" to MaterialTheme.colorScheme.tertiary
        else -> "Not Installed" to MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = AppAlpha.ChipContainer)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.ElevationNone)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = AppDimens.SpaceMd,
                vertical = AppDimens.SpaceXs
            ),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}