package com.example

import com.example.camera.FramePipeline
import com.example.camera.FrameSource
import com.example.camera.TestBitmapFrameSource
import com.example.utils.BitmapAssetLoader
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageOneFramePipelineTest {

    @Test
    fun testLabelAssetsDecodeToBitmapsAndPassThroughFramePipeline() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val assetNames = context.assets.list(TEST_LABEL_DIR)
            ?.filter { it.endsWith(".jpg", ignoreCase = true) }
            .orEmpty()

        assertFalse("Expected test label images in androidTest assets.", assetNames.isEmpty())

        val pipeline = FramePipeline(throttleMs = 0L)
        var processedCount = 0

        assetNames.forEach { assetName ->
            val bitmap = BitmapAssetLoader.load(context, "$TEST_LABEL_DIR/$assetName")
            val frame = TestBitmapFrameSource.fromBitmap(bitmap)
            val result = pipeline.process(frame)

            assertNotNull("Expected $assetName to produce a frame result.", result)
            requireNotNull(result)
            assertEquals(bitmap.width, result.width)
            assertEquals(bitmap.height, result.height)
            assertEquals(0, result.rotationDegrees)
            assertEquals(FrameSource.TEST_ASSET, result.source)
            assertTrue(result.hasBitmap)
            assertTrue(result.processingLatencyMs >= 0L)

            processedCount += 1
            bitmap.recycle()
        }

        assertEquals(assetNames.size, processedCount)
    }

    @Test
    fun framePipelineThrottlesFramesBeforeSevenHundredMilliseconds() {
        var nowMs = 1_000L
        val pipeline = FramePipeline(throttleMs = 700L, clockMs = { nowMs })
        val context = InstrumentationRegistry.getInstrumentation().context
        val firstAsset = context.assets.list(TEST_LABEL_DIR)
            ?.first { it.endsWith(".jpg", ignoreCase = true) }
            ?: error("No test label image assets found.")
        val bitmap = BitmapAssetLoader.load(context, "$TEST_LABEL_DIR/$firstAsset")
        val frame = TestBitmapFrameSource.fromBitmap(bitmap)

        assertNotNull(pipeline.process(frame))

        nowMs += 699L
        assertNull(pipeline.process(frame))

        nowMs += 1L
        assertNotNull(pipeline.process(frame))

        bitmap.recycle()
    }

    private companion object {
        const val TEST_LABEL_DIR = "test_labels"
    }
}
