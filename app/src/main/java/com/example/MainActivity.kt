package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ui.features.home.HomeScreen
import com.example.ui.features.scan.ScanScreen
import com.example.ui.features.results.ResultsScreen
import com.example.ui.features.debug.DebugBenchmarkScreen
import com.example.ui.features.replay.ReplayViewerScreen
import com.example.ui.features.settings.SettingsScreen
import com.example.ui.navigation.NavController
import com.example.ui.navigation.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { hasCameraPermission = it }
                )

                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = remember { NavController() }

                    when (val screen = navController.currentScreen) {
                        is Screen.Home -> HomeScreen(navController = navController)
                        is Screen.Scan -> ScanScreen(
                            hasCameraPermission = hasCameraPermission,
                            onRequestCameraPermission = {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            navController = navController
                        )
                        is Screen.Results -> ResultsScreen(
                            args = screen,
                            navController = navController
                        )
                        is Screen.DebugReplay -> DebugBenchmarkScreen(
                            navController = navController
                        )
                        is Screen.ReplayViewer -> ReplayViewerScreen(
                            replayId = screen.replayId,
                            navController = navController
                        )
                        is Screen.Settings -> SettingsScreen(
                            navController = navController
                        )
                    }
                }
            }
        }
    }
}

