package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.platform.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    outline = LightOutline
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    outline = DarkOutline
)

private val HcLightColors = lightColorScheme(
    primary = HcLightPrimary,
    onPrimary = Color.White,
    background = HcLightBackground,
    onBackground = HcLightOnBackground,
    surface = HcLightSurface,
    onSurface = HcLightOnSurface,
    outline = Color.Black
)

private val HcDarkColors = darkColorScheme(
    primary = HcDarkPrimary,
    onPrimary = Color.Black,
    background = HcDarkBackground,
    onBackground = HcDarkOnBackground,
    surface = HcDarkSurface,
    onSurface = HcDarkOnSurface,
    outline = Color.White
)

@Composable
fun NutriGuardTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    highContrast: Boolean = false,
    largerText: Boolean = false,
    content: @Composable () -> Unit
) {
    com.example.ui.design.NutriTheme(
        themeMode = themeMode,
        highContrast = highContrast,
        largerText = largerText,
        content = content
    )
}
