package com.example.ui.features.production

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.config.FeatureFlags
import com.example.data.AppSettings
import com.example.platform.settings.OcrMode
import com.example.platform.settings.ThemeMode
import com.example.ui.navigation.NavController
import com.example.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Open Drawer")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. General Preferences
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "General Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Theme selector row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = "App Theme Mode",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Choose between Light, Dark, or System Default.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        var showThemeMenu by remember { mutableStateOf(false) }
                        Box {
                            Button(onClick = { showThemeMenu = true }) {
                                Text(AppSettings.themePreference.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                            DropdownMenu(
                                expanded = showThemeMenu,
                                onDismissRequest = { showThemeMenu = false }
                            ) {
                                ThemeMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                        onClick = {
                                            AppSettings.setThemeMode(mode)
                                            showThemeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. OCR Scanning Configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "OCR Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    SettingToggle(
                        label = "Adaptive OCR Engine",
                        description = "Dynamically adjusts image resolution, tiles, contrast, and timeout parameters.",
                        checked = AppSettings.enableAdaptiveOcr,
                        onCheckedChange = { AppSettings.setAdaptiveOcrEnabled(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    // OCR Performance Mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = "OCR Execution Target",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Accuracy: thorough parsing; Performance: faster, lighter scan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        var showOcrMenu by remember { mutableStateOf(false) }
                        Box {
                            Button(onClick = { showOcrMenu = true }) {
                                Text(AppSettings.ocrMode.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                            DropdownMenu(
                                expanded = showOcrMenu,
                                onDismissRequest = { showOcrMenu = false }
                            ) {
                                OcrMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                        onClick = {
                                            AppSettings.setOcrPerformanceMode(mode)
                                            showOcrMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Accessibility Options
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Accessibility Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    SettingToggle(
                        label = "Larger Fonts",
                        description = "Increases all text labels and descriptions by 15% across screens.",
                        checked = AppSettings.largerTextEnabled,
                        onCheckedChange = { AppSettings.setLargerText(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    SettingToggle(
                        label = "High Contrast Mode",
                        description = "Enforces pure black and white surfaces for optimized readability.",
                        checked = AppSettings.highContrastEnabled,
                        onCheckedChange = { AppSettings.setHighContrast(it) }
                    )
                }
            }

            // 4. Developer Tools (Only shown in dev/internal builds)
            if (FeatureFlags.enableDiagnostics) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Developer Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        SettingToggle(
                            label = "Save Failed Scans to Cache",
                            description = "Serializes failed extractions locally as offline-inspectable replay JSONs.",
                            checked = AppSettings.replaySaving,
                            onCheckedChange = { AppSettings.setReplaySavingEnabled(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        SettingToggle(
                            label = "Show Live OCR Overlays",
                            description = "Draws detected ingredient bounding boxes directly over the preview viewport.",
                            checked = AppSettings.showOverlays,
                            onCheckedChange = { AppSettings.setShowOverlaysEnabled(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        SettingToggle(
                            label = "Diagnostics Text Overlays",
                            description = "Displays live frame stats (contrast/blur/size) in the camera UI.",
                            checked = AppSettings.ocrDiagnostics,
                            onCheckedChange = { AppSettings.setOcrDiagnosticsEnabled(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        SettingToggle(
                            label = "Show Preprocessed Grayscale Preview",
                            description = "Renders the adaptive thresh/contrast-adjusted bitmap for tuning.",
                            checked = AppSettings.preprocessingPreviews,
                            onCheckedChange = { AppSettings.setPreprocessingPreviewsEnabled(it) }
                        )
                    }
                }
            }

            Button(
                onClick = { navController.clearBackStackAndNavigate(Screen.Home) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Return to Dashboard")
            }
        }
    }
}

@Composable
private fun SettingToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
