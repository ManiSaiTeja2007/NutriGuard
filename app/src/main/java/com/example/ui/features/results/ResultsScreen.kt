package com.example.ui.features.results

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.intelligence.correction.CorrectionResult
import com.example.core.intelligence.correction.FailureType
import com.example.data.AppSettings
import com.example.ui.navigation.NavController
import com.example.ui.navigation.Screen
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    args: Screen.Results,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val parsedIngredients = remember(args.canonicalJson) {
        val list = mutableListOf<CorrectionResult>()
        try {
            val arr = JSONArray(args.canonicalJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val debugStepsArr = obj.getJSONArray("debugSteps")
                val debugSteps = mutableListOf<String>()
                for (j in 0 until debugStepsArr.length()) {
                    debugSteps.add(debugStepsArr.getString(j))
                }

                val failsArr = obj.getJSONArray("failures")
                val failures = mutableListOf<FailureType>()
                for (j in 0 until failsArr.length()) {
                    failures.add(FailureType.valueOf(failsArr.getString(j)))
                }

                val phraseWindowArr = obj.optJSONArray("phraseWindow")
                val phraseWindow = mutableListOf<String>()
                if (phraseWindowArr != null) {
                    for (j in 0 until phraseWindowArr.length()) phraseWindow.add(phraseWindowArr.getString(j))
                }

                list.add(
                    CorrectionResult(
                        canonical = obj.getString("canonical"),
                        confidence = obj.getDouble("confidence").toFloat(),
                        failures = failures,
                        debugSteps = debugSteps,
                        phraseWindow = phraseWindow,
                        ontologyCategory = obj.optString("ontologyCategory").ifBlank { null },
                        disambiguationRule = obj.optString("disambiguationRule").ifBlank { null },
                        groupPath = obj.optString("groupPath").ifBlank { "root" }
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    val latencies = remember(args.latencyJson) {
        val map = mutableMapOf<String, Long>()
        try {
            val obj = JSONObject(args.latencyJson)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.getLong(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        map
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ingested Results", color = Color(0xFF163832), fontWeight = FontWeight.Bold) },
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
            // 1. Expected Canonical Ingredient list
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Canonical Ingredients & Corrections",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF163832)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (parsedIngredients.isEmpty()) {
                        Text("No ingredients extracted.", color = Color(0xFF7D8E8A))
                    } else {
                        parsedIngredients.forEach { ingredient ->
                            IngredientItemRow(ingredient)
                            HorizontalDivider(color = Color(0xFFF0F5F3), modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }

            // 2. Expandable Raw OCR Text card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                var expanded by remember { mutableStateOf(false) }
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Raw OCR & Normalized Text",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF163832)
                        )
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Hide" else "Show", color = Color(0xFF116A5B))
                        }
                    }
                    if (expanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Raw OCR Text:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D6E6A)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFFF7FAF9))
                                .border(1.dp, Color(0xFFE8EFEC))
                                .padding(8.dp)
                        ) {
                            Text(text = args.rawOcrText, style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Normalized Form:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D6E6A)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFFF7FAF9))
                                .border(1.dp, Color(0xFFE8EFEC))
                                .padding(8.dp)
                        ) {
                            Text(text = args.normalizedText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // 3. Debug Metadata (visible if AppSettings.debugMode is enabled)
            if (AppSettings.debugMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pipeline Stage Latencies",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF163832)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        latencies.forEach { (stage, timeMs) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stage.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF7D8E8A))
                                Text("$timeMs ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF163832))
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF0F5F3), modifier = Modifier.padding(vertical = 6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Runtime", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF163832))
                            Text("${latencies.values.sum()} ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF116A5B))
                        }
                    }
                }
            }

            // Navigation Options
            Button(
                onClick = { navController.clearBackStackAndNavigate(Screen.Home) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF116A5B))
            ) {
                Text("Return to Dashboard")
            }
        }
    }
}

@Composable
private fun IngredientItemRow(ingredient: CorrectionResult) {
    var traceExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ingredient.canonical,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF163832)
                )
                if (ingredient.originalToken != ingredient.canonical && ingredient.originalToken.isNotBlank()) {
                    Text(
                        text = "Original: \"${ingredient.originalToken}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7D8E8A)
                    )
                }
                // Ontology category badge
                val category = ingredient.ontologyCategory
                if (category != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE8F5F0), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = category.replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF0D5C4A),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                // Disambiguation rule (debug mode)
                if (AppSettings.debugMode && ingredient.disambiguationRule != null) {
                    Text(
                        text = "rule: ${ingredient.disambiguationRule}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF9DADA9)
                    )
                }
            }

            // Match tag chip / failures
            val hasFailures = ingredient.failures.isNotEmpty()
            val tagColor = if (hasFailures) Color(0xFFFCEFEF) else Color(0xFFE8EFEC)
            val textColor = if (hasFailures) Color(0xFFC0392B) else Color(0xFF116A5B)
            val labelText = if (hasFailures) {
                ingredient.failures.first().name
            } else {
                "PASSED (${(ingredient.confidence * 100).toInt()}%)"
            }

            Box(
                modifier = Modifier
                    .background(tagColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Trace expansion button when debugMode is active
        if (AppSettings.debugMode) {
            TextButton(
                onClick = { traceExpanded = !traceExpanded },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = if (traceExpanded) "Hide Ingestion Trace" else "Show Ingestion Trace",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF116A5B)
                )
            }

            if (traceExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0xFFF0F5F3))
                        .border(1.dp, Color(0xFFE8EFEC), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        ingredient.debugSteps.forEach { step ->
                            val isStageHeader = step.startsWith("OCR:") ||
                                step.startsWith("normalized:") ||
                                step.startsWith("phrase-normalized:") ||
                                step.startsWith("ontology:") ||
                                step.startsWith("vocabulary hit:") ||
                                step.startsWith("canonicalized:") ||
                                step.startsWith("disambiguation:") ||
                                step.startsWith("category:")
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isStageHeader) Color(0xFF163832) else Color(0xFF5D6E6A),
                                fontWeight = if (isStageHeader) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
