package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.frame.FrameAnalysisResult
import com.example.core.ocr.OcrResult

@Composable
fun CameraMetadataPanel(
    title: String,
    fileName: String,
    frameResult: FrameAnalysisResult?,
    ocrResult: OcrResult?,
    status: String,
    errorMessage: String? = null,
    dark: Boolean = false,
    modifier: Modifier = Modifier
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

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
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
