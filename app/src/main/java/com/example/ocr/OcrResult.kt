package com.example.ocr

import com.example.camera.FrameAnalysisResult
import com.example.camera.FrameSource

data class OcrResult(
    val text: String,
    val processingLatencyMs: Long,
    val averageConfidence: Float?,
    val textBlockCount: Int,
    val lineCount: Int,
    val elementCount: Int,
    val source: FrameSource,
    val frame: FrameAnalysisResult,
    val segmentsProcessed: Int = 1,
    val skippedReason: String? = null
)
