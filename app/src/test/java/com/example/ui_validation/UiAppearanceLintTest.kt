package com.example.ui_validation

import com.example.ui.design.NutriColors
import com.example.core.ui_validation.UiValidationEngine
import org.junit.Assert.assertTrue
import org.junit.Test

class UiAppearanceLintTest {

    @Test
    fun testLightModeContrastRatios() {
        // Text on Background/Surface contrast ratio must be >= 4.5
        val ratioTextBg = UiValidationEngine.calculateContrastRatio(NutriColors.LightOnBackground, NutriColors.LightBackground)
        val ratioTextSurf = UiValidationEngine.calculateContrastRatio(NutriColors.LightOnSurface, NutriColors.LightSurface)
        val ratioPrimaryBg = UiValidationEngine.calculateContrastRatio(NutriColors.LightPrimary, NutriColors.LightBackground)
        
        println("Light Theme Contrast Ratios:")
        println("- Text on Background: ${"%.2f".format(ratioTextBg)}")
        println("- Text on Surface: ${"%.2f".format(ratioTextSurf)}")
        println("- Primary Action on Background: ${"%.2f".format(ratioPrimaryBg)}")

        assertTrue("Light theme text on background contrast ratio (${"%.2f".format(ratioTextBg)}) must be >= 4.5", ratioTextBg >= 4.5)
        assertTrue("Light theme text on surface contrast ratio (${"%.2f".format(ratioTextSurf)}) must be >= 4.5", ratioTextSurf >= 4.5)
        assertTrue("Light theme primary action on background contrast ratio (${"%.2f".format(ratioPrimaryBg)}) must be >= 3.0", ratioPrimaryBg >= 3.0)
    }

    @Test
    fun testDarkModeContrastRatios() {
        val ratioTextBg = UiValidationEngine.calculateContrastRatio(NutriColors.DarkOnBackground, NutriColors.DarkBackground)
        val ratioTextSurf = UiValidationEngine.calculateContrastRatio(NutriColors.DarkOnSurface, NutriColors.DarkSurface)
        val ratioPrimaryBg = UiValidationEngine.calculateContrastRatio(NutriColors.DarkPrimary, NutriColors.DarkBackground)

        println("Dark Theme Contrast Ratios:")
        println("- Text on Background: ${"%.2f".format(ratioTextBg)}")
        println("- Text on Surface: ${"%.2f".format(ratioTextSurf)}")
        println("- Primary Action on Background: ${"%.2f".format(ratioPrimaryBg)}")

        assertTrue("Dark theme text on background contrast ratio (${"%.2f".format(ratioTextBg)}) must be >= 4.5", ratioTextBg >= 4.5)
        assertTrue("Dark theme text on surface contrast ratio (${"%.2f".format(ratioTextSurf)}) must be >= 4.5", ratioTextSurf >= 4.5)
        assertTrue("Dark theme primary action on background contrast ratio (${"%.2f".format(ratioPrimaryBg)}) must be >= 3.0", ratioPrimaryBg >= 3.0)
    }

    @Test
    fun testHighContrastPalettes() {
        // High contrast themes should aim for enhanced readability (>= 7.0)
        val ratioHcLight = UiValidationEngine.calculateContrastRatio(NutriColors.HcLightOnBackground, NutriColors.HcLightBackground)
        val ratioHcDark = UiValidationEngine.calculateContrastRatio(NutriColors.HcDarkOnBackground, NutriColors.HcDarkBackground)

        println("High Contrast Theme Contrast Ratios:")
        println("- HC Light text on background: ${"%.2f".format(ratioHcLight)}")
        println("- HC Dark text on background: ${"%.2f".format(ratioHcDark)}")

        assertTrue("High contrast Light theme text contrast ratio (${"%.2f".format(ratioHcLight)}) must be >= 7.0", ratioHcLight >= 7.0)
        assertTrue("High contrast Dark theme text contrast ratio (${"%.2f".format(ratioHcDark)}) must be >= 7.0", ratioHcDark >= 7.0)
    }
}
