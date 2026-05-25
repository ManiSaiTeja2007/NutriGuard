package com.example

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.core.frame.FramePipeline
import com.example.core.imaging.ImageFrame
import com.example.core.imaging.ImageSource
import com.example.core.pipeline.OCRPipeline
import com.example.core.ocr.OcrResult
import com.example.utils.BitmapAssetLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrHardeningTest {

    @Test
    fun testOcrDeterminism() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val assetNames = context.assets.list(TEST_LABEL_DIR)?.filter { it.endsWith(".jpg", ignoreCase = true) }.orEmpty()
        assertFalse("Expected test label images in androidTest assets.", assetNames.isEmpty())
        val assetName = assetNames.first()
        val asset = BitmapAssetLoader.loadWithMetadata(context, "$TEST_LABEL_DIR/$assetName")

        val pipeline = OCRPipeline()
        val framePipeline = FramePipeline(throttleMs = 0L)
        val frame = ImageFrame.BitmapFrame(
            bitmap = asset.bitmap,
            rotationDegrees = asset.rotationDegrees,
            timestampNanos = System.nanoTime(),
            source = ImageSource.TEST_ASSET
        )
        val frameResult = framePipeline(frame)!!

        val results = mutableListOf<OcrResult>()
        repeat(5) {
            results.add(pipeline(Pair(frame, frameResult)))
        }

        val firstText = results[0].text
        val firstConf = results[0].averageConfidence

        results.forEachIndexed { index, res ->
            assertEquals("Mismatch in text at run $index", firstText, res.text)
            assertEquals("Mismatch in confidence at run $index", firstConf, res.averageConfidence)
        }

        asset.bitmap.recycle()
        pipeline.close()
    }

    @Test
    fun testRotationValidation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val assetNames = context.assets.list(TEST_LABEL_DIR)?.filter { it.endsWith(".jpg", ignoreCase = true) }.orEmpty()
        assertFalse("Expected test label images in androidTest assets.", assetNames.isEmpty())
        val assetName = assetNames.first()
        val asset = BitmapAssetLoader.loadWithMetadata(context, "$TEST_LABEL_DIR/$assetName")

        val pipeline = OCRPipeline()
        val framePipeline = FramePipeline(throttleMs = 0L)

        val rotations = listOf(90, 180, 270)
        rotations.forEach { rotation ->
            val frame = ImageFrame.BitmapFrame(
                bitmap = asset.bitmap,
                rotationDegrees = rotation,
                timestampNanos = System.nanoTime(),
                source = ImageSource.TEST_ASSET
            )
            val frameResult = framePipeline(frame)!!
            assertEquals(rotation, frameResult.rotationDegrees)

            val ocrResult = pipeline(Pair(frame, frameResult))
            assertEquals(rotation, ocrResult.frame.rotationDegrees)
        }

        asset.bitmap.recycle()
        pipeline.close()
    }

    @Test
    fun testCorruptedImageGracefulFailure() = runBlocking {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.recycle() // Recycle to corrupt it

        val frame = ImageFrame.BitmapFrame(
            bitmap = bitmap,
            rotationDegrees = 0,
            timestampNanos = System.nanoTime(),
            source = ImageSource.TEST_ASSET
        )
        val framePipeline = FramePipeline(throttleMs = 0L)
        val frameResult = framePipeline(frame)!!
        val pipeline = OCRPipeline()

        var threwException = false
        try {
            pipeline(Pair(frame, frameResult))
        } catch (e: Throwable) {
            threwException = true
        }

        assertTrue("Expected exception to be thrown for recycled/corrupted bitmap", threwException)
        pipeline.close()
    }

    @Test
    fun testLowResolutionRejection() = runBlocking {
        val bitmap = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888)
        val frame = ImageFrame.BitmapFrame(
            bitmap = bitmap,
            rotationDegrees = 0,
            timestampNanos = System.nanoTime(),
            source = ImageSource.TEST_ASSET
        )
        val framePipeline = FramePipeline(throttleMs = 0L)
        val frameResult = framePipeline(frame)!!
        val pipeline = OCRPipeline()

        val ocrResult = pipeline(Pair(frame, frameResult))
        assertNotNull(ocrResult.skippedReason)
        assertEquals("Image is below ML Kit minimum size of 32x32", ocrResult.skippedReason)
        assertEquals("", ocrResult.text)
        assertEquals(0, ocrResult.segmentsProcessed)

        bitmap.recycle()
        pipeline.close()
    }

    @Test
    fun testMemoryStability() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val assetNames = context.assets.list(TEST_LABEL_DIR)?.filter { it.endsWith(".jpg", ignoreCase = true) }.orEmpty()
        assertFalse("Expected test label images in androidTest assets.", assetNames.isEmpty())

        val framePipeline = FramePipeline(throttleMs = 0L)
        val pipeline = OCRPipeline()

        // Loop over the dataset twice to check stability under repeated workloads
        repeat(2) {
            assetNames.forEach { assetName ->
                val asset = BitmapAssetLoader.loadWithMetadata(context, "$TEST_LABEL_DIR/$assetName")
                val frame = ImageFrame.BitmapFrame(
                    bitmap = asset.bitmap,
                    rotationDegrees = asset.rotationDegrees,
                    timestampNanos = System.nanoTime(),
                    source = ImageSource.TEST_ASSET
                )
                val frameResult = framePipeline(frame)!!

                val ocrResult = pipeline(Pair(frame, frameResult))
                assertTrue(ocrResult.segmentsProcessed > 0)

                asset.bitmap.recycle()
            }
            System.gc()
        }

        pipeline.close()
    }

    @Test
    fun testSegmentationConsistency() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val assetNames = context.assets.list(TEST_LABEL_DIR)?.filter { it.endsWith(".jpg", ignoreCase = true) }.orEmpty()
        assertFalse("Expected test label images in androidTest assets.", assetNames.isEmpty())
        val assetName = assetNames.first()
        val asset = BitmapAssetLoader.loadWithMetadata(context, "$TEST_LABEL_DIR/$assetName")

        val pipeline = OCRPipeline()
        val framePipeline = FramePipeline(throttleMs = 0L)

        // 1. Run full-image OCR on small copy (width = 800) which guarantees full-image processing (1 segment)
        val smallBitmap = Bitmap.createScaledBitmap(asset.bitmap, 800, (asset.bitmap.height * 800f / asset.bitmap.width).toInt(), true)
        val smallFrame = ImageFrame.BitmapFrame(
            bitmap = smallBitmap,
            rotationDegrees = asset.rotationDegrees,
            timestampNanos = System.nanoTime(),
            source = ImageSource.TEST_ASSET
        )
        val smallResult = framePipeline(smallFrame)!!
        val fullOcrResult = pipeline(Pair(smallFrame, smallResult))
        assertEquals(1, fullOcrResult.segmentsProcessed)

        // 2. Run segmented OCR on large scaled-up copy (width = 2100) which guarantees segmented processing (>1 segment)
        val largeBitmap = Bitmap.createScaledBitmap(asset.bitmap, 2100, (asset.bitmap.height * 2100f / asset.bitmap.width).toInt(), true)
        val largeFrame = ImageFrame.BitmapFrame(
            bitmap = largeBitmap,
            rotationDegrees = asset.rotationDegrees,
            timestampNanos = System.nanoTime(),
            source = ImageSource.TEST_ASSET
        )
        val largeResult = framePipeline(largeFrame)!!
        val segmentedOcrResult = pipeline(Pair(largeFrame, largeResult))
        assertTrue(segmentedOcrResult.segmentsProcessed > 1)

        // 3. Compare text content: ensure significant word overlap
        val fullWords = fullOcrResult.text.lowercase().split(Regex("\\s+")).filter { it.length > 3 }.toSet()
        val segmentedWords = segmentedOcrResult.text.lowercase().split(Regex("\\s+")).filter { it.length > 3 }.toSet()

        if (fullWords.isNotEmpty()) {
            val commonWords = fullWords.intersect(segmentedWords)
            val matchRatio = commonWords.size.toFloat() / fullWords.size
            assertTrue(
                "Segmentation lost too much text. Overlap ratio: $matchRatio. Full words: $fullWords, Segmented words: $segmentedWords",
                matchRatio >= 0.4f
            )
        }

        smallBitmap.recycle()
        largeBitmap.recycle()
        asset.bitmap.recycle()
        pipeline.close()
    }

    private companion object {
        const val TEST_LABEL_DIR = "test_labels"
    }
}
