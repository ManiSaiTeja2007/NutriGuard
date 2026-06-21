package com.example.core.ocr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.core.frame.FrameAnalysisResult
import com.example.core.frame.FramePipeline
import com.example.core.imaging.ImageFrame
import com.example.core.pipeline.OCRPipeline
import com.example.platform.health.AppHealthMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OcrCameraFrameAnalyzer(
    private val framePipeline: FramePipeline,
    private val ocrPipeline: OCRPipeline,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val onOcrResult: (OcrResult) -> Unit,
    private val onFrameValidated: (FrameAnalysisResult) -> Unit = {}
) : ImageAnalysis.Analyzer {

    private val isOcrRunning = AtomicBoolean(false)
    // Track consecutive failures for health monitoring (ISSUE-005 / BLACK-002)
    private val consecutiveFailures = AtomicInteger(0)
    private val consecutiveFailureThreshold = 5

    override fun analyze(imageProxy: ImageProxy) {
        // CRASH-001 FIX: Guard against zero-dimension frames that arrive during
        // CameraX lifecycle transitions (orientation change, permission grant, screen off/on).
        // These would cause IllegalArgumentException in FramePipeline.require() calls.
        if (imageProxy.width <= 0 || imageProxy.height <= 0) {
            imageProxy.close()
            return
        }

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
                // Reset failure counter on successful OCR (ISSUE-005 / BLACK-002)
                consecutiveFailures.set(0)
                AppHealthMonitor.clearError()

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
                // ISSUE-005 / BLACK-002 FIX: Report persistent OCR failures to AppHealthMonitor
                // so the FallbackRecoveryScreen can activate if OCR is completely broken.
                val failures = consecutiveFailures.incrementAndGet()
                if (failures >= consecutiveFailureThreshold) {
                    AppHealthMonitor.reportError(error, "OCR failed $failures consecutive times. Last error: ${error.message}")
                }
            } finally {
                isOcrRunning.set(false)
                // Enforce CameraXFrame lifecycle safety by closing the imageProxy
                imageProxy.close()
            }
        }
    }
}
