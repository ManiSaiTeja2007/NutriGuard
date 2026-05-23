package com.example.ui.features.scan

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import com.example.core.ocr.OCRWord
import com.example.core.ocr.OCRLine
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.frame.FrameAnalysisResult
import com.example.core.frame.FramePipeline
import com.example.core.imaging.ImageFrame
import com.example.core.imaging.ImageSource
import com.example.core.ocr.OcrPipeline
import com.example.core.ocr.OcrResult
import com.example.core.ingredient.*
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.intelligence.correction.FailureType
import com.example.core.intelligence.correction.CorrectionResult
import com.example.data.AppSettings
import com.example.core.replay.ReplayStorageHelper
import com.example.ui.CameraPreview
import com.example.ui.navigation.NavController
import com.example.ui.navigation.Screen
import com.example.ui.state.CameraValidationState
import com.example.utils.LoadedBitmapAsset
import com.example.utils.TestLabelAssetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(if (AppSettings.debugMode) DebugMode.TestImages else DebugMode.LiveCamera) }

    // Synchronize mode if debug settings change in the background
    LaunchedEffect(AppSettings.debugMode) {
        if (!AppSettings.debugMode) {
            mode = DebugMode.LiveCamera
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Ingestion Scan", color = Color(0xFF163832), fontWeight = FontWeight.Bold) },
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
        ) {
            if (AppSettings.debugMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeButton(
                        text = "Live Camera",
                        selected = mode == DebugMode.LiveCamera,
                        onClick = { mode = DebugMode.LiveCamera }
                    )
                    ModeButton(
                        text = "Test Images",
                        selected = mode == DebugMode.TestImages,
                        onClick = { mode = DebugMode.TestImages }
                    )
                }
            }

            when (mode) {
                DebugMode.LiveCamera -> LiveCameraPanel(
                    hasCameraPermission = hasCameraPermission,
                    onRequestCameraPermission = onRequestCameraPermission,
                    navController = navController,
                    modifier = Modifier.fillMaxSize()
                )
                DebugMode.TestImages -> TestImagesPanel(
                    navController = navController,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

enum class DebugMode {
    LiveCamera, TestImages
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors(containerColor = Color(0xFF116A5B))
    } else {
        ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF163832))
    }

    if (selected) {
        Button(onClick = onClick, colors = colors) {
            Text(text = text)
        }
    } else {
        OutlinedButton(onClick = onClick, colors = colors) {
            Text(text = text)
        }
    }
}

@Composable
private fun LiveCameraPanel(
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var latestFrame by remember { mutableStateOf<FrameAnalysisResult?>(null) }
    var latestOcr by remember { mutableStateOf<OcrResult?>(null) }
    val framePipeline = remember { FramePipeline() }
    val ocrPipeline = remember { OcrPipeline() }

    DisposableEffect(Unit) {
        onDispose {
            ocrPipeline.close()
        }
    }

    if (!hasCameraPermission) {
        Column(
            modifier = modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Camera access required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF163832)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRequestCameraPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF116A5B))
            ) {
                Text(text = "Grant camera access")
            }
        }
        return
    }

    Box(modifier = modifier) {
        CameraPreview(
            framePipeline = framePipeline,
            onFrameValidated = { latestFrame = it },
            ocrPipeline = ocrPipeline,
            onOcrResult = { latestOcr = it },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(16.dp)
        ) {
            val ocrText = latestOcr?.text ?: ""
            Text(
                text = if (ocrText.isBlank()) "Looking for ingredients label..." else "Label detected!",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )

            if (ocrText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ocrText.take(120) + if (ocrText.length > 120) "..." else "",
                    color = Color(0xFFC8D4CF),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val currentOcr = latestOcr
                    if (currentOcr == null || currentOcr.text.isBlank()) {
                        Toast.makeText(context, "No text detected yet.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    coroutineScope.launch {
                        processAndNavigate(
                            context = context,
                            sourceName = "Live Camera Scan",
                            ocrResult = currentOcr,
                            navController = navController
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = ocrText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF116A5B),
                    disabledContainerColor = Color(0xFF2C4C46)
                )
            ) {
                Text("Ingest Scanned Text", color = Color.White)
            }
        }
    }
}

