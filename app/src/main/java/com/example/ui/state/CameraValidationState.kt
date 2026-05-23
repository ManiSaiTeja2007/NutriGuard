package com.example.ui.state

import com.example.core.frame.FrameAnalysisResult
import com.example.core.ocr.OcrResult
import com.example.utils.LoadedBitmapAsset

data class CameraValidationState(
    val asset: LoadedBitmapAsset? = null,
    val frameResult: FrameAnalysisResult? = null,
    val ocrResult: OcrResult? = null,
    val status: String = "Idle",
    val errorMessage: String? = null
)

enum class DebugMode {
    TestImages,
    LiveCamera
}
