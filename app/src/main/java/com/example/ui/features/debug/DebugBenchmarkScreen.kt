package com.example.ui.features.debug

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.frame.FramePipeline
import com.example.core.imaging.ImageFrame
import com.example.core.imaging.ImageSource
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.ingredient.IngredientNormalizationPipeline
import com.example.core.ocr.OcrPipeline
import com.example.ui.navigation.NavController
import com.example.ui.navigation.Screen
import com.example.utils.TestLabelAssetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugBenchmarkScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var selectedTab by remember { mutableStateOf(0) }
    var replaysList by remember { mutableStateOf<List<ReplayItem>>(emptyList()) }
    
    // In-app benchmark stats
    var benchmarkRunning by remember { mutableStateOf(false) }
    var benchmarkProgress by remember { mutableStateOf(0f) }
    var benchmarkReport by remember { mutableStateOf<BenchmarkReport?>(null) }

    val repository = remember { TestLabelAssetRepository(context) }
    val testImages = remember { repository.listImageNames().take(5) }

    // Load replays from cache
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
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
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Diagnostics", color = Color(0xFF163832), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7FAF9))
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Color(0xFF116A5B)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Benchmarking") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Local Replays") }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Benchmark Panel
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Offline Ingestion Benchmark",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF163832)
                        )
                        Text(
                            text = "Exercises the ingestion pipeline (OCR -> normalisation -> extraction -> alias -> canonicalization) against $testImages.size seeded test label images to evaluate local throughput.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7D8E8A)
                        )

                        if (benchmarkRunning) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Running OCR & Ingestion Pipeline...", fontWeight = FontWeight.SemiBold)
                                    LinearProgressIndicator(
                                        progress = benchmarkProgress,
                                        color = Color(0xFF116A5B),
                                        modifier = Modifier.fillMaxWidth().height(8.dp)
                                    )
                                    Text("${(benchmarkProgress * 100).toInt()}% completed", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    benchmarkRunning = true
                                    benchmarkProgress = 0f
                                    coroutineScope.launch {
                                        val report = runLocalBenchmark(context, repository, testImages) { progress ->
                                            benchmarkProgress = progress
                                        }
                                        benchmarkReport = report
                                        benchmarkRunning = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF116A5B))
                            ) {
                                Text("Execute Local Ingestion Benchmark")
                            }
                        }

                        val report = benchmarkReport
                        if (report != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Aggregated Results",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF163832)
                                    )
                                    StatusRow("Images Processed", "${report.totalProcessed}")
                                    StatusRow("Avg Latency / Image", "${report.avgLatencyMs} ms")
                                    StatusRow("Avg OCR Latency", "${report.avgOcrLatencyMs} ms")
                                    StatusRow("Avg Ingestion Latency", "${report.avgIngestionLatencyMs} ms")
                                    StatusRow("Avg Extracted Count", "${report.avgIngredientsCount} items")
                                }
                            }
                        }
                        
                        Button(
                            onClick = { navController.clearBackStackAndNavigate(Screen.Home) },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF163832))
                        ) {
                            Text("Return to Dashboard")
                        }
                    }
                }
                1 -> {
                    // Local Replays Panel
                    if (replaysList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No local replays logged in cache.", color = Color(0xFF7D8E8A))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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
    }
}

@Composable
private fun ReplayRowItem(item: ReplayItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Replay: ${item.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF163832)
                )
                Text(
                    text = item.sourceImage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7D8E8A)
                )
                Text(
                    text = "Scanned: ${item.timestamp}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9DADA9)
                )
            }
            if (item.failuresCount > 0) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFCEFEF), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${item.failuresCount} FAILURES",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFC0392B),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFE8EFEC), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "PASSED",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF116A5B),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7D8E8A))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF163832))
    }
}

private suspend fun runLocalBenchmark(
    context: Context,
    repository: TestLabelAssetRepository,
    imageNames: List<String>,
    onProgress: (Float) -> Unit
): BenchmarkReport = withContext(Dispatchers.Default) {
    val framePipeline = FramePipeline(throttleMs = 0L)
    val ocrPipeline = OcrPipeline()
    val vocabulary = IngredientVocabulary()
    val pipeline = IngredientNormalizationPipeline(vocabulary)

    var totalOcrLatency = 0L
    var totalIngestionLatency = 0L
    var totalIngredientsCount = 0

    imageNames.forEachIndexed { index, name ->
        try {
            val asset = repository.load(name)
            val frame = ImageFrame.BitmapFrame(
                bitmap = asset.bitmap,
                rotationDegrees = asset.rotationDegrees,
                timestampNanos = System.nanoTime(),
                source = ImageSource.TEST_ASSET
            )
            val frameResult = requireNotNull(framePipeline(frame))

            // Measure OCR
            val ocrStart = System.currentTimeMillis()
            val ocrResult = ocrPipeline(Pair(frame, frameResult))
            totalOcrLatency += (System.currentTimeMillis() - ocrStart)

            // Measure full ingestion pipeline
            val ingestionStart = System.currentTimeMillis()
            val ingestionResult = pipeline(Pair(ocrResult.text, ocrResult.averageConfidence ?: 0.8f))
            totalIngestionLatency += (System.currentTimeMillis() - ingestionStart)

            totalIngredientsCount += ingestionResult.correction.output.size
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onProgress((index + 1).toFloat() / imageNames.size)
    }

    ocrPipeline.close()

    val total = imageNames.size
    BenchmarkReport(
        totalProcessed = total,
        avgOcrLatencyMs = totalOcrLatency / total,
        avgIngestionLatencyMs = totalIngestionLatency / total,
        avgLatencyMs = (totalOcrLatency + totalIngestionLatency) / total,
        avgIngredientsCount = totalIngredientsCount / total
    )
}

data class ReplayItem(
    val id: String,
    val sourceImage: String,
    val failuresCount: Int,
    val timestamp: String
)

data class BenchmarkReport(
    val totalProcessed: Int,
    val avgOcrLatencyMs: Long,
    val avgIngestionLatencyMs: Long,
    val avgLatencyMs: Long,
    val avgIngredientsCount: Int
)
