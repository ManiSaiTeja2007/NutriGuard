package com.example.ui.features.production

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.config.FeatureFlags
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
                title = { Text("Analysis Results", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateTo(Screen.Home) }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back to Home")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Potential Concerns Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Concerns Icon",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Potential Concerns",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    val concernedIngredients = parsedIngredients.filter { it.failures.isNotEmpty() }
                    if (concernedIngredients.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Passed Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "All ingredients recognized successfully. No processing or consistency issues detected.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        concernedIngredients.forEach { ingredient ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ingredient.canonical,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val failureDesc = ingredient.failures.map { getFriendlyFailureDescription(it) }.distinct().joinToString(", ")
                                    Text(
                                        text = failureDesc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }

            // 2. Ingredients Detected Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ingredients Detected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (parsedIngredients.isEmpty()) {
                        Text(
                            text = "No ingredients extracted.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        parsedIngredients.forEach { ingredient ->
                            IngredientItemRow(ingredient)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }

            // 3. Diagnostics Panel (if diagnostics are enabled for this build mode)
            if (FeatureFlags.enableDiagnostics) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Developer Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "Hide" else "Show", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (expanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            // Text dumps
                            Text(
                                text = "Raw OCR Text:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Text(text = args.rawOcrText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Normalized Form:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Text(text = args.normalizedText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Pipeline Stage Latencies",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            latencies.forEach { (stage, timeMs) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stage.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$timeMs ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total Runtime",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${latencies.values.sum()} ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Navigation Button
            Button(
                onClick = { navController.clearBackStackAndNavigate(Screen.Home) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Return to Dashboard")
            }
        }
    }
}

@Composable
private fun IngredientItemRow(ingredient: CorrectionResult) {
    var traceExpanded by remember { mutableStateOf(false) }
    val showTraceOption = FeatureFlags.enableDiagnostics && AppSettings.showOverlays

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
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (ingredient.originalToken != ingredient.canonical && ingredient.originalToken.isNotBlank()) {
                    Text(
                        text = "Original: \"${ingredient.originalToken}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Ontology category badge
                val category = ingredient.ontologyCategory
                if (category != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = category.replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                // Disambiguation rule (debug mode)
                if (showTraceOption && ingredient.disambiguationRule != null) {
                    Text(
                        text = "rule: ${ingredient.disambiguationRule}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Match tag chip / failures
            val hasFailures = ingredient.failures.isNotEmpty()
            val tagColor = if (hasFailures) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
            val textColor = if (hasFailures) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
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

        // Trace expansion button when diagnostics and overlays are active
        if (showTraceOption) {
            TextButton(
                onClick = { traceExpanded = !traceExpanded },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = if (traceExpanded) "Hide Ingestion Trace" else "Show Ingestion Trace",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (traceExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
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
                                color = if (isStageHeader) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isStageHeader) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getFriendlyFailureDescription(failure: FailureType): String {
    return when (failure) {
        FailureType.UNKNOWN_INGREDIENT_FAILURE -> "Unknown ingredient"
        FailureType.OCR_AMBIGUITY_FAILURE -> "OCR character confusion detected (e.g. O vs 0)"
        FailureType.ADDITIVE_NOTATION_FAILURE -> "Ambiguous food additive / E-number notation"
        FailureType.FUZZY_CORRECTION_FAILURE -> "Fuzzy matching replacement applied"
        FailureType.LOW_CONFIDENCE_FAILURE, FailureType.LOW_CONFIDENCE_CORRECTION_FAILURE -> "Low confidence matching result"
        FailureType.CONTEXT_DISAMBIGUATION_FAILURE -> "Contextual correction ambiguity"
        FailureType.PHRASE_CORRECTION_FAILURE -> "Multi-word token grouping issue"
        FailureType.OCR_FRAGMENTATION_FAILURE -> "Fragmented OCR lines reconstructed"
        FailureType.LINE_RECONSTRUCTION_FAILURE -> "Imprecise OCR line alignment"
        else -> "OCR verification warning: ${failure.name.lowercase().replace("_", " ")}"
    }
}
