package com.example.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.platform.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = NutriColors.LightPrimary,
    onPrimary = NutriColors.LightOnPrimary,
    primaryContainer = NutriColors.LightPrimaryContainer,
    onPrimaryContainer = NutriColors.LightOnPrimaryContainer,
    secondary = NutriColors.LightSecondary,
    onSecondary = NutriColors.LightOnSecondary,
    secondaryContainer = NutriColors.LightSecondaryContainer,
    onSecondaryContainer = NutriColors.LightOnSecondaryContainer,
    background = NutriColors.LightBackground,
    onBackground = NutriColors.LightOnBackground,
    surface = NutriColors.LightSurface,
    onSurface = NutriColors.LightOnSurface,
    outline = NutriColors.LightOutline
)

private val DarkColors = darkColorScheme(
    primary = NutriColors.DarkPrimary,
    onPrimary = NutriColors.DarkOnPrimary,
    primaryContainer = NutriColors.DarkPrimaryContainer,
    onPrimaryContainer = NutriColors.DarkOnPrimaryContainer,
    secondary = NutriColors.DarkSecondary,
    onSecondary = NutriColors.DarkOnSecondary,
    secondaryContainer = NutriColors.DarkSecondaryContainer,
    onSecondaryContainer = NutriColors.DarkOnSecondaryContainer,
    background = NutriColors.DarkBackground,
    onBackground = NutriColors.DarkOnBackground,
    surface = NutriColors.DarkSurface,
    onSurface = NutriColors.DarkOnSurface,
    outline = NutriColors.DarkOutline
)

private val HcLightColors = lightColorScheme(
    primary = NutriColors.HcLightPrimary,
    onPrimary = Color.White,
    background = NutriColors.HcLightBackground,
    onBackground = NutriColors.HcLightOnBackground,
    surface = NutriColors.HcLightSurface,
    onSurface = NutriColors.HcLightOnSurface,
    outline = Color.Black
)

private val HcDarkColors = darkColorScheme(
    primary = NutriColors.HcDarkPrimary,
    onPrimary = Color.Black,
    background = NutriColors.HcDarkBackground,
    onBackground = NutriColors.HcDarkOnBackground,
    surface = NutriColors.HcDarkSurface,
    onSurface = NutriColors.HcDarkOnSurface,
    outline = Color.White
)

@Composable
fun NutriTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    highContrast: Boolean = false,
    largerText: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        highContrast && darkTheme -> HcDarkColors
        highContrast && !darkTheme -> HcLightColors
        darkTheme -> DarkColors
        else -> LightColors
    }

    val typography = NutriTypography.getTypography(largerText)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
