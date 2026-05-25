package com.example.ui.features.scan

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.frame.FrameAnalysisResult
import com.example.core.ocr.OcrResult
import com.example.core.ocr.preprocessing.OcrPreprocessor
import com.example.data.AppSettings
import com.example.ui.CameraPreview
import com.example.ui.navigation.NavController
import com.example.utils.LoadedBitmapAsset
import com.example.utils.TestLabelAssetRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                        selected = uiState.mode == DebugMode.LiveCamera,
                        onClick = { viewModel.setMode(DebugMode.LiveCamera) }
                    )
                    ModeButton(
                        text = "Test Images",
                        selected = uiState.mode == DebugMode.TestImages,
                        onClick = { viewModel.setMode(DebugMode.TestImages) }
                    )
                }
            }

            when (uiState.mode) {
                DebugMode.LiveCamera -> LiveCameraPanel(
                    hasCameraPermission = hasCameraPermission,
                    onRequestCameraPermission = onRequestCameraPermission,
                    navController = navController,
                    viewModel = viewModel,
                    uiState = uiState,
                    modifier = Modifier.fillMaxSize()
                )
                DebugMode.TestImages -> TestImagesPanel(
                    navController = navController,
                    viewModel = viewModel,
                    uiState = uiState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
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
    viewModel: ScanViewModel,
    uiState: ScanUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
            framePipeline = viewModel.framePipelineInstance,
            onFrameValidated = { viewModel.setLatestFrame(it) },
            ocrPipeline = viewModel.ocrPipelineInstance,
            onOcrResult = { viewModel.setLatestOcr(it) },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(16.dp)
        ) {
            val ocrText = uiState.latestOcr?.text ?: ""
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
                    viewModel.ingestLiveCamera(context, navController)
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

enum class PreprocessingFilter {
    Raw, Contrast, Threshold
}

@Composable
private fun TestImagesPanel(
    navController: NavController,
    viewModel: ScanViewModel,
    uiState: ScanUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { TestLabelAssetRepository(context) }
    val imageNames = remember(repository) { repository.listImageNames() }
    var selectedFilter by remember { mutableStateOf(PreprocessingFilter.Raw) }

    LaunchedEffect(imageNames) {
        if (imageNames.isNotEmpty() && uiState.imageNames.isEmpty()) {
            viewModel.initializeTestImages(imageNames, repository)
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
                text = uiState.validationState.status,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF3D4946)
            )
            return@Column
        }

        // Debugging filters segmented buttons
        if (AppSettings.ocrDiagnostics) {
            Text(
                text = "Show Preprocessed Preview:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5D6E6A)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreprocessingFilter.values().forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val colors = if (isSelected) {
                        ButtonDefaults.buttonColors(containerColor = Color(0xFF116A5B))
                    } else {
                        ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF163832))
                    }
                    if (isSelected) {
                        Button(
                            onClick = { selectedFilter = filter },
                            colors = colors,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = when (filter) {
                                    PreprocessingFilter.Raw -> "Raw Label"
                                    PreprocessingFilter.Contrast -> "Contrast"
                                    PreprocessingFilter.Threshold -> "Threshold"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { selectedFilter = filter },
                            colors = colors,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = when (filter) {
                                    PreprocessingFilter.Raw -> "Raw Label"
                                    PreprocessingFilter.Contrast -> "Contrast"
                                    PreprocessingFilter.Threshold -> "Threshold"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        TestImagePreview(
            asset = uiState.validationState.asset,
            ocrResult = uiState.validationState.ocrResult,
            filter = selectedFilter
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    viewModel.selectPreviousTestImage(repository)
                },
                modifier = Modifier.width(110.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF163832))
            ) {
                Text(text = "Previous")
            }
            Text(
                text = "${uiState.selectedIndex + 1} / ${imageNames.size}",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF3D4946),
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    viewModel.selectNextTestImage(repository)
                },
                modifier = Modifier.width(110.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF116A5B))
            ) {
                Text(text = "Next")
            }
        }

        val ocrResult = uiState.validationState.ocrResult
        val ocrText = ocrResult?.text ?: ""

        Button(
            onClick = {
                viewModel.ingestTestImage(context, navController)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = ocrText.isNotBlank() && !uiState.isIngesting,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF116A5B),
                disabledContainerColor = Color(0xFF2C4C46)
            )
        ) {
            if (uiState.isIngesting) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text("Ingest Test Image")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (AppSettings.ocrDiagnostics) {
            com.example.ui.components.CameraMetadataPanel(
                title = "Test Image Metadata",
                fileName = uiState.validationState.asset?.fileName ?: imageNames[uiState.selectedIndex],
                frameResult = uiState.validationState.frameResult,
                ocrResult = uiState.validationState.ocrResult,
                status = uiState.validationState.status,
                errorMessage = uiState.validationState.errorMessage
            )
            Spacer(modifier = Modifier.height(4.dp))
            com.example.ui.components.CameraOcrOutputPanel(uiState.validationState.ocrResult)
        }
    }
}

@Composable
private fun TestImagePreview(
    asset: LoadedBitmapAsset?,
    ocrResult: OcrResult?,
    filter: PreprocessingFilter
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
            val preprocessedBitmap = remember(asset.bitmap, filter) {
                when (filter) {
                    PreprocessingFilter.Raw -> asset.bitmap
                    PreprocessingFilter.Contrast -> {
                        OcrPreprocessor.toGrayscale(asset.bitmap)
                            .let { OcrPreprocessor.normalizeBrightness(it) }
                            .let { OcrPreprocessor.applyClahe(it) }
                            .let { OcrPreprocessor.applySharpen(it) }
                    }
                    PreprocessingFilter.Threshold -> {
                        OcrPreprocessor.toGrayscale(asset.bitmap)
                            .let { OcrPreprocessor.applyAdaptiveThreshold(it) }
                    }
                }
            }
            val imageBitmap = remember(preprocessedBitmap) { preprocessedBitmap.asImageBitmap() }

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

                        // 4. Draw tile boundaries (dashed orange stroke) if tiled OCR was used
                        ocrResult.tileRegions.forEach { rect ->
                            val left = offsetX + rect.left * scale
                            val top = offsetY + rect.top * scale
                            val right = offsetX + rect.right * scale
                            val bottom = offsetY + rect.bottom * scale

                            drawRect(
                                color = Color(0xFFE67E22),
                                topLeft = Offset(left, top),
                                size = Size(right - left, bottom - top),
                                style = Stroke(
                                    width = 1.5f.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(15f, 10f), 0f
                                    )
                                )
                            )
                        }
                    }
                }
            }

            if (AppSettings.ocrDiagnostics && ocrResult != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE67E22),
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = ocrResult.routedStrategy,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF116A5B),
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = ocrResult.complexityRating,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Blur: %.1f | Luma: %.1f | Contrast: %.1f".format(
                                ocrResult.blurScore,
                                ocrResult.brightnessScore,
                                ocrResult.contrastScore
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
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
