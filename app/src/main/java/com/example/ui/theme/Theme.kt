package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = BrandPrimary,
    secondary = BrandSecondary,
    background = Color(0xFF121413),
    surface = Color(0xFF1A1C1B),
    onPrimary = Color.White,
    onBackground = Color(0xFFE1E3E1),
    onSurface = Color(0xFFE1E3E1),
    outline = BrandOutline
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BrandPrimary,
    secondary = BrandSecondary,
    background = BrandBackground,
    surface = BrandSurface,
    onPrimary = BrandOnPrimary,
    onBackground = BrandOnSurface,
    onSurface = BrandOnSurface,
    outline = BrandOutline,
    surfaceVariant = Color(0xFFAAB8B5)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Set to false to prioritize our high-fidelity custom polish design theme
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
