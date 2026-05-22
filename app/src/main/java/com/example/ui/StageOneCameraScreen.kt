package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.camera.FrameAnalysisResult
import com.example.camera.FramePipeline
import com.example.camera.TestBitmapFrameSource
import com.example.ocr.OcrProcessor
import com.example.ocr.OcrResult
import com.example.utils.LoadedBitmapAsset
import com.example.utils.TestLabelAssetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NutriGuardDebugScreen(
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(DebugMode.TestImages) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7FAF9))
    ) {
        Header(mode = mode, onModeChanged = { mode = it })

        when (mode) {
            DebugMode.TestImages -> TestImageValidationPanel(
                modifier = Modifier.fillMaxSize()
            )

            DebugMode.LiveCamera -> LiveCameraOcrPanel(
                hasCameraPermission = hasCameraPermission,
                onRequestCameraPermission = onRequestCameraPermission,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun Header(
    mode: DebugMode,
    onModeChanged: (DebugMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "NutriGuard OCR Debug",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF163832)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeButton(
                text = "Test Images",
                selected = mode == DebugMode.TestImages,
                onClick = { onModeChanged(DebugMode.TestImages) }
            )
            ModeButton(
                text = "Live Camera",
                selected = mode == DebugMode.LiveCamera,
                onClick = { onModeChanged(DebugMode.LiveCamera) }
            )
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
private fun TestImageValidationPanel(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { TestLabelAssetRepository(context) }
    val imageNames = remember(repository) { repository.listImageNames() }
    val framePipeline = remember { FramePipeline(throttleMs = 0L) }
    var ocrProcessor by remember { mutableStateOf<OcrProcessor?>(null) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(AssetValidationState()) }

    DisposableEffect(Unit) {
        onDispose {
            ocrProcessor?.close()
        }
    }

    LaunchedEffect(imageNames, selectedIndex) {
        if (imageNames.isEmpty()) {
            state = AssetValidationState(status = "No test label assets found")
            return@LaunchedEffect
        }

        val fileName = imageNames[selectedIndex.coerceIn(imageNames.indices)]
        state = AssetValidationState(status = "Loading bitmap")

        state = try {
            val asset = withContext(Dispatchers.IO) {
                repository.load(fileName)
            }
            val frame = TestBitmapFrameSource.fromBitmap(
                bitmap = asset.bitmap,
                rotationDegrees = asset.rotationDegrees
            )
            val frameResult = requireNotNull(framePipeline.process(frame)) {
                "Frame pipeline throttled test asset unexpectedly."
            }
            val processor = ocrProcessor ?: OcrProcessor().also {
                ocrProcessor = it
            }
            val ocrResult = withContext(Dispatchers.Default) {
                processor.recognizeBitmap(frame, frameResult)
            }

            AssetValidationState(
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
            AssetValidationState(
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

        TestImagePreview(state.asset)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    selectedIndex = if (selectedIndex == 0) imageNames.lastIndex else selectedIndex - 1
                },
                modifier = Modifier.testTag("previous_test_image")
            ) {
                Text(text = "Previous")
            }
            Text(
                text = "${selectedIndex + 1} / ${imageNames.size}",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF3D4946)
            )
            Button(
                onClick = {
                    selectedIndex = if (selectedIndex == imageNames.lastIndex) 0 else selectedIndex + 1
                },
                modifier = Modifier.testTag("next_test_image")
            ) {
                Text(text = "Next")
            }
        }

        MetadataBlock(
            title = "Test Image",
            fileName = state.asset?.fileName ?: imageNames[selectedIndex],
            frameResult = state.frameResult,
            ocrResult = state.ocrResult,
            status = state.status,
            errorMessage = state.errorMessage
        )

        OcrOutputBlock(state.ocrResult)
    }
}

@Composable
private fun TestImagePreview(asset: LoadedBitmapAsset?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE8EFEC))
            .border(1.dp, Color(0xFFC8D4CF), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (asset == null) {
            Text(text = "Loading preview", color = Color(0xFF3D4946))
        } else {
            Image(
                bitmap = asset.bitmap.asImageBitmap(),
                contentDescription = asset.fileName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .graphicsLayer {
                        rotationZ = asset.rotationDegrees.toFloat()
                    },
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun LiveCameraOcrPanel(
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var latestFrame by remember { mutableStateOf<FrameAnalysisResult?>(null) }
    var latestOcr by remember { mutableStateOf<OcrResult?>(null) }
    val framePipeline = remember { FramePipeline() }
    val ocrProcessor = remember { OcrProcessor() }

    DisposableEffect(Unit) {
        onDispose {
            ocrProcessor.close()
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
            Button(onClick = onRequestCameraPermission) {
                Text(text = "Grant camera access")
            }
        }
        return
    }

    Box(modifier = modifier) {
        CameraPreview(
            framePipeline = framePipeline,
            onFrameValidated = { latestFrame = it },
            ocrProcessor = ocrProcessor,
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
            MetadataBlock(
                title = "Live Camera",
                fileName = "CameraX frame",
                frameResult = latestFrame,
                ocrResult = latestOcr,
                status = if (latestOcr == null) "Waiting for OCR frame" else "Live OCR complete",
                dark = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OcrOutputBlock(result = latestOcr, dark = true)
        }
    }
}

@Composable
private fun MetadataBlock(
    title: String,
    fileName: String,
    frameResult: FrameAnalysisResult?,
    ocrResult: OcrResult?,
    status: String,
    errorMessage: String? = null,
    dark: Boolean = false
) {
    val primary = if (dark) Color.White else Color(0xFF163832)
    val secondary = if (dark) Color(0xFFD9E4E0) else Color(0xFF3D4946)
    val resolution = frameResult?.let { "${it.width}x${it.height}" } ?: "Pending"
    val rotation = frameResult?.rotationDegrees?.toString() ?: "Pending"
    val pipelineLatency = frameResult?.processingLatencyMs?.let { "${it}ms" } ?: "Pending"
    val ocrLatency = ocrResult?.let {
        if (it.skippedReason == null) "${it.processingLatencyMs}ms" else "Skipped"
    } ?: "Pending"
    val confidence = ocrResult?.averageConfidence?.let { "%.2f".format(it) } ?: "Unavailable"
    val segments = ocrResult?.segmentsProcessed?.toString() ?: "Pending"

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = primary
        )
        DebugLine("Filename", fileName, primary, secondary)
        DebugLine("Resolution", resolution, primary, secondary)
        DebugLine("Rotation", rotation, primary, secondary)
        DebugLine("Pipeline Time", pipelineLatency, primary, secondary)
        DebugLine("OCR Time", ocrLatency, primary, secondary)
        DebugLine("OCR Segments", segments, primary, secondary)
        DebugLine("Confidence", confidence, primary, secondary)
        DebugLine(
            "Status",
            errorMessage ?: ocrResult?.skippedReason ?: status,
            primary,
            if (errorMessage == null && ocrResult?.skippedReason == null) secondary else Color(0xFFB3261E)
        )
    }
}

@Composable
private fun DebugLine(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            modifier = Modifier.size(width = 104.dp, height = 20.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OcrOutputBlock(
    result: OcrResult?,
    dark: Boolean = false
) {
    val background = if (dark) Color.Transparent else Color.White
    val border = if (dark) Color.White.copy(alpha = 0.18f) else Color(0xFFC8D4CF)
    val textColor = if (dark) Color.White else Color(0xFF163832)
    val bodyColor = if (dark) Color(0xFFEAF3F0) else Color(0xFF263D38)
    val text = result?.text?.ifBlank { "No text recognized" } ?: "OCR pending"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "OCR OUTPUT",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = bodyColor
        )
    }
}

private data class AssetValidationState(
    val asset: LoadedBitmapAsset? = null,
    val frameResult: FrameAnalysisResult? = null,
    val ocrResult: OcrResult? = null,
    val status: String = "Idle",
    val errorMessage: String? = null
)

private enum class DebugMode {
    TestImages,
    LiveCamera
}
