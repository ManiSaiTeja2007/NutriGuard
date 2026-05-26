package com.example.ui.features.benchmark

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.benchmark.BenchmarkRunner
import com.example.ui.navigation.NavController
import com.example.ui.navigation.Screen
import com.example.ui.design.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkRunnerScreen(
    navController: NavController,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var benchmarkRunning by remember { mutableStateOf(false) }
    var benchmarkProgress by remember { mutableStateOf(0f) }
    var benchmarkReport by remember { mutableStateOf<BenchmarkReport?>(null) }

    NutriScreenScaffold(
        title = "Benchmark Suite",
        onOpenDrawer = onOpenDrawer,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(NutriSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NutriSpacing.md)
        ) {
            Text(
                text = "Offline Ingestion Benchmark",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Exercises the ingestion pipeline (OCR -> normalisation -> extraction -> alias -> canonicalization) against the master test dataset manifest to evaluate local throughput.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (benchmarkRunning) {
                NutriCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(NutriSpacing.sm)
                    ) {
                        Text("Running OCR & Ingestion Pipeline...", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        LinearProgressIndicator(
                            progress = { benchmarkProgress },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                        Text("${(benchmarkProgress * 100).toInt()}% completed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                NutriPrimaryButton(
                    text = "Execute Local Ingestion Benchmark",
                    onClick = {
                        benchmarkRunning = true
                        benchmarkProgress = 0f
                        coroutineScope.launch {
                            val report = runLocalBenchmark(context) { progress ->
                                benchmarkProgress = progress
                            }
                            benchmarkReport = report
                            benchmarkRunning = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val report = benchmarkReport
            if (report != null) {
                NutriCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Aggregated Results",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        StatusRow("Images Processed", "${report.totalProcessed}")
                        StatusRow("Avg Latency / Image", "${report.avgLatencyMs} ms")
                        StatusRow("Avg OCR Latency", "${report.avgOcrLatencyMs} ms")
                        StatusRow("Avg Ingestion Latency", "${report.avgIngestionLatencyMs} ms")
                        StatusRow("Avg Extracted Count", "${report.avgIngredientsCount} items")
                    }
                }
            }
            
            NutriSecondaryButton(
                text = "Return to Dashboard",
                onClick = { navController.navigateTo(Screen.Home) },
                modifier = Modifier.fillMaxWidth().padding(top = NutriSpacing.md)
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private suspend fun runLocalBenchmark(
    context: Context,
    onProgress: (Float) -> Unit
): BenchmarkReport = withContext(Dispatchers.Default) {
    val runner = BenchmarkRunner(context)
    try {
        val (summary, records) = runner.run("manifests/master_manifest.json", "all") { progress ->
            onProgress(progress.current.toFloat() / progress.total)
        }
        val avgIngredientsCount = if (records.isNotEmpty()) {
            records.map { it.ingredientsCount }.average().toInt()
        } else 0

        BenchmarkReport(
            totalProcessed = summary.totalImagesProcessed,
            avgOcrLatencyMs = summary.averageOcrLatencyMs,
            avgIngestionLatencyMs = summary.averageSemanticLatencyMs,
            avgLatencyMs = summary.averageTotalLatencyMs,
            avgIngredientsCount = avgIngredientsCount
        )
    } finally {
        runner.close()
    }
}

data class BenchmarkReport(
    val totalProcessed: Int,
    val avgOcrLatencyMs: Long,
    val avgIngestionLatencyMs: Long,
    val avgLatencyMs: Long,
    val avgIngredientsCount: Int
)
