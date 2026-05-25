package com.example.core.ocr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.core.frame.FrameAnalysisResult
import com.example.core.frame.FramePipeline
import com.example.core.imaging.ImageFrame
import com.example.core.pipeline.OCRPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class OcrCameraFrameAnalyzer(
    private val framePipeline: FramePipeline,
    private val ocrPipeline: OCRPipeline,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val onOcrResult: (OcrResult) -> Unit,
    private val onFrameValidated: (FrameAnalysisResult) -> Unit = {}
) : ImageAnalysis.Analyzer {

    private val isOcrRunning = AtomicBoolean(false)

    override fun analyze(imageProxy: ImageProxy) {
        if (!isOcrRunning.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val frame = ImageFrame.CameraXFrame(imageProxy)

        scope.launch {
            try {
                val frameResult = framePipeline(frame)
                if (frameResult == null) {
                    isOcrRunning.set(false)
                    imageProxy.close()
                    return@launch
                }

                onFrameValidated(frameResult)

                val ocrResult = ocrPipeline(Pair(frame, frameResult))
                onOcrResult(ocrResult)
            } catch (error: Throwable) {
                // Eliminate silent failures by logging the error
                OcrInstrumentation.logFailure(frame.source, FrameAnalysisResult(
                    width = frame.width,
                    height = frame.height,
                    rotationDegrees = frame.rotationDegrees,
                    timestampNanos = frame.timestampNanos,
                    source = frame.source,
                    hasBitmap = false,
                    processingLatencyMs = 0L
                ), error)
            } finally {
                isOcrRunning.set(false)
                // Enforce CameraXFrame lifecycle safety by closing the imageProxy
                imageProxy.close()
            }
        }
    }
}
