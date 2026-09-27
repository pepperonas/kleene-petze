package io.celox.notifvault.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = DarkPrimary, onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer, onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = LightPrimary,
    secondary = DarkSecondary, onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer, onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary, onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer, onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError, onError = DarkOnError,
    errorContainer = DarkErrorContainer, onErrorContainer = DarkOnErrorContainer,
    background = DarkSurface, onBackground = DarkOnSurface,
    surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceTint = DarkPrimary,
    inverseSurface = DarkInverseSurface, inverseOnSurface = DarkInverseOnSurface,
    outline = DarkOutline, outlineVariant = DarkOutlineVariant,
    scrim = Color.Black,
)

private val LightColors
    @Composable get() = expressiveLightColorScheme().copy(
        primary = LightPrimary, onPrimary = LightOnPrimary,
        primaryContainer = LightPrimaryContainer, onPrimaryContainer = LightOnPrimaryContainer,
        inversePrimary = DarkPrimary,
        secondary = LightSecondary, onSecondary = LightOnSecondary,
        secondaryContainer = LightSecondaryContainer, onSecondaryContainer = LightOnSecondaryContainer,
        tertiary = LightTertiary, onTertiary = LightOnTertiary,
        tertiaryContainer = LightTertiaryContainer, onTertiaryContainer = LightOnTertiaryContainer,
        error = LightError, onError = LightOnError,
        errorContainer = LightErrorContainer, onErrorContainer = LightOnErrorContainer,
        background = LightSurface, onBackground = LightOnSurface,
        surface = LightSurface, onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant, onSurfaceVariant = LightOnSurfaceVariant,
        surfaceContainerLowest = LightSurfaceContainerLowest,
        surfaceContainerLow = LightSurfaceContainerLow,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        surfaceContainerHighest = LightSurfaceContainerHighest,
        surfaceTint = LightPrimary,
        inverseSurface = LightInverseSurface, inverseOnSurface = LightInverseOnSurface,
        outline = LightOutline, outlineVariant = LightOutlineVariant,
    )

/** Rounder than stock M3 across the board — the expressive shape scale, as in Brutus/Flipper. */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Light/dark choice from Settings; [SYSTEM] follows the phone. Stored by name. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Material 3 Expressive: spring-based [MotionScheme.expressive] (spatial motion may overshoot,
 * effects never do), the expressive shape scale, and the Kleene Petze brand scheme. Material You
 * dynamic color is an opt-in in Settings (API 31+).
 */
@Composable
fun NotifVaultTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        content = content
    )
}
