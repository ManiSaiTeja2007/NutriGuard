package com.example.ui.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.navigation.NavController
import com.example.ui.navigation.Screen

@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var replayCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val cacheDir = context.cacheDir
        val files = cacheDir.listFiles { _, name -> name.endsWith("_replay.json") }
        replayCount = files?.size ?: 0
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7FAF9))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                text = "NutriGuard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF163832)
            )
            Text(
                text = "Offline Ingredient Ingestion Engine",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF5D6E6A)
            )
        }

        // Navigation Options
        MenuCard(
            title = "Scan Product Label",
            description = "Activate live camera or test label validation sequence.",
            onClick = { navController.navigateTo(Screen.Scan) }
        )

        MenuCard(
            title = "In-App Debugger & Replays",
            description = "Run local performance benchmarks and inspect failed cases.",
            onClick = { navController.navigateTo(Screen.DebugReplay) }
        )

        MenuCard(
            title = "System Settings",
            description = "Configure diagnostics overlays and local storage parameters.",
            onClick = { navController.navigateTo(Screen.Settings) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // System Ingestion Status Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "System Diagnostics Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF163832)
                )
                
                StatusRow(label = "Pipeline Version", value = "1.0.0")
                StatusRow(label = "Execution State", value = "Ready (Offline-First)")
                StatusRow(label = "Benchmark Dataset", value = "v1.0 (Seeded)")
                StatusRow(label = "Logged Replays", value = "$replayCount files")
            }
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF116A5B)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5D6E6A)
                )
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF7D8E8A)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF163832)
        )
    }
}
