package com.istarvin.skinscriptinstaller.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import com.istarvin.skinscriptinstaller.ui.theme.AppAlpha
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens

enum class SettingsTone {
    Positive,
    Caution,
    Critical,
    Neutral
}

enum class SettingsActionStyle {
    Filled,
    Outlined
}

data class SettingsActionButton(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val style: SettingsActionStyle = SettingsActionStyle.Filled
)

@Composable
fun SettingsSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(AppDimens.SpaceXs))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AppAlpha.SecondaryText)
        )
    }
}

@Composable
fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimens.ElevationMedium)
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.SpaceLg),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceMd),
            content = content
        )
    }
}

@Composable
fun SettingsCardHeader(
    title: String,
    subtitle: String,
    badgeLabel: String,
    badgeTone: SettingsTone,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceMd),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(AppDimens.SpaceXs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AppAlpha.SecondaryText)
            )
        }

        SettingsStatusBadge(label = badgeLabel, tone = badgeTone)
    }
}

@Composable
fun SettingsStatusBadge(
    label: String,
    tone: SettingsTone,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = badgeContainerColor(tone)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = AppDimens.SpaceMd,
                vertical = AppDimens.SpaceSm
            ),
            style = MaterialTheme.typography.labelLarge,
            color = toneColor(tone)
        )
    }
}

@Composable
fun SettingsStatusItem(
    label: String,
    value: String,
    tone: SettingsTone,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.SpaceMd, vertical = AppDimens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = iconContainerColor(tone),
                        shape = CircleShape
                    )
                    .padding(AppDimens.SpaceSm),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon(tone),
                    contentDescription = null,
                    tint = toneColor(tone)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AppAlpha.SecondaryText)
                )
                Spacer(modifier = Modifier.height(AppDimens.Space2))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    color = toneColor(tone)
                )
            }
        }
    }
}

@Composable
fun SettingsInlineNotice(
    title: String,
    message: String,
    tone: SettingsTone,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = noticeContainerColor(tone)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceMd),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = noticeIcon(tone),
                contentDescription = null,
                tint = toneColor(tone)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = noticeTextColor(tone)
                )
                Spacer(modifier = Modifier.height(AppDimens.SpaceXs))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = noticeTextColor(tone)
                )
            }
        }
    }
}

@Composable
fun SettingsActionBlock(
    title: String,
    description: String,
    primaryAction: SettingsActionButton,
    modifier: Modifier = Modifier,
    secondaryAction: SettingsActionButton? = null,
    isInProgress: Boolean = false,
    progressLabel: String? = null,
    message: String? = null,
    messageTone: SettingsTone = SettingsTone.Neutral
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceMd)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceXs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AppAlpha.SecondaryText)
            )
        }

        ActionButton(
            action = primaryAction,
            modifier = Modifier.fillMaxWidth()
        )

        secondaryAction?.let { action ->
            ActionButton(
                action = action,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isInProgress) {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.SpaceXs)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                progressLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = when (messageTone) {
                    SettingsTone.Positive -> MaterialTheme.colorScheme.primary
                    SettingsTone.Caution -> MaterialTheme.colorScheme.secondary
                    SettingsTone.Critical -> MaterialTheme.colorScheme.error
                    SettingsTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun ActionButton(
    action: SettingsActionButton,
    modifier: Modifier = Modifier
) {
    when (action.style) {
        SettingsActionStyle.Filled -> {
            Button(
                onClick = action.onClick,
                enabled = action.enabled,
                modifier = modifier
            ) {
                Text(action.label)
            }
        }

        SettingsActionStyle.Outlined -> {
            OutlinedButton(
                onClick = action.onClick,
                enabled = action.enabled,
                modifier = modifier
            ) {
                Text(action.label)
            }
        }
    }
}

@Composable
private fun statusIcon(tone: SettingsTone): ImageVector = when (tone) {
    SettingsTone.Positive -> Icons.Rounded.CheckCircle
    SettingsTone.Caution -> Icons.Rounded.Info
    SettingsTone.Critical -> Icons.Rounded.Error
    SettingsTone.Neutral -> Icons.Rounded.Info
}

@Composable
private fun noticeIcon(tone: SettingsTone): ImageVector = when (tone) {
    SettingsTone.Positive -> Icons.Rounded.CheckCircle
    SettingsTone.Caution -> Icons.Rounded.Info
    SettingsTone.Critical -> Icons.Rounded.Error
    SettingsTone.Neutral -> Icons.Rounded.Info
}

@Composable
private fun badgeContainerColor(tone: SettingsTone): Color = when (tone) {
    SettingsTone.Positive -> MaterialTheme.colorScheme.primaryContainer
    SettingsTone.Caution -> MaterialTheme.colorScheme.surface
    SettingsTone.Critical -> MaterialTheme.colorScheme.surface
    SettingsTone.Neutral -> MaterialTheme.colorScheme.surface
}

@Composable
private fun iconContainerColor(tone: SettingsTone): Color = when (tone) {
    SettingsTone.Positive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    SettingsTone.Caution -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    SettingsTone.Critical -> MaterialTheme.colorScheme.surfaceVariant
    SettingsTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun noticeContainerColor(tone: SettingsTone): Color = when (tone) {
    SettingsTone.Positive -> MaterialTheme.colorScheme.primaryContainer
    SettingsTone.Caution -> MaterialTheme.colorScheme.secondaryContainer
    SettingsTone.Critical -> MaterialTheme.colorScheme.errorContainer
    SettingsTone.Neutral -> MaterialTheme.colorScheme.surface
}

@Composable
private fun toneColor(tone: SettingsTone): Color = when (tone) {
    SettingsTone.Positive -> MaterialTheme.colorScheme.primary
    SettingsTone.Caution -> MaterialTheme.colorScheme.secondary
    SettingsTone.Critical -> MaterialTheme.colorScheme.error
    SettingsTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun noticeTextColor(tone: SettingsTone): Color = when (tone) {
    SettingsTone.Positive -> MaterialTheme.colorScheme.onPrimaryContainer
    SettingsTone.Caution -> MaterialTheme.colorScheme.onSecondaryContainer
    SettingsTone.Critical -> MaterialTheme.colorScheme.onErrorContainer
    SettingsTone.Neutral -> MaterialTheme.colorScheme.onSurface
}
