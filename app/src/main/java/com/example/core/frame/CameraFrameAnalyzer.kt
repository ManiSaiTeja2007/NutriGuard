package com.example.core.frame

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.core.imaging.ImageFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CameraFrameAnalyzer(
    private val framePipeline: FramePipeline = FramePipeline(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val onFrameValidated: (FrameAnalysisResult) -> Unit = {}
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        val frame = ImageFrame.CameraXFrame(imageProxy)
        scope.launch {
            try {
                val result = framePipeline(frame)
                if (result != null) {
                    onFrameValidated(result)
                }
            } finally {
                imageProxy.close()
            }
        }
    }
}
