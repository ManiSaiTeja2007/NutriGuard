package com.example.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class CameraFrameAnalyzer(
    private val framePipeline: FramePipeline = FramePipeline(),
    private val onFrameValidated: (FrameAnalysisResult) -> Unit = {}
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val result = framePipeline.process(
                FrameInput(
                    width = imageProxy.width,
                    height = imageProxy.height,
                    rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                    timestampNanos = imageProxy.imageInfo.timestamp,
                    source = FrameSource.CAMERA_X
                )
            )

            if (result != null) {
                onFrameValidated(result)
            }
        } finally {
            imageProxy.close()
        }
    }
}
