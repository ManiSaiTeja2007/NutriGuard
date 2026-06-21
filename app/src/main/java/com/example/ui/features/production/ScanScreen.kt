package com.example.ui.features.production

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.core.frame.FrameAnalysisResult
import com.example.core.ocr.OcrResult
import com.example.core.ocr.preprocessing.OcrPreprocessor
import com.example.core.ocr.debug.OverlayCoordinateMapper
import com.example.data.AppSettings
import com.example.core.config.FeatureFlags
import com.example.ui.CameraPreview
import com.example.ui.navigation.NavController
import com.example.ui.navigation.Screen
import com.example.utils.LoadedBitmapAsset
import com.example.utils.TestLabelAssetRepository
import com.example.platform.health.AppHealthMonitor
import com.example.ui.design.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    navController: NavController,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel()
) {
    // ISSUE-005 FIX: Move side effect out of composition body into LaunchedEffect
    // to prevent firing on every recomposition.
    LaunchedEffect(Unit) {
        AppHealthMonitor.trackScreenTransition("Scan")
    }
    val uiState by viewModel.uiState.collectAsState()

    NutriScreenScaffold(
        title = "Product Ingestion Scan",
        onOpenDrawer = onOpenDrawer,
        modifier = modifier.testTag("scan_screen")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            val modeToRender = if (FeatureFlags.enableTestImages) uiState.mode else DebugMode.LiveCamera
            
            if (FeatureFlags.enableTestImages) {
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

            when (modeToRender) {
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
private fun RowScope.ModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    } else {
        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
    }
    if (selected) {
        Button(
            onClick = onClick,
            colors = colors,
            modifier = Modifier.weight(1f).height(48.dp)
        ) {
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = colors,
            modifier = Modifier.weight(1f).height(48.dp)
        ) {
            Text(text = text, style = MaterialTheme.typography.labelMedium)
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
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRequestCameraPermission,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
            onOcrResult = { 
                viewModel.setLatestOcr(it) 
                AppHealthMonitor.trackOcrState(it.routedStrategy)
            },
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

            NutriPrimaryButton(
                text = "Ingest Scanned Text",
                onClick = {
                    viewModel.ingestLiveCamera(context, navController)
                },
                modifier = Modifier.fillMaxWidth().testTag("scan_ingest_button"),
                enabled = ocrText.isNotBlank()
            )
        }
    }
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
    
    val showOverlaysSupported = FeatureFlags.showOverlays
    var selectedFilter by remember { mutableStateOf(PreprocessingFilter.Raw) }

    var showRawBoxes by remember { mutableStateOf(true) }
    var showReconstructedLines by remember { mutableStateOf(true) }
    var showCandidates by remember { mutableStateOf(true) }
    var showHeatmap by remember { mutableStateOf(true) }
    var showTileBoundaries by remember { mutableStateOf(true) }
    var showIngredientRegions by remember { mutableStateOf(true) }

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
        if (showOverlaysSupported && AppSettings.showOverlays) {
            Text(
                text = "Show Preprocessed Preview:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreprocessingFilter.values().forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val colors = if (isSelected) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    } else {
                        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    }
                    if (isSelected) {
                        Button(
                            onClick = { selectedFilter = filter },
                            colors = colors,
                            modifier = Modifier.weight(1f).height(48.dp)
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
                            modifier = Modifier.weight(1f).height(48.dp)
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
            filter = selectedFilter,
            showRawBoxes = showRawBoxes && showOverlaysSupported && AppSettings.showOverlays,
            showTileBoundaries = showTileBoundaries && showOverlaysSupported && AppSettings.showOverlays,
            showReconstructedLines = showReconstructedLines && showOverlaysSupported && AppSettings.showOverlays,
            showIngredientRegions = showIngredientRegions && showOverlaysSupported && AppSettings.showOverlays,
            showCandidates = showCandidates && showOverlaysSupported && AppSettings.showOverlays,
            showHeatmap = showHeatmap && showOverlaysSupported && AppSettings.showOverlays
        )

        if (showOverlaysSupported && AppSettings.showOverlays && uiState.validationState.ocrResult != null) {
            Text(
                text = "Active Overlays:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NutriCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), NutriShapes.button)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = showRawBoxes,
                                onCheckedChange = { showRawBoxes = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Raw Boxes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = showReconstructedLines,
                                onCheckedChange = { showReconstructedLines = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lines", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = showCandidates,
                                onCheckedChange = { showCandidates = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Candidates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = showHeatmap,
                                onCheckedChange = { showHeatmap = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Heatmap", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = showTileBoundaries,
                                onCheckedChange = { showTileBoundaries = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tiles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = showIngredientRegions,
                                onCheckedChange = { showIngredientRegions = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paragraphs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    viewModel.selectPreviousTestImage(repository)
                },
                modifier = Modifier.size(width = 110.dp, height = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(text = "Previous")
            }
            Text(
                text = "${uiState.selectedIndex + 1} / ${imageNames.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    viewModel.selectNextTestImage(repository)
                },
                modifier = Modifier.size(width = 110.dp, height = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("scan_ingest_button"),
            enabled = ocrText.isNotBlank() && !uiState.isIngesting,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        ) {
            if (uiState.isIngesting) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            } else {
                Text("Ingest Test Image")
            }
        }

        // Error label — only shown on ingestion failure. Tagged for instrumentation observability.
        uiState.errorMsg?.let { errorText ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scan_ingest_error_label")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showOverlaysSupported && AppSettings.showOverlays) {
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
    filter: PreprocessingFilter,
    showRawBoxes: Boolean,
    showTileBoundaries: Boolean,
    showReconstructedLines: Boolean,
    showIngredientRegions: Boolean,
    showCandidates: Boolean,
    showHeatmap: Boolean
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        val maxW = maxWidth
        val maxH = maxHeight
        if (asset == null) {
            Text(text = "Loading preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            var zoomScale by remember { mutableStateOf(1f) }
            var panOffset by remember { mutableStateOf(Offset.Zero) }

            val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
                zoomScale = (zoomScale * zoomChange).coerceIn(1f, 5f)
                panOffset += panChange
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (zoomScale > 1f) {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    zoomScale = 2.5f
                                    panOffset = Offset.Zero
                                }
                            }
                        )
                    }
                    .transformable(state = transformState)
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = asset.fileName,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .graphicsLayer {
                            rotationZ = asset.rotationDegrees.toFloat()
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        },
                    contentScale = ContentScale.Fit
                )

                if (ocrResult != null) {
                    val density = LocalDensity.current
                    val paddingPx = with(density) { 8.dp.toPx() }

                    val containerW = maxW.value * density.density - (paddingPx * 2)
                    val containerH = maxH.value * density.density - (paddingPx * 2)

                    val bitmapW = asset.bitmap.width.toFloat()
                    val bitmapH = asset.bitmap.height.toFloat()

                    if (containerW > 0 && containerH > 0 && bitmapW > 0 && bitmapH > 0) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            // 1. Draw raw OCR boxes (yellow stroke)
                            if (showRawBoxes) {
                                ocrResult.ocrWords.forEach { word ->
                                    val mapped = OverlayCoordinateMapper.mapRect(
                                        rawRect = word.bounds,
                                        srcWidth = bitmapW,
                                        srcHeight = bitmapH,
                                        containerWidth = containerW,
                                        containerHeight = containerH,
                                        zoomScale = zoomScale,
                                        panX = panOffset.x,
                                        panY = panOffset.y
                                    )
                                    drawRect(
                                        color = Color(0xFFF39C12).copy(alpha = 0.6f),
                                        topLeft = Offset(mapped.left, mapped.top),
                                        size = Size(mapped.width, mapped.height),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                }
                            }

                            // 2. Draw detected ingredient paragraphs (green fill and stroke)
                            if (showIngredientRegions) {
                                ocrResult.detectedParagraphs.forEach { line ->
                                    val mapped = OverlayCoordinateMapper.mapRect(
                                        rawRect = line.bounds,
                                        srcWidth = bitmapW,
                                        srcHeight = bitmapH,
                                        containerWidth = containerW,
                                        containerHeight = containerH,
                                        zoomScale = zoomScale,
                                        panX = panOffset.x,
                                        panY = panOffset.y
                                    )
                                    drawRect(
                                        color = Color(0xFF2ECC71).copy(alpha = 0.15f),
                                        topLeft = Offset(mapped.left, mapped.top),
                                        size = Size(mapped.width, mapped.height)
                                    )
                                    drawRect(
                                        color = Color(0xFF27AE60).copy(alpha = 0.8f),
                                        topLeft = Offset(mapped.left, mapped.top),
                                        size = Size(mapped.width, mapped.height),
                                        style = Stroke(width = 1.5f.dp.toPx())
                                    )
                                }
                            }

                            // 3. Draw reading order indexes and reconstructed lines (blue dashed stroke)
                            if (showReconstructedLines) {
                                ocrResult.reconstructedLines.forEachIndexed { index, line ->
                                    val mapped = OverlayCoordinateMapper.mapRect(
                                        rawRect = line.bounds,
                                        srcWidth = bitmapW,
                                        srcHeight = bitmapH,
                                        containerWidth = containerW,
                                        containerHeight = containerH,
                                        zoomScale = zoomScale,
                                        panX = panOffset.x,
                                        panY = panOffset.y
                                    )
                                    drawRect(
                                        color = Color(0xFF3498DB).copy(alpha = 0.5f),
                                        topLeft = Offset(mapped.left, mapped.top),
                                        size = Size(mapped.width, mapped.height),
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
                                        center = Offset(mapped.left, mapped.top + (mapped.height / 2f))
                                    )

                                    drawContext.canvas.nativeCanvas.drawText(
                                        (index + 1).toString(),
                                        mapped.left - 3.dp.toPx(),
                                        mapped.top + (mapped.height / 2f) + 3.dp.toPx(),
                                        android.graphics.Paint().apply {
                                            color = android.graphics.Color.WHITE
                                            textSize = 10.dp.toPx()
                                            isFakeBoldText = true
                                        }
                                    )
                                }
                            }

                            // 4. Draw tile boundaries (dashed orange stroke) if tiled OCR was used
                            if (showTileBoundaries) {
                                ocrResult.tileRegions.forEach { rect ->
                                    val mapped = OverlayCoordinateMapper.mapRect(
                                        rawRect = rect,
                                        srcWidth = bitmapW,
                                        srcHeight = bitmapH,
                                        containerWidth = containerW,
                                        containerHeight = containerH,
                                        zoomScale = zoomScale,
                                        panX = panOffset.x,
                                        panY = panOffset.y
                                    )
                                    drawRect(
                                        color = Color(0xFFE67E22),
                                        topLeft = Offset(mapped.left, mapped.top),
                                        size = Size(mapped.width, mapped.height),
                                        style = Stroke(
                                            width = 1.5f.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(
                                                floatArrayOf(15f, 10f), 0f
                                            )
                                        )
                                    )
                                }
                            }

                            // 5. Draw candidate text (green overlay badges)
                            if (showCandidates) {
                                ocrResult.reconstructedLines.forEach { line ->
                                    val mapped = OverlayCoordinateMapper.mapRect(
                                        rawRect = line.bounds,
                                        srcWidth = bitmapW,
                                        srcHeight = bitmapH,
                                        containerWidth = containerW,
                                        containerHeight = containerH,
                                        zoomScale = zoomScale,
                                        panX = panOffset.x,
                                        panY = panOffset.y
                                    )
                                    val lineText = line.words.joinToString(" ") { it.text }
                                    if (lineText.isNotBlank()) {
                                        val paint = android.graphics.Paint().apply {
                                            color = android.graphics.Color.WHITE
                                            textSize = 10.dp.toPx()
                                            isFakeBoldText = true
                                        }
                                        val textWidth = paint.measureText(lineText)
                                        drawRect(
                                            color = Color.White.copy(alpha = 0.85f),
                                            topLeft = Offset(mapped.left, mapped.top - 14.dp.toPx()),
                                            size = Size(textWidth + 6.dp.toPx(), 14.dp.toPx())
                                        )
                                        drawContext.canvas.nativeCanvas.drawText(
                                            lineText,
                                            mapped.left + 3.dp.toPx(),
                                            mapped.top - 3.dp.toPx(),
                                            paint.apply {
                                                color = android.graphics.Color.parseColor("#116A5B")
                                            }
                                        )
                                    }
                                }
                            }

                            // 6. Draw heatmap (OCR confidence overlay)
                            if (showHeatmap) {
                                ocrResult.ocrWords.forEach { word ->
                                    val mapped = OverlayCoordinateMapper.mapRect(
                                        rawRect = word.bounds,
                                        srcWidth = bitmapW,
                                        srcHeight = bitmapH,
                                        containerWidth = containerW,
                                        containerHeight = containerH,
                                        zoomScale = zoomScale,
                                        panX = panOffset.x,
                                        panY = panOffset.y
                                    )
                                    val confidence = word.confidence
                                    val color = when {
                                        confidence < 0.70f -> Color(0xFFE74C3C).copy(alpha = 0.35f) // Red
                                        confidence < 0.85f -> Color(0xFFF1C40F).copy(alpha = 0.35f) // Yellow
                                        else -> Color(0xFF2ECC71).copy(alpha = 0.25f) // Green
                                    }
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(mapped.left, mapped.top),
                                        size = Size(mapped.width, mapped.height)
                                    )
                                }
                            }
                        }
                    }
                }

                if (FeatureFlags.showOverlays && AppSettings.showOverlays && ocrResult != null) {
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
                                color = MaterialTheme.colorScheme.primary,
                                tonalElevation = 2.dp
                            ) {
                                Text(
                                    text = ocrResult.complexityRating,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
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

                if (zoomScale != 1f || panOffset != Offset.Zero) {
                    Button(
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                    ) {
                        Text("Reset View", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

enum class PreprocessingFilter {
    Raw, Contrast, Threshold
}
