package com.example.ui.features.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.example.ui.navigation.NavController
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayViewerScreen(
    replayId: String,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var replayData by remember { mutableStateOf<JSONObject?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(replayId) {
        try {
            val file = File(context.cacheDir, "${replayId}_replay.json")
            if (file.exists()) {
                replayData = JSONObject(file.readText())
            } else {
                errorMessage = "Replay file not found."
            }
        } catch (e: Exception) {
            errorMessage = "Failed to parse replay: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Replay Inspector", color = Color(0xFF163832), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text("Back", color = Color(0xFF116A5B))
                    }
                },
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (errorMessage != null) {
                Text(text = errorMessage!!, color = Color.Red, style = MaterialTheme.typography.bodyLarge)
                return@Column
            }

            val data = replayData
            if (data == null) {
                Text("Loading replay details...", color = Color(0xFF7D8E8A))
                return@Column
            }

            // 1. Replay Metadata Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Replay ID: $replayId", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF163832))
                    Text("Source: ${data.optString("source_image")}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7D8E8A))
                    Text("Timestamp: ${data.optString("timestamp")}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7D8E8A))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text("Pipeline: v${data.optString("pipeline_version")}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF116A5B), fontWeight = FontWeight.Bold)
                        Text("Schema: v${data.optString("benchmark_schema_version")}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF116A5B), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 2. Benchmark Metrics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Benchmark Metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF163832))
                    Spacer(modifier = Modifier.height(10.dp))
                    val metrics = data.optJSONObject("metrics")
                    if (metrics != null) {
                        metrics.keys().forEach { key ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(key.uppercase(), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7D8E8A))
                                val v = metrics.optDouble(key)
                                val valStr = if (key.contains("accuracy")) "${(v * 100).toInt()}%" else "%.4f".format(v)
                                Text(valStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF163832))
                            }
                        }
                    } else {
                        Text("No metrics recorded.", color = Color(0xFF7D8E8A))
                    }
                }
            }

            // 3. Failures List Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Failure Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF163832))
                    Spacer(modifier = Modifier.height(10.dp))
                    val failures = data.optJSONArray("failures")
                    if (failures != null && failures.length() > 0) {
                        for (i in 0 until failures.length()) {
                            val fail = failures.getJSONObject(i)
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(fail.optString("failure_type"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFFC0392B))
                                    Text("Stage: " + fail.optString("stage").uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFF7D8E8A))
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(fail.optString("details"), style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D6E6A))
                            }
                            if (i < failures.length() - 1) {
                                HorizontalDivider(color = Color(0xFFF0F5F3), modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    } else {
                        Text("No failures detected. Stage passes successfully.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF116A5B), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 4. OCR raw and normalized text
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("OCR Text Ingested", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF163832))
                    Text("Raw Ingested Text:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF5D6E6A))
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF7FAF9)).border(1.dp, Color(0xFFE8EFEC)).padding(8.dp)) {
                        Text(data.optString("ocr_output"), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Normalized Ingested Text:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF5D6E6A))
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF7FAF9)).border(1.dp, Color(0xFFE8EFEC)).padding(8.dp)) {
                        Text(data.optString("normalized_text"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 5. Ingredient correction waterfall traces
            val canonicalArr = data.optJSONArray("canonical_ingredients")
            if (canonicalArr != null && canonicalArr.length() > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ingredient Correction Traces", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF163832))
                        Text("${canonicalArr.length()} ingredients", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7D8E8A))
                        Spacer(modifier = Modifier.height(4.dp))

                        for (i in 0 until canonicalArr.length()) {
                            val ing = canonicalArr.getJSONObject(i)
                            val canonical = ing.optString("canonical")
                            val category = ing.optString("ontologyCategory").ifBlank { null }
                            val rule = ing.optString("disambiguationRule").ifBlank { null }
                            val stepsArr = ing.optJSONArray("debugSteps")
                            val failsArr = ing.optJSONArray("failures")
                            val hasFailure = failsArr != null && failsArr.length() > 0

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        canonical,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasFailure) Color(0xFFC0392B) else Color(0xFF163832),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (category != null) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFE8F5F0), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(category.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = Color(0xFF0D5C4A), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                if (rule != null) {
                                    Text("context rule: $rule", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9DADA9))
                                }
                                if (stepsArr != null && stepsArr.length() > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF0F5F3))
                                            .border(1.dp, Color(0xFFE8EFEC), RoundedCornerShape(4.dp))
                                            .padding(8.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            for (j in 0 until stepsArr.length()) {
                                                val step = stepsArr.getString(j)
                                                val isHeader = step.startsWith("OCR:") ||
                                                    step.startsWith("normalized:") ||
                                                    step.startsWith("ontology:") ||
                                                    step.startsWith("canonicalized:") ||
                                                    step.startsWith("vocabulary hit:") ||
                                                    step.startsWith("disambiguation:")
                                                Text(
                                                    step,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isHeader) Color(0xFF163832) else Color(0xFF5D6E6A),
                                                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (i < canonicalArr.length() - 1) {
                                HorizontalDivider(color = Color(0xFFF0F5F3), modifier = Modifier.padding(vertical = 6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
