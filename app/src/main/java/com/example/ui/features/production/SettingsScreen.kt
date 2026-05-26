package com.example.ui.features.production

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.core.config.FeatureFlags
import com.example.data.AppSettings
import com.example.platform.settings.OcrMode
import com.example.platform.settings.ThemeMode
import com.example.ui.design.*
import com.example.ui.navigation.NavController
import com.example.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showThemeMenu by remember { mutableStateOf(false) }
    var showOcrMenu by remember { mutableStateOf(false) }

    // Read state from AppSettings
    var adaptiveOcr by remember { mutableStateOf(AppSettings.enableAdaptiveOcr) }
    var largerText by remember { mutableStateOf(AppSettings.largerTextEnabled) }
    var highContrast by remember { mutableStateOf(AppSettings.highContrastEnabled) }
    var themePreference by remember { mutableStateOf(AppSettings.themePreference) }
    var ocrModePreference by remember { mutableStateOf(AppSettings.ocrMode) }

    // Developer settings states
    var replaySaving by remember { mutableStateOf(AppSettings.replaySaving) }
    var showOverlays by remember { mutableStateOf(AppSettings.showOverlays) }
    var ocrDiagnostics by remember { mutableStateOf(AppSettings.ocrDiagnostics) }
    var preprocessingPreviews by remember { mutableStateOf(AppSettings.preprocessingPreviews) }

    NutriScreenScaffold(
        title = "System Settings",
        onOpenDrawer = onOpenDrawer,
        modifier = modifier.testTag("settings_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(NutriSpacing.md),
                verticalArrangement = Arrangement.spacedBy(NutriSpacing.md)
            ) {
                // 1. General Preferences
                NutriSettingsSection(title = "General Preferences") {
                    NutriSettingsSelector(
                        title = "App Theme Mode",
                        subtitle = "Choose between Light, Dark, or System Default.",
                        selectedValue = themePreference.name.lowercase().replaceFirstChar { it.uppercase() },
                        onClick = { showThemeMenu = true }
                    )
                }

                // 2. OCR Scanning Configuration
                NutriSettingsSection(title = "OCR Configuration") {
                    NutriSettingsToggle(
                        title = "Adaptive OCR Engine",
                        subtitle = "Dynamically adjusts image resolution, tiles, contrast, and timeout parameters.",
                        checked = adaptiveOcr,
                        onCheckedChange = {
                            AppSettings.setAdaptiveOcrEnabled(it)
                            adaptiveOcr = it
                        }
                    )

                    NutriSettingsSelector(
                        title = "OCR Execution Target",
                        subtitle = "Accuracy: thorough parsing; Performance: faster, lighter scan.",
                        selectedValue = ocrModePreference.name.lowercase().replaceFirstChar { it.uppercase() },
                        onClick = { showOcrMenu = true }
                    )
                }

                // 3. Accessibility Options
                NutriSettingsSection(title = "Accessibility Options") {
                    NutriSettingsToggle(
                        title = "Larger Fonts",
                        subtitle = "Increases all text labels and descriptions by 15% across screens.",
                        checked = largerText,
                        onCheckedChange = {
                            AppSettings.setLargerText(it)
                            largerText = it
                        }
                    )

                    NutriSettingsToggle(
                        title = "High Contrast Mode",
                        subtitle = "Enforces pure black and white surfaces for optimized readability.",
                        checked = highContrast,
                        onCheckedChange = {
                            AppSettings.setHighContrast(it)
                            highContrast = it
                        }
                    )
                }

                // 4. Developer Tools (Only shown in dev/internal builds via FeatureFlags)
                if (FeatureFlags.enableDiagnostics) {
                    NutriSettingsSection(title = "Developer Diagnostics") {
                        NutriSettingsToggle(
                            title = "Save Failed Scans to Cache",
                            subtitle = "Serializes failed extractions locally as offline-inspectable replay JSONs.",
                            checked = replaySaving,
                            onCheckedChange = {
                                AppSettings.setReplaySavingEnabled(it)
                                replaySaving = it
                            }
                        )

                        NutriSettingsToggle(
                            title = "Show Live OCR Overlays",
                            subtitle = "Draws detected ingredient bounding boxes directly over the preview viewport.",
                            checked = showOverlays,
                            onCheckedChange = {
                                AppSettings.setShowOverlaysEnabled(it)
                                showOverlays = it
                            }
                        )

                        NutriSettingsToggle(
                            title = "Diagnostics Text Overlays",
                            subtitle = "Displays live frame stats (contrast/blur/size) in the camera UI.",
                            checked = ocrDiagnostics,
                            onCheckedChange = {
                                AppSettings.setOcrDiagnosticsEnabled(it)
                                ocrDiagnostics = it
                            }
                        )

                        NutriSettingsToggle(
                            title = "Show Preprocessed Grayscale Preview",
                            subtitle = "Renders the adaptive thresh/contrast-adjusted bitmap for tuning.",
                            checked = preprocessingPreviews,
                            onCheckedChange = {
                                AppSettings.setPreprocessingPreviewsEnabled(it)
                                preprocessingPreviews = it
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(NutriSpacing.sm))

                NutriPrimaryButton(
                    text = "Return to Dashboard",
                    onClick = { navController.clearBackStackAndNavigate(Screen.Home) },
                    icon = NutriIcons.Home,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Dropdown Menus overlaying the Box container
            if (showThemeMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                ) {
                    AlertDialog(
                        onDismissRequest = { showThemeMenu = false },
                        title = { Text("App Theme Mode", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(NutriSpacing.sm)) {
                                ThemeMode.values().forEach { mode ->
                                    TextButton(
                                        onClick = {
                                            AppSettings.setThemeMode(mode)
                                            themePreference = mode
                                            showThemeMenu = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (themePreference == mode) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showThemeMenu = false }) {
                                Text("Cancel")
                            }
                        },
                        shape = NutriShapes.dialog
                    )
                }
            }

            if (showOcrMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                ) {
                    AlertDialog(
                        onDismissRequest = { showOcrMenu = false },
                        title = { Text("OCR Execution Target", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(NutriSpacing.sm)) {
                                OcrMode.values().forEach { mode ->
                                    TextButton(
                                        onClick = {
                                            AppSettings.setOcrPerformanceMode(mode)
                                            ocrModePreference = mode
                                            showOcrMenu = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (ocrModePreference == mode) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showOcrMenu = false }) {
                                Text("Cancel")
                            }
                        },
                        shape = NutriShapes.dialog
                    )
                }
            }
        }
    }
}
