package app.ove.studio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    background = OveBackgroundDark,
    onBackground = OveOnSurfaceDark,
    surface = OveSurfaceDark,
    onSurface = OveOnSurfaceDark,
    surfaceContainer = OveSurfaceContainerDark,
    surfaceContainerHigh = OveSurfaceContainerHighDark,
    surfaceVariant = OveSurfaceVariantDark,
    onSurfaceVariant = OveOnSurfaceVariantDark,
    primary = OvePrimaryDark,
    onPrimary = OveOnPrimaryDark,
    secondary = OveSecondaryDark,
    tertiary = OveTertiaryDark,
    error = OveErrorDark,
    outline = OveOutlineDark,
)

private val LightScheme = lightColorScheme(
    background = OveBackgroundLight,
    onBackground = OveOnSurfaceLight,
    surface = OveSurfaceLight,
    onSurface = OveOnSurfaceLight,
    surfaceContainer = OveSurfaceContainerLight,
    surfaceContainerHigh = OveSurfaceContainerHighLight,
    surfaceVariant = OveSurfaceVariantLight,
    onSurfaceVariant = OveOnSurfaceVariantDark,
    primary = OvePrimaryLight,
    onPrimary = OveOnPrimaryLight,
    secondary = OveSecondaryLight,
    tertiary = OveTertiaryLight,
    error = OveErrorLight,
    outline = OveOutlineLight,
)

private val OveShapes = Shapes(
    extraSmall = RoundedCornerShape(Shape.small.dp),
    small = RoundedCornerShape(Shape.small.dp),
    medium = RoundedCornerShape(Shape.medium.dp),
    large = RoundedCornerShape(Shape.large.dp),
    extraLarge = RoundedCornerShape(Shape.large.dp),
)

/**
 * OVE theme. The editor surface is ALWAYS dark (preview fidelity decision,
 * docs/UI_SYSTEM.md §10); other screens follow the system setting.
 * [forceDark] is set by the editor screen.
 */
@Composable
fun OveTheme(forceDark: Boolean = false, content: @Composable () -> Unit) {
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val useDark = forceDark || isSystemDark
    MaterialTheme(
        colorScheme = if (useDark) DarkScheme else LightScheme,
        shapes = OveShapes,
        content = content,
    )
}

/** Clip fill token lives outside the M3 roles — exposed explicitly. */
@Composable
fun clipVideoColor(): androidx.compose.ui.graphics.Color =
    if (androidx.compose.foundation.isSystemInDarkTheme()) OveClipVideoDark else OveClipVideoLight

/** Editor surfaces are always dark — convenience accessor. */
@Composable
fun editorClipColor(): androidx.compose.ui.graphics.Color = OveClipVideoDark
