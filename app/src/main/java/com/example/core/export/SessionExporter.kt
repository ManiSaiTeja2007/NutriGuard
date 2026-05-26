package com.example.core.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.example.core.pipeline.PipelineResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SessionExporter(
    private val writer: ExportFileWriter
) {

    /**
     * Exports a self-contained, reproducible pipeline execution snapshot to the file system.
     * Returns the absolute path of the generated folder, or null if failed.
     */
    fun export(snapshot: PipelineSnapshot): String? {
        val executionId = snapshot.executionId
        val dir = writer.getExportDir(executionId) ?: return null

        val fileHashes = mutableMapOf<String, String>()

        // 1. Copy Raw Image
        snapshot.rawImagePath?.let { rawPath ->
            val rawFile = writer.copyFile(dir, "raw", rawPath, "raw_image.png")
            if (rawFile != null) {
                fileHashes["raw/raw_image.png"] = writer.calculateSha256(rawFile)
            }
        }

        // 2. Copy Preprocessed Image
        snapshot.preprocessedImagePath?.let { prepPath ->
            val prepFile = writer.copyFile(dir, "preprocessed", prepPath, "preprocessed_image.png")
            if (prepFile != null) {
                fileHashes["preprocessed/preprocessed_image.png"] = writer.calculateSha256(prepFile)
            }
        }

        // 3. Render and Save Overlays
        val overlayRenderFile = renderOverlayToBitmap(snapshot.preprocessedImagePath, snapshot.result.ocrLines)?.let { bitmap ->
            writer.writeBitmapFile(dir, "overlays", "overlay_render.png", bitmap)
        }
        if (overlayRenderFile != null) {
            fileHashes["overlays/overlay_render.png"] = writer.calculateSha256(overlayRenderFile)
        }

        val overlayBoundsJson = buildOverlayBoundsJson(snapshot.result)
        val overlayBoundsFile = writer.writeTextFile(dir, "overlays", "overlay_bounds.json", overlayBoundsJson)
        if (overlayBoundsFile != null) {
            fileHashes["overlays/overlay_bounds.json"] = writer.calculateSha256(overlayBoundsFile)
        }

        // 4. Replay Trace (with full stage trace maps and normalization trace)
        val replayJson = buildReplayJson(snapshot.result)
        val replayFile = writer.writeTextFile(dir, "replay", "replay_trace.json", replayJson)
        if (replayFile != null) {
            fileHashes["replay/replay_trace.json"] = writer.calculateSha256(replayFile)
        }

        // 5. Semantic Interpretations
        val semanticJson = buildSemanticJson(snapshot.result)
        val semanticFile = writer.writeTextFile(dir, "semantic", "semantic_interpretation.json", semanticJson)
        if (semanticFile != null) {
            fileHashes["semantic/semantic_interpretation.json"] = writer.calculateSha256(semanticFile)
        }

        // 6. Metrics (per-stage breakdown)
        val metricsJson = buildMetricsJson(snapshot.result)
        val metricsFile = writer.writeTextFile(dir, "metrics", "metrics.json", metricsJson)
        if (metricsFile != null) {
            fileHashes["metrics/metrics.json"] = writer.calculateSha256(metricsFile)
        }

        // 7. Metadata (versioned references)
        val metadataObj = SnapshotMetadata(
            pipelineVersion = "1.0.0",
            ontologyVersion = "1.0.0",
            preprocessingVersion = "1.0.0",
            executionId = executionId,
            timestamp = System.currentTimeMillis()
        )
        val metadataJson = buildMetadataJson(metadataObj)
        val metadataFile = writer.writeTextFile(dir, "metadata", "metadata.json", metadataJson)
        if (metadataFile != null) {
            fileHashes["metadata/metadata.json"] = writer.calculateSha256(metadataFile)
        }

        // 8. Generate manifest.json (read-only verification index)
        val manifestObj = ExportManifest(
            executionId = executionId,
            timestamp = metadataObj.timestamp,
            fileHashes = fileHashes,
            metadata = metadataObj
        )
        val manifestJson = buildManifestJson(manifestObj)
        writer.writeTextFile(dir, "", "manifest.json", manifestJson)

        return dir.absolutePath
    }

    private fun renderOverlayToBitmap(preprocessedImagePath: String?, ocrLines: List<com.example.core.ocr.OCRLine>): Bitmap? {
        val path = preprocessedImagePath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        val original = BitmapFactory.decodeFile(path) ?: return null
        val mutableBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        ocrLines.forEach { line ->
            val rect = Rect(line.bounds.left, line.bounds.top, line.bounds.right, line.bounds.bottom)
            canvas.drawRect(rect, paint)
        }
        return mutableBitmap
    }

    private fun buildOverlayBoundsJson(result: PipelineResult): String {
        val root = JSONObject()
        val blocksArr = JSONArray()
        result.ocrBlocks.forEach { block ->
            blocksArr.put(JSONObject().apply {
                val blockText = block.lines.joinToString(" ") { line -> line.words.joinToString(" ") { it.text } }
                put("text", blockText)
                put("left", block.bounds.left)
                put("top", block.bounds.top)
                put("right", block.bounds.right)
                put("bottom", block.bounds.bottom)
            })
        }
        root.put("blocks", blocksArr)

        val linesArr = JSONArray()
        result.ocrLines.forEach { line ->
            linesArr.put(JSONObject().apply {
                put("left", line.bounds.left)
                put("top", line.bounds.top)
                put("right", line.bounds.right)
                put("bottom", line.bounds.bottom)
                val wordsArr = JSONArray()
                line.words.forEach { wordsArr.put(it.text) }
                put("words", wordsArr)
            })
        }
        root.put("lines", linesArr)
        return root.toString(2)
    }

    private fun buildReplayJson(result: PipelineResult): String {
        val arr = JSONArray()
        result.replayTrace.forEach { trace ->
            arr.put(JSONObject().apply {
                put("stageName", trace.stageName)
                put("input", trace.input)
                put("output", trace.output)
                put("latencyMs", trace.latencyMs)
            })
        }
        return arr.toString(2)
    }

    private fun buildSemanticJson(result: PipelineResult): String {
        val arr = JSONArray()
        result.interpretedIngredients.forEach { ing ->
            arr.put(JSONObject().apply {
                put("ingredient", ing.canonicalName)
                put("category", ing.category.name)
                put("confidenceBand", ing.confidenceBand.name)
                put("additiveCode", ing.additiveCode ?: "")
                put("explanation", ing.explanation ?: "")
                val warningsArr = JSONArray()
                ing.warnings.forEach { warningsArr.put(it) }
                put("warnings", warningsArr)
            })
        }
        return arr.toString(2)
    }

    private fun buildMetricsJson(result: PipelineResult): String {
        return JSONObject().apply {
            put("ocrMs", result.metrics.ocrLatencyMs)
            put("normalizationMs", result.metrics.normalizationLatencyMs)
            put("extractionMs", result.metrics.extractionLatencyMs)
            put("groupingMs", result.metrics.groupingLatencyMs)
            put("phraseCorrectionMs", result.metrics.phraseCorrectionLatencyMs)
            put("correctionMs", result.metrics.correctionLatencyMs)
            put("totalMs", result.metrics.totalLatencyMs)
        }.toString(2)
    }

    private fun buildMetadataJson(metadata: SnapshotMetadata): String {
        return JSONObject().apply {
            put("executionId", metadata.executionId)
            put("pipelineVersion", metadata.pipelineVersion)
            put("ontologyVersion", metadata.ontologyVersion)
            put("preprocessingVersion", metadata.preprocessingVersion)
            put("timestamp", metadata.timestamp)
        }.toString(2)
    }

    private fun buildManifestJson(manifest: ExportManifest): String {
        val root = JSONObject()
        root.put("executionId", manifest.executionId)
        root.put("schemaVersion", manifest.schemaVersion)
        root.put("timestamp", manifest.timestamp)

        val hashesObj = JSONObject()
        manifest.fileHashes.forEach { (k, v) -> hashesObj.put(k, v) }
        root.put("fileHashes", hashesObj)

        val metaObj = JSONObject().apply {
            put("executionId", manifest.metadata.executionId)
            put("pipelineVersion", manifest.metadata.pipelineVersion)
            put("ontologyVersion", manifest.metadata.ontologyVersion)
            put("preprocessingVersion", manifest.metadata.preprocessingVersion)
            put("timestamp", manifest.metadata.timestamp)
        }
        root.put("metadata", metaObj)

        return root.toString(2)
    }
}