@Composable
private fun TestImagesPanel(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember(context) { TestLabelAssetRepository(context) }
    val imageNames = remember(repository) { repository.listImageNames() }
    val framePipeline = remember { FramePipeline(throttleMs = 0L) }
    var ocrPipeline by remember { mutableStateOf<OcrPipeline?>(null) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(CameraValidationState()) }
    var loading by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            ocrPipeline?.close()
        }
    }

    LaunchedEffect(imageNames, selectedIndex) {
        if (imageNames.isEmpty()) {
            state = CameraValidationState(status = "No test label assets found")
            return@LaunchedEffect
        }

        val fileName = imageNames[selectedIndex.coerceIn(imageNames.indices)]
        state = CameraValidationState(status = "Loading bitmap")

        state = try {
            val asset = withContext(Dispatchers.IO) {
                repository.load(fileName)
            }
            val frame = ImageFrame.BitmapFrame(
                bitmap = asset.bitmap,
                rotationDegrees = asset.rotationDegrees,
                timestampNanos = System.nanoTime(),
                source = ImageSource.TEST_ASSET
            )
            val frameResult = requireNotNull(framePipeline(frame)) {
                "Frame pipeline throttled test asset unexpectedly."
            }
            val pipeline = ocrPipeline ?: OcrPipeline().also {
                ocrPipeline = it
            }
            val ocrResult = withContext(Dispatchers.Default) {
                pipeline(Pair(frame, frameResult))
            }

            CameraValidationState(
                asset = asset,
                frameResult = frameResult,
                ocrResult = ocrResult,
                status = when {
                    ocrResult.skippedReason != null -> "Frame valid, OCR skipped"
                    ocrResult.segmentsProcessed > 1 -> "Frame valid, OCR merged ${ocrResult.segmentsProcessed} segments"
                    ocrResult.text.isBlank() -> "Frame valid, OCR returned no text"
                    else -> "Frame valid, OCR complete"
                }
            )
        } catch (error: Throwable) {
            CameraValidationState(
                status = "Pipeline failed",
                errorMessage = error.message ?: error::class.java.simpleName
            )
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (imageNames.isEmpty()) {
            Text(
                text = state.status,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF3D4946)
            )
            return@Column
        }

        TestImagePreview(state.asset, state.ocrResult)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    selectedIndex = if (selectedIndex == 0) imageNames.lastIndex else selectedIndex - 1
                },
                modifier = Modifier.width(110.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF163832))
            ) {
                Text(text = "Previous")
            }
            Text(
                text = "${selectedIndex + 1} / ${imageNames.size}",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF3D4946),
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    selectedIndex = if (selectedIndex == imageNames.lastIndex) 0 else selectedIndex + 1
                },
                modifier = Modifier.width(110.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF116A5B))
            ) {
                Text(text = "Next")
            }
        }

        val ocrResult = state.ocrResult
        val ocrText = ocrResult?.text ?: ""

        Button(
            onClick = {
                if (ocrResult == null || ocrText.isBlank()) {
                    Toast.makeText(context, "No text detected in test image.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                loading = true
                coroutineScope.launch {
                    processAndNavigate(
                        context = context,
                        sourceName = state.asset?.fileName ?: "Test Image",
                        ocrResult = ocrResult,
                        navController = navController
                    )
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = ocrText.isNotBlank() && !loading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF116A5B),
                disabledContainerColor = Color(0xFF2C4C46)
            )
        ) {
            if (loading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text("Ingest Test Image")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (AppSettings.ocrDiagnostics) {
            com.example.ui.components.CameraMetadataPanel(
                title = "Test Image Metadata",
                fileName = state.asset?.fileName ?: imageNames[selectedIndex],
                frameResult = state.frameResult,
                ocrResult = state.ocrResult,
                status = state.status,
                errorMessage = state.errorMessage
            )
            Spacer(modifier = Modifier.height(4.dp))
            com.example.ui.components.CameraOcrOutputPanel(state.ocrResult)
        }
    }
}

@Composable
private fun TestImagePreview(
    asset: LoadedBitmapAsset?,
    ocrResult: OcrResult?
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE8EFEC))
            .border(1.dp, Color(0xFFC8D4CF), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (asset == null) {
            Text(text = "Loading preview", color = Color(0xFF3D4946))
        } else {
            val imageBitmap = remember(asset.bitmap) { asset.bitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = asset.fileName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .graphicsLayer {
                        rotationZ = asset.rotationDegrees.toFloat()
                    },
                contentScale = ContentScale.Fit
            )

            if (AppSettings.ocrDiagnostics && ocrResult != null) {
                val density = LocalDensity.current
                val paddingPx = with(density) { 8.dp.toPx() }
                
                val containerW = maxWidth.value * density.density - (paddingPx * 2)
                val containerH = maxHeight.value * density.density - (paddingPx * 2)
                
                val bitmapW = asset.bitmap.width.toFloat()
                val bitmapH = asset.bitmap.height.toFloat()
                
                if (containerW > 0 && containerH > 0 && bitmapW > 0 && bitmapH > 0) {
                    val (scale, offset) = calculateFitScaleAndOffset(containerW, containerH, bitmapW, bitmapH)
                    val (offsetX, offsetY) = offset

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        // 1. Draw detected words (yellow stroke)
                        ocrResult.ocrWords.forEach { word ->
                            val left = offsetX + word.bounds.left * scale
                            val top = offsetY + word.bounds.top * scale
                            val right = offsetX + word.bounds.right * scale
                            val bottom = offsetY + word.bounds.bottom * scale
                            
                            drawRect(
                                color = Color(0xFFF39C12).copy(alpha = 0.6f),
                                topLeft = Offset(left, top),
                                size = Size(right - left, bottom - top),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }

                        // 2. Draw detected ingredient paragraphs (green fill and stroke)
                        ocrResult.detectedParagraphs.forEach { line ->
                            val left = offsetX + line.bounds.left * scale
                            val top = offsetY + line.bounds.top * scale
                            val right = offsetX + line.bounds.right * scale
                            val bottom = offsetY + line.bounds.bottom * scale

                            drawRect(
                                color = Color(0xFF2ECC71).copy(alpha = 0.15f),
                                topLeft = Offset(left, top),
                                size = Size(right - left, bottom - top)
                            )
                            
                            drawRect(
                                color = Color(0xFF27AE60).copy(alpha = 0.8f),
                                topLeft = Offset(left, top),
                                size = Size(right - left, bottom - top),
                                style = Stroke(width = 1.5f.dp.toPx())
                            )
                        }

                        // 3. Draw reading order indexes and reconstructed lines (blue dashed stroke)
                        ocrResult.reconstructedLines.forEachIndexed { index, line ->
                            val left = offsetX + line.bounds.left * scale
                            val top = offsetY + line.bounds.top * scale
                            
                            drawRect(
                                color = Color(0xFF3498DB).copy(alpha = 0.5f),
                                topLeft = Offset(left, top),
                                size = Size(
                                    (line.bounds.right - line.bounds.left) * scale,
                                    (line.bounds.bottom - line.bounds.top) * scale
                                ),
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(10f, 10f), 0f
                                    )
                                )
                            )

                            // Circle badge for reading order
                            drawCircle(
                                color = Color(0xFFE74C3C),
                                radius = 8.dp.toPx(),
                                center = Offset(left, top + ((line.bounds.bottom - line.bounds.top) * scale / 2f))
                            )
                            
                            drawContext.canvas.nativeCanvas.drawText(
                                (index + 1).toString(),
                                left - 3.dp.toPx(),
                                top + ((line.bounds.bottom - line.bounds.top) * scale / 2f) + 3.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 10.dp.toPx()
                                    isFakeBoldText = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun calculateFitScaleAndOffset(
    containerW: Float,
    containerH: Float,
    bitmapW: Float,
    bitmapH: Float
): Pair<Float, Pair<Float, Float>> {
    val bitmapRatio = bitmapW / bitmapH
    val containerRatio = containerW / containerH

    val scale = if (bitmapRatio > containerRatio) {
        containerW / bitmapW
    } else {
        containerH / bitmapH
    }

    val actualW = bitmapW * scale
    val actualH = bitmapH * scale

    val offsetX = (containerW - actualW) / 2f
    val offsetY = (containerH - actualH) / 2f

    return Pair(scale, Pair(offsetX, offsetY))
}

private suspend fun processAndNavigate(
    context: Context,
    sourceName: String,
    ocrResult: OcrResult,
    navController: NavController
) = withContext(Dispatchers.Default) {
    val ocrText = ocrResult.text
    val ocrLatency = ocrResult.processingLatencyMs
    val ocrConfidence = ocrResult.averageConfidence ?: 0.8f

    val vocabulary = IngredientVocabulary()
    val pipeline = IngredientNormalizationPipeline(vocabulary)

    val ingestionResult = pipeline(Pair(ocrText, ocrConfidence))

    // JSON serialization for results passing
    val canonicalJson = JSONArray().apply {
        ingestionResult.correction.output.forEach { result ->
            put(JSONObject().apply {
                put("canonical", result.canonical)
                put("confidence", result.confidence.toDouble())
                put("originalToken", result.originalToken)
                put("ontologyCategory", result.ontologyCategory ?: "")
                put("disambiguationRule", result.disambiguationRule ?: "")
                put("groupPath", result.groupPath)

                val stepsArr = JSONArray()
                result.debugSteps.forEach { stepsArr.put(it) }
                put("debugSteps", stepsArr)

                val failsArr = JSONArray()
                result.failures.forEach { failsArr.put(it.name) }
                put("failures", failsArr)

                val phraseArr = JSONArray()
                result.phraseWindow.forEach { phraseArr.put(it) }
                put("phraseWindow", phraseArr)
            })
        }
    }.toString()

    val latenciesMap = mapOf(
        "ocr" to ocrLatency,
        "normalization" to ingestionResult.normalization.latencyMs,
        "extraction" to ingestionResult.extraction.latencyMs,
        "grouping" to ingestionResult.grouping.latencyMs,
        "phrase_correction" to ingestionResult.phraseCorrection.latencyMs,
        "correction" to ingestionResult.correction.latencyMs
    )
    val latenciesJson = JSONObject().apply {
        latenciesMap.forEach { (k, v) -> put(k, v) }
    }.toString()

    // Determine failures & Save replay if enabled
    val failuresList = mutableListOf<Map<String, Any>>()

    // Add stage level failures
    ingestionResult.normalization.failures.forEach { fail ->
        failuresList.add(mapOf(
            "failure_type" to fail.name,
            "stage" to "normalization",
            "details" to "Normalization failed: output was blank"
        ))
    }
    ingestionResult.extraction.failures.forEach { fail ->
        failuresList.add(mapOf(
            "failure_type" to fail.name,
            "stage" to "extraction",
            "details" to "Extraction failed: zero tokens parsed from input"
        ))
    }
    ingestionResult.correction.output.forEach { res ->
        res.failures.forEach { fail ->
            failuresList.add(mapOf(
                "failure_type" to fail.name,
                "stage" to "correction",
                "details" to when(fail) {
                    FailureType.UNKNOWN_INGREDIENT_FAILURE -> "Unknown ingredient \"${res.originalToken}\" not found in vocabulary or ontology."
                    FailureType.AMBIGUOUS_MATCH_FAILURE -> "Ambiguous match detected for \"${res.originalToken}\"."
                    FailureType.FUZZY_CORRECTION_FAILURE -> "Fuzzy correction quality exception for \"${res.originalToken}\"."
                    FailureType.LOW_CONFIDENCE_CORRECTION_FAILURE -> "Low correction confidence for \"${res.originalToken}\" -> \"${res.canonical}\"."
                    else -> "Correction exception detected."
                }
            ))
        }
    }

    if (AppSettings.replaySaving && failuresList.isNotEmpty()) {
        val metrics = mapOf(
            "avg_confidence" to (ingestionResult.correction.output.map { it.confidence }.average().takeIf { !it.isNaN() } ?: 0.0),
            "ingredient_count" to ingestionResult.correction.output.size.toDouble(),
            "ocr_character_count" to ocrText.length.toDouble()
        )
        ReplayStorageHelper.saveReplay(
            context = context,
            sourceImage = sourceName,
            ocrOutput = ocrText,
            normalizedText = ingestionResult.normalization.output,
            extractedIngredients = ingestionResult.extraction.output,
            canonicalIngredients = ingestionResult.correction.output,
            metrics = metrics,
            failures = failuresList,
            latencyMetrics = latenciesMap,
            ocrWords = ocrResult.ocrWords,
            reconstructedLines = ocrResult.reconstructedLines,
            detectedParagraphs = ocrResult.detectedParagraphs,
            passesRun = ocrResult.passesRun
        )
    }

    // Results screen navigation arguments
    val routeArgs = Screen.Results(
        rawOcrText = ocrText,
        normalizedText = ingestionResult.normalization.output,
        extractedTokens = ingestionResult.extraction.output,
        canonicalJson = canonicalJson,
        latencyJson = latenciesJson
    )

    withContext(Dispatchers.Main) {
        navController.navigateTo(routeArgs)
    }
}
