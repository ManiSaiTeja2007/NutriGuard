package com.example.ui.features.developer.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.navigation.NavController
import com.example.ui.design.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperExportScreen(
    navController: NavController,
    viewModel: DeveloperExportViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkSnapshotStatus(context)
    }

    NutriScreenScaffold(
        title = "Session Exporter",
        onBack = { navController.popBackStack() },
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
            // Dashboard Status Card
            NutriCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(NutriSpacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = NutriIcons.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(NutriIcons.md)
                        )
                        Spacer(modifier = Modifier.width(NutriSpacing.sm))
                        Text(
                            text = "Latest Ingestion Snapshot",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(NutriSpacing.sm))
                    Text(
                        text = if (uiState.isSnapshotAvailable) {
                            "A pipeline execution snapshot is available in memory cache."
                        } else {
                            "No execution snapshot cached. Please scan or run a headless test first."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(NutriSpacing.md))
                    NutriPrimaryButton(
                        text = "Export Session to Storage",
                        onClick = { viewModel.exportLatestSession(context) },
                        enabled = uiState.isSnapshotAvailable && uiState.exportStatus !is ExportStatus.Exporting,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Export Status Alert Banner
            when (val status = uiState.exportStatus) {
                is ExportStatus.Exporting -> {
                    NutriCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(modifier = Modifier.padding(NutriSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(NutriSpacing.sm))
                            Text("Writing self-contained replay archive...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
                is ExportStatus.Success -> {
                    NutriCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(modifier = Modifier.padding(NutriSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = NutriIcons.CheckCircle, contentDescription = "Success", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(NutriSpacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Export Completed Successfully!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Path: ${status.path}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            }
                            IconButton(onClick = { viewModel.clearStatus() }) {
                                Text("OK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                is ExportStatus.Failure -> {
                    NutriCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(modifier = Modifier.padding(NutriSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Text("Export Failed: ${status.message}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.clearStatus() }) {
                                Text("Clear", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                else -> {}
            }

            // Existing Sessions Header
            Text(
                text = "Exported Sessions (${uiState.exportedSessions.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Existing Sessions List
            if (uiState.exportedSessions.isEmpty()) {
                NutriEmptyState(
                    message = "No exports found in context external storage.",
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.exportedSessions) { sessionName ->
                        NutriCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(NutriSpacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sessionName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Location: /exports/$sessionName",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
