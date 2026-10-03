package app.ove.studio.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ove.studio.ui.theme.Shape
import app.ove.studio.ui.theme.Spacing
import app.ove.studio.ui.theme.ToolBarMetrics

/**
 * A single CapCut-style tool chip: icon tile (44dp, rounded 12dp) above a
 * 10sp label. The signature bottom-toolbar atom. Selected state fills the
 * tile with the accent; danger tint marks destructive tools.
 */
@Composable
fun ToolChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    danger: Boolean = false,
    accent: Boolean = false,
) {
    val tileColor = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val iconTint = when {
        danger -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.onPrimary
        accent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val labelTint = when {
        danger -> MaterialTheme.colorScheme.error
        accent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .width(64.dp)
            .semantics { contentDescription = label }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(ToolBarMetrics.chipBox.dp)
                .background(tileColor, RoundedCornerShape(ToolBarMetrics.chipCorner.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(ToolBarMetrics.chipIcon.dp),
            )
        }
        Text(
            text = label,
            fontSize = ToolBarMetrics.chipLabel.sp,
            color = if (enabled) labelTint else labelTint.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = ToolBarMetrics.chipGap.dp),
        )
    }
}

/**
 * The CapCut-signature bottom tool bar: a horizontally scrollable row of
 * [ToolChip]s. Real, wired actions only — no dead controls (charter).
 */
@Composable
fun ToolChipBar(
    chips: List<ToolChipSpec>,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(ToolBarMetrics.barHeight.dp),
        contentPadding = PaddingValues(horizontal = Spacing.md.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(chips, key = { it.label }) { chip ->
            ToolChip(
                icon = chip.icon,
                label = chip.label,
                onClick = chip.onClick,
                enabled = chip.enabled,
                danger = chip.danger,
                accent = chip.accent,
            )
        }
    }
}

data class ToolChipSpec(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val accent: Boolean = false,
)
