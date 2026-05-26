package com.example.ui.features.developer.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.config.BuildCapabilities
import com.example.core.export.ExportOrchestrator
import com.example.core.export.ExportState
import com.example.core.export.PipelineSnapshotRepository
import com.example.ui.design.*

@Composable
fun ExpandableDeveloperSection(
    executionId: String,
    modifier: Modifier = Modifier
) {
    if (!BuildCapabilities.isDeveloperBuild) return

    val context = LocalContext.current
    val exportState by ExportOrchestrator.state.collectAsState()

    var showResultDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogPath by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is ExportState.Exporting -> {
                showResultDialog = false
            }
            is ExportState.Success -> {
                dialogTitle = "Export Complete"
                dialogPath = state.path
                dialogError = null
                showResultDialog = true
            }
            is ExportState.Failure -> {
                dialogTitle = "Export Failed"
                dialogPath = ""
                dialogError = state.message
                showResultDialog = true
            }
            is ExportState.Idle -> {
                showResultDialog = false
            }
        }
    }

    val snapshot = PipelineSnapshotRepository.get(executionId)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), NutriShapes.card)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), NutriShapes.card)
            .padding(NutriSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NutriSpacing.md)
    ) {
        Text(
            text = "Developer Controls (executionId: ${executionId.take(8)}...)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )

        if (snapshot == null) {
            Text(
                text = "Snapshot details not found in cache.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            return
        }

        // Section 1: Exports Group
        DeveloperGroup(title = "Exports") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NutriSpacing.sm)
            ) {
                NutriSecondaryButton(
                    text = "Export Session",
                    onClick = {
                        ExportOrchestrator.exportSession(context, snapshot)
                    },
                    modifier = Modifier.weight(1f).testTag("export_session_button"),
                    enabled = exportState !is ExportState.Exporting
                )

                NutriSecondaryButton(
                    text = "Export Replay",
                    onClick = {
                        ExportOrchestrator.exportReplay(context, snapshot)
                    },
                    modifier = Modifier.weight(1f).testTag("export_replay_button"),
                    enabled = exportState !is ExportState.Exporting
                )

                NutriSecondaryButton(
                    text = "Export Overlay",
                    onClick = {
                        ExportOrchestrator.exportOverlay(context, snapshot)
                    },
                    modifier = Modifier.weight(1f).testTag("export_overlay_button"),
                    enabled = exportState !is ExportState.Exporting
                )
            }
        }

        // Section 2: Analysis Group
        var showTrace by remember { mutableStateOf(false) }
        DeveloperGroup(title = "Analysis") {
            Column(verticalArrangement = Arrangement.spacedBy(NutriSpacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NutriSpacing.sm)
                ) {
                    NutriSecondaryButton(
                        text = if (showTrace) "Hide Trace" else "Semantic Trace",
                        onClick = { showTrace = !showTrace },
                        modifier = Modifier.weight(1f).testTag("view_semantic_trace_button")
                    )
                    NutriSecondaryButton(
                        text = "OCR Overlay",
                        onClick = {
                            Toast.makeText(context, "OCR Overlay Active (${snapshot.result.ocrLines.size} lines)", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).testTag("ocr_overlay_button")
                    )
                }

                AnimatedVisibility(visible = showTrace) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(NutriSpacing.sm)
                    ) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .verticalScroll(scrollState)
                        ) {
                            snapshot.result.replayTrace.forEach { trace ->
                                Text(
                                    text = "${trace.stageName.uppercase()}: ${trace.output.take(100)} (${trace.latencyMs}ms)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Benchmark Group
        DeveloperGroup(title = "Benchmark") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NutriSpacing.sm)
            ) {
                NutriSecondaryButton(
                    text = "Save Failure Case",
                    onClick = {
                        Toast.makeText(context, "Failure case marked for export.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).testTag("save_failure_case_button")
                )
                NutriSecondaryButton(
                    text = "Compare Metrics",
                    onClick = {
                        Toast.makeText(context, "Latency: ${snapshot.result.metrics.totalLatencyMs}ms", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).testTag("compare_benchmark_button")
                )
            }
        }

        // Non-blocking Loading Indicator overlay if exporting
        if (exportState is ExportState.Exporting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f), NutriShapes.card)
                    .padding(NutriSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(NutriSpacing.sm))
                Text(
                    text = "Processing export...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Confirmation dialog showing results + copy-to-clipboard action
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = {
                showResultDialog = false
                ExportOrchestrator.clearState()
            },
            title = {
                Text(
                    text = dialogTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NutriSpacing.sm)) {
                    Text(
                        text = "executionId:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = executionId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (dialogError != null) {
                        Text(
                            text = "Error: $dialogError",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "Saved To:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = dialogPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(NutriSpacing.sm)
                ) {
                    if (dialogError == null && dialogPath.isNotBlank()) {
                        NutriSecondaryButton(
                            text = "Copy Path",
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("export_path", dialogPath)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("export_copy_path_button")
                        )
                    }
                    Button(
                        onClick = {
                            showResultDialog = false
                            ExportOrchestrator.clearState()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("export_close_dialog_button")
                    ) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@Composable
private fun DeveloperGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NutriSpacing.xs)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}
