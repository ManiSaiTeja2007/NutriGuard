package com.example.ui.features.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.design.*
import com.example.ui.navigation.NavController
import com.example.ui.navigation.Screen
import org.json.JSONObject
import java.io.File

@Composable
fun DeveloperToolsScreen(
    navController: NavController,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var replaysList by remember { mutableStateOf<List<ReplayItem>>(emptyList()) }
    var loadingReplays by remember { mutableStateOf(true) }

    // Load replays from cache
    LaunchedEffect(Unit) {
        val cacheDir = context.cacheDir
        val files = cacheDir.listFiles { _, name -> name.endsWith("_replay.json") } ?: emptyArray()
        replaysList = files.mapNotNull { file ->
            try {
                val jsonObj = JSONObject(file.readText())
                ReplayItem(
                    id = jsonObj.getString("replay_id"),
                    sourceImage = jsonObj.getString("source_image"),
                    failuresCount = jsonObj.getJSONArray("failures").length(),
                    timestamp = jsonObj.getString("timestamp").take(16).replace("T", " ")
                )
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.timestamp }
        loadingReplays = false
    }

    NutriScreenScaffold(
        title = "Developer Diagnostics",
        onOpenDrawer = onOpenDrawer,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(NutriSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NutriSpacing.md)
        ) {
            // Dashboard summary card
            NutriCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(NutriSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = NutriIcons.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(NutriIcons.lg)
                    )
                    Spacer(modifier = Modifier.width(NutriSpacing.md))
                    Column {
                        Text(
                            text = "Developer Admin Console",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "View saved offline run replays and failure serialization.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = "Serialized Replays (${replaysList.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = NutriSpacing.xs)
            )

            if (loadingReplays) {
                NutriLoadingState(
                    message = "Loading replays...",
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else if (replaysList.isEmpty()) {
                NutriEmptyState(
                    message = "No local replays logged in cache.",
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(replaysList) { item ->
                        ReplayRowItem(item) {
                            navController.navigateTo(Screen.ReplayViewer(item.id))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplayRowItem(item: ReplayItem, onClick: () -> Unit) {
    NutriCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(NutriSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Replay: ${item.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.sourceImage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Scanned: ${item.timestamp}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.width(NutriSpacing.sm))

            if (item.failuresCount > 0) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${item.failuresCount} FAILURES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "PASSED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

data class ReplayItem(
    val id: String,
    val sourceImage: String,
    val failuresCount: Int,
    val timestamp: String
)
