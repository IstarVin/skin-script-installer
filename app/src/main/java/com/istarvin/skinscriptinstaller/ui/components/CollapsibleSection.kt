package com.istarvin.skinscriptinstaller.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.istarvin.skinscriptinstaller.ui.theme.AppDimens

/**
 * A generic collapsible card section with a clickable header and expandable content.
 *
 * @param title Section header text
 * @param subtitle Secondary text below the title (e.g. item count)
 * @param isExpanded Whether the content area is visible
 * @param onToggle Called when the header is clicked
 * @param containerColor Background color for the card
 * @param expandedElevation Elevation when expanded
 * @param collapsedElevation Elevation when collapsed
 * @param contentPadding Padding inside the expanded content area
 * @param contentSpacing Vertical spacing between content items
 * @param leadingIcon Optional composable slot before the title (e.g. hero avatar)
 * @param content The expandable content slot
 */
@Composable
fun CollapsibleSection(
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    expandedElevation: Dp = AppDimens.ElevationMedium,
    collapsedElevation: Dp = AppDimens.ElevationLow,
    contentPadding: Modifier = Modifier.padding(
        start = AppDimens.SpaceMd,
        end = AppDimens.SpaceMd,
        bottom = AppDimens.SpaceMd
    ),
    contentSpacing: Dp = AppDimens.SpaceSm,
    titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    leadingIcon: @Composable (() -> Unit)? = null,
    titleSuffix: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isExpanded) expandedElevation else collapsedElevation
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = AppDimens.SpaceLg, vertical = AppDimens.SpaceMd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leadingIcon?.invoke()

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpaceSm)
                    ) {
                        Text(
                            text = title,
                            style = titleStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        titleSuffix?.invoke()
                    }
                    Spacer(modifier = Modifier.height(AppDimens.Space2))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse $title" else "Expand $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = tween(
                    durationMillis = 180,
                    easing = LinearOutSlowInEasing
                )
            ) + fadeIn(animationSpec = tween(durationMillis = 120)),
            exit = shrinkVertically(
                animationSpec = tween(
                    durationMillis = 110,
                    easing = FastOutLinearInEasing
                )
            ) + fadeOut(animationSpec = tween(durationMillis = 90))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(contentPadding),
                verticalArrangement = Arrangement.spacedBy(contentSpacing)
            ) {
                content()
            }
        }
    }
}
