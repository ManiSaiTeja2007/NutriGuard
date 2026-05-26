package com.example.ui.design

import androidx.compose.material3.Typography

object NutriTypography {
    private val baseTypography = Typography()

    fun getTypography(largerText: Boolean): Typography {
        return if (largerText) {
            Typography(
                displayLarge = baseTypography.displayLarge.copy(fontSize = baseTypography.displayLarge.fontSize * 1.15f),
                displayMedium = baseTypography.displayMedium.copy(fontSize = baseTypography.displayMedium.fontSize * 1.15f),
                displaySmall = baseTypography.displaySmall.copy(fontSize = baseTypography.displaySmall.fontSize * 1.15f),
                headlineLarge = baseTypography.headlineLarge.copy(fontSize = baseTypography.headlineLarge.fontSize * 1.15f),
                headlineMedium = baseTypography.headlineMedium.copy(fontSize = baseTypography.headlineMedium.fontSize * 1.15f),
                headlineSmall = baseTypography.headlineSmall.copy(fontSize = baseTypography.headlineSmall.fontSize * 1.15f),
                titleLarge = baseTypography.titleLarge.copy(fontSize = baseTypography.titleLarge.fontSize * 1.15f),
                titleMedium = baseTypography.titleMedium.copy(fontSize = baseTypography.titleMedium.fontSize * 1.15f),
                titleSmall = baseTypography.titleSmall.copy(fontSize = baseTypography.titleSmall.fontSize * 1.15f),
                bodyLarge = baseTypography.bodyLarge.copy(fontSize = baseTypography.bodyLarge.fontSize * 1.15f),
                bodyMedium = baseTypography.bodyMedium.copy(fontSize = baseTypography.bodyMedium.fontSize * 1.15f),
                bodySmall = baseTypography.bodySmall.copy(fontSize = baseTypography.bodySmall.fontSize * 1.15f),
                labelLarge = baseTypography.labelLarge.copy(fontSize = baseTypography.labelLarge.fontSize * 1.15f),
                labelMedium = baseTypography.labelMedium.copy(fontSize = baseTypography.labelMedium.fontSize * 1.15f),
                labelSmall = baseTypography.labelSmall.copy(fontSize = baseTypography.labelSmall.fontSize * 1.15f)
            )
        } else {
            baseTypography
        }
    }
}
