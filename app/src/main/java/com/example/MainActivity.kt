package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.config.FeatureFlags
import com.example.data.AppSettings
import com.example.platform.health.AppHealthMonitor
import com.example.platform.settings.SettingsRepository
import com.example.ui.features.production.HomeScreen
import com.example.ui.features.production.ScanScreen
import com.example.ui.features.production.ResultsScreen
import com.example.ui.features.production.SettingsScreen
import com.example.ui.features.production.AboutScreen
import com.example.ui.features.developer.DeveloperToolsScreen
import com.example.ui.features.developer.ReplayViewerScreen
import com.example.ui.features.benchmark.BenchmarkRunnerScreen
import com.example.ui.navigation.NavController
import com.example.ui.navigation.Screen
import com.example.ui.theme.NutriGuardTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }

        // Initialize persistent settings repository via DataStore
        val settingsRepository = SettingsRepository(applicationContext)
        AppSettings.initialize(applicationContext, settingsRepository)

        super.onCreate(savedInstanceState)

        setContent {
            val themeMode = AppSettings.themePreference
            val highContrast = AppSettings.highContrastEnabled
            val largerText = AppSettings.largerTextEnabled

            NutriGuardTheme(
                themeMode = themeMode,
                highContrast = highContrast,
                largerText = largerText
            ) {
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { hasCameraPermission = it }
                )

                val navController = remember { NavController() }

                if (AppHealthMonitor.hasError) {
                    FallbackRecoveryScreen(
                        error = AppHealthMonitor.lastError,
                        lastTransition = AppHealthMonitor.lastScreenTransition,
                        lastOcrState = AppHealthMonitor.lastOcrState,
                        onRecover = {
                            AppHealthMonitor.clearError()
                            navController.clearBackStackAndNavigate(Screen.Home)
                        }
                    )
                } else {
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet(
                                drawerContainerColor = MaterialTheme.colorScheme.surface,
                                drawerContentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                NavigationDrawerContent(
                                    currentScreen = navController.currentScreen,
                                    onNavigate = { targetScreen ->
                                        scope.launch { drawerState.close() }
                                        navController.clearBackStackAndNavigate(targetScreen)
                                    }
                                )
                            }
                        }
                    ) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            when (val screen = navController.currentScreen) {
                                is Screen.Home -> HomeScreen(
                                    navController = navController,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                                is Screen.Scan -> ScanScreen(
                                    hasCameraPermission = hasCameraPermission,
                                    onRequestCameraPermission = {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    },
                                    navController = navController,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                                is Screen.Results -> ResultsScreen(
                                    args = screen,
                                    navController = navController
                                )
                                is Screen.Settings -> SettingsScreen(
                                    navController = navController,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                                is Screen.About -> AboutScreen(
                                    navController = navController,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                                is Screen.DeveloperTools -> DeveloperToolsScreen(
                                    navController = navController,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                                is Screen.ReplayViewer -> ReplayViewerScreen(
                                    replayId = screen.replayId,
                                    navController = navController
                                )
                                is Screen.BenchmarkRunner -> BenchmarkRunnerScreen(
                                    navController = navController,
                                    onOpenDrawer = { scope.launch { drawerState.open() } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationDrawerContent(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "NutriGuard Menu",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        DrawerItem(
            label = "Home",
            selected = currentScreen is Screen.Home,
            icon = Icons.Default.Home,
            onClick = { onNavigate(Screen.Home) }
        )

        DrawerItem(
            label = "Scan Product",
            selected = currentScreen is Screen.Scan,
            icon = Icons.Default.Search,
            onClick = { onNavigate(Screen.Scan) }
        )

        DrawerItem(
            label = "Settings",
            selected = currentScreen is Screen.Settings,
            icon = Icons.Default.Settings,
            onClick = { onNavigate(Screen.Settings) }
        )

        DrawerItem(
            label = "About App",
            selected = currentScreen is Screen.About,
            icon = Icons.Default.Info,
            onClick = { onNavigate(Screen.About) }
        )

        if (FeatureFlags.enableDiagnostics) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "Developer Diagnostics",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.secondary
            )
            DrawerItem(
                label = "Dev Console",
                selected = currentScreen is Screen.DeveloperTools,
                icon = Icons.Default.Build,
                onClick = { onNavigate(Screen.DeveloperTools) }
            )
        }

        if (FeatureFlags.enableBenchmarks) {
            DrawerItem(
                label = "Benchmark Run",
                selected = currentScreen is Screen.BenchmarkRunner,
                icon = Icons.Default.Star,
                onClick = { onNavigate(Screen.BenchmarkRunner) }
            )
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(imageVector = icon, contentDescription = label) },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

@Composable
private fun FallbackRecoveryScreen(
    error: Throwable?,
    lastTransition: String?,
    lastOcrState: String?,
    onRecover: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "NutriGuard Health Alert",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )

                Text(
                    text = "An unexpected state error was detected. The platform prevented a crash and is operating inside safe recovery mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Error: ${error?.message ?: "Unknown State Exception"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Last Screen: $lastTransition", style = MaterialTheme.typography.bodySmall)
                        Text("Last OCR State: $lastOcrState", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Button(
                    onClick = onRecover,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Recover & Restart")
                }
            }
        }
    }
}
