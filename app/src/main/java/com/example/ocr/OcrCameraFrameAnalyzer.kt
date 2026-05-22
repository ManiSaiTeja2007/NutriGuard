package com.example.ocr

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.camera.FrameInput
import com.example.camera.FramePipeline
import com.example.camera.FrameSource
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

class OcrCameraFrameAnalyzer(
    private val framePipeline: FramePipeline,
    private val ocrProcessor: OcrProcessor,
    private val onOcrResult: (OcrResult) -> Unit,
    private val onFrameValidated: (com.example.camera.FrameAnalysisResult) -> Unit = {}
) : ImageAnalysis.Analyzer {

    private val isOcrRunning = AtomicBoolean(false)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isOcrRunning.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val frame = FrameInput(
            width = imageProxy.width,
            height = imageProxy.height,
            rotationDegrees = imageProxy.imageInfo.rotationDegrees,
            timestampNanos = imageProxy.imageInfo.timestamp,
            source = FrameSource.CAMERA_X
        )

        val frameResult = try {
            framePipeline.process(frame)
        } catch (error: Throwable) {
            isOcrRunning.set(false)
            imageProxy.close()
            throw error
        }

        if (frameResult == null) {
            isOcrRunning.set(false)
            imageProxy.close()
            return
        }

        onFrameValidated(frameResult)

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isOcrRunning.set(false)
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        ocrProcessor.recognizeAsync(
            image = inputImage,
            frameResult = frameResult,
            onResult = onOcrResult,
            onError = {},
            onComplete = {
                isOcrRunning.set(false)
                imageProxy.close()
            }
        )
    }
}
