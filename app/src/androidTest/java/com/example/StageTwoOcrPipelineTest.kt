package com.example

import androidx.test.platform.app.InstrumentationRegistry
import android.util.Log
import com.example.core.frame.FramePipeline
import com.example.core.imaging.ImageFrame
import com.example.core.imaging.ImageSource
import com.example.core.ocr.OcrPipeline
import com.example.utils.BitmapAssetLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageTwoOcrPipelineTest {

    @Test
    fun ocrRunsAcrossAllDatasetImagesWithoutCrashes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val assetNames = context.assets.list(TEST_LABEL_DIR)
            ?.filter { it.endsWith(".jpg", ignoreCase = true) }
            .orEmpty()

        assertFalse("Expected test label images in androidTest assets.", assetNames.isEmpty())

        val framePipeline = FramePipeline(throttleMs = 0L)
        val ocrPipeline = OcrPipeline()
        var nonEmptyOcrCount = 0
        var ocrExecutedCount = 0
        var segmentedCount = 0
        val latencies = mutableListOf<Long>()

        try {
            assetNames.forEach { assetName ->
                val asset = BitmapAssetLoader.loadWithMetadata(context, "$TEST_LABEL_DIR/$assetName")
                val frame = ImageFrame.BitmapFrame(
                    bitmap = asset.bitmap,
                    rotationDegrees = asset.rotationDegrees,
                    timestampNanos = System.nanoTime(),
                    source = ImageSource.TEST_ASSET
                )
                val frameResult = framePipeline(frame)

                assertNotNull("Expected $assetName to produce a frame result.", frameResult)
                requireNotNull(frameResult)
                assertEquals(ImageSource.TEST_ASSET, frameResult.source)

                val ocrResult = ocrPipeline(Pair(frame, frameResult))
                assertEquals(frameResult, ocrResult.frame)

                assertTrue("OCR should not skip bitmap assets after segment padding.", ocrResult.skippedReason == null)
                assertTrue("Expected at least one OCR segment for $assetName.", ocrResult.segmentsProcessed > 0)
                ocrExecutedCount += 1
                if (ocrResult.segmentsProcessed > 1) {
                    segmentedCount += 1
                }
                latencies += ocrResult.processingLatencyMs

                if (ocrResult.text.isNotBlank()) {
                    nonEmptyOcrCount += 1
                }
                asset.bitmap.recycle()
            }
        } finally {
            ocrPipeline.close()
        }

        assertTrue("Expected OCR to execute on eligible dataset images.", ocrExecutedCount > 0)
        assertTrue("Expected at least one narrow label to use segmented OCR.", segmentedCount > 0)
        assertTrue("Expected at least one readable label to return raw OCR text.", nonEmptyOcrCount > 0)
        assertTrue("Expected OCR latency samples.", latencies.isNotEmpty())

        val sortedLatencies = latencies.sorted()
        val p95Latency = sortedLatencies[((sortedLatencies.size - 1) * 0.95).toInt()]
        val averageLatency = latencies.average()

        Log.i(
            "NutriGuardOcrTest",
            "OCR metrics images=${assetNames.size} executed=$ocrExecutedCount " +
                "segmented=$segmentedCount nonEmpty=$nonEmptyOcrCount " +
                "avgMs=${averageLatency.toInt()} p95Ms=$p95Latency maxMs=${sortedLatencies.last()}"
        )

        assertTrue("Expected average OCR latency to stay reasonable.", averageLatency <= 5_000.0)
        assertTrue("Expected p95 OCR latency to stay reasonable.", p95Latency <= 10_000L)
    }

    private companion object {
        const val TEST_LABEL_DIR = "test_labels"
    }
}
