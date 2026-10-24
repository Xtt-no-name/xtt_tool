package com.xtt.toolbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Sage40,
    onPrimary = PureWhite,
    primaryContainer = Sage90,
    onPrimaryContainer = Sage10,
    secondary = Slate40,
    onSecondary = PureWhite,
    secondaryContainer = Slate90,
    onSecondaryContainer = Ink,
    tertiary = Clay40,
    onTertiary = PureWhite,
    tertiaryContainer = Clay90,
    onTertiaryContainer = Sage10,
    background = WarmWhite,
    onBackground = Ink,
    surface = WarmSurface,
    onSurface = Ink,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = MutedInk,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,
    error = Error40,
    onError = PureWhite,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = Sage80,
    onPrimary = Sage10,
    primaryContainer = Sage30,
    onPrimaryContainer = Sage90,
    secondary = Slate80,
    onSecondary = Color(0xFF22302B),
    secondaryContainer = Color(0xFF34443E),
    onSecondaryContainer = Slate90,
    tertiary = Clay80,
    onTertiary = Color(0xFF3B2417),
    tertiaryContainer = Color(0xFF54392A),
    onTertiaryContainer = Clay90,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMutedInk,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = Error80,
    onError = Color(0xFF44201D),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
)

@Composable
fun XttToolboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
