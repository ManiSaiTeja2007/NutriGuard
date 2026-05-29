package com.example.ui.features.production

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.frame.FrameAnalysisResult
import com.example.core.frame.FramePipeline
import com.example.core.imaging.ImageFrame
import com.example.core.imaging.ImageSource
import com.example.core.pipeline.OCRPipeline
import com.example.core.pipeline.SemanticPipeline
import com.example.core.pipeline.PipelineResult
import com.example.core.pipeline.PipelineConfig
import com.example.core.pipeline.PipelineMode
import com.example.core.ocr.OcrResult
import com.example.core.ingredient.*
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.intelligence.correction.FailureType
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.intelligence.IngredientInterpreter
import com.example.core.intelligence.explanation.ExplanationType
import com.example.core.intelligence.confidence.DatasetProvenance
import com.example.core.replay.ReplayStorageHelper

import com.example.data.AppSettings
import com.example.core.config.FeatureFlags
import com.example.ui.navigation.NavController
import com.example.ui.navigation.Screen
import com.example.ui.state.CameraValidationState
import com.example.utils.TestLabelAssetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class DebugMode {
    LiveCamera, TestImages
}

data class ScanUiState(
    val mode: DebugMode = DebugMode.LiveCamera,
    
    // Live Camera state
    val latestFrame: FrameAnalysisResult? = null,
    val latestOcr: OcrResult? = null,
    
    // Test Images state
    val selectedIndex: Int = 0,
    val imageNames: List<String> = emptyList(),
    val validationState: CameraValidationState = CameraValidationState(),
    val isIngesting: Boolean = false,
    val errorMsg: String? = null
)

class ScanViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val framePipeline = FramePipeline(throttleMs = 0L)
    private val ocrPipeline = OCRPipeline()
    private val vocabulary = IngredientVocabulary()
    private val semanticPipeline = SemanticPipeline(vocabulary)
    private val pipelineRunner = com.example.core.pipeline.PipelineRunner(ocrPipeline, semanticPipeline)

    private var latestBitmap: android.graphics.Bitmap? = null

    val framePipelineInstance: FramePipeline get() = framePipeline
    val ocrPipelineInstance: OCRPipeline get() = ocrPipeline

    init {
        val initialMode = if (FeatureFlags.enableTestImages) DebugMode.TestImages else DebugMode.LiveCamera
        _uiState.update { it.copy(mode = initialMode) }
    }

    fun setMode(mode: DebugMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun setLatestFrame(frame: FrameAnalysisResult) {
        _uiState.update { it.copy(latestFrame = frame) }
    }

    fun setLatestOcr(ocr: OcrResult) {
        _uiState.update { it.copy(latestOcr = ocr) }
        ocr.frameBitmap?.let {
            latestBitmap = it
        }
    }

    fun initializeTestImages(imageNames: List<String>, repository: TestLabelAssetRepository) {
        _uiState.update { it.copy(imageNames = imageNames, selectedIndex = 0) }
        loadTestImage(repository)
    }

    fun selectPreviousTestImage(repository: TestLabelAssetRepository) {
        val names = _uiState.value.imageNames
        if (names.isEmpty()) return
        val currentIdx = _uiState.value.selectedIndex
        val newIdx = if (currentIdx == 0) names.lastIndex else currentIdx - 1
        _uiState.update { it.copy(selectedIndex = newIdx) }
        loadTestImage(repository)
    }

    fun selectNextTestImage(repository: TestLabelAssetRepository) {
        val names = _uiState.value.imageNames
        if (names.isEmpty()) return
        val currentIdx = _uiState.value.selectedIndex
        val newIdx = if (currentIdx == names.lastIndex) 0 else currentIdx + 1
        _uiState.update { it.copy(selectedIndex = newIdx) }
        loadTestImage(repository)
    }

    private fun loadTestImage(repository: TestLabelAssetRepository) {
        val idx = _uiState.value.selectedIndex
        val names = _uiState.value.imageNames
        if (names.isEmpty() || idx !in names.indices) return

        val fileName = names[idx]
        _uiState.update { it.copy(validationState = CameraValidationState(status = "Loading bitmap")) }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val asset = repository.load(fileName)
                val frame = ImageFrame.BitmapFrame(
                    bitmap = asset.bitmap,
                    rotationDegrees = asset.rotationDegrees,
                    timestampNanos = System.nanoTime(),
                    source = ImageSource.TEST_ASSET
                )
                val frameResult = requireNotNull(framePipeline(frame)) {
                    "Frame pipeline throttled test asset unexpectedly."
                }
                val ocrResult = ocrPipeline(Pair(frame, frameResult))

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            validationState = CameraValidationState(
                                asset = asset,
                                frameResult = frameResult,
                                ocrResult = ocrResult,
                                status = when {
                                    ocrResult.skippedReason != null -> "Frame valid, OCR skipped: ${ocrResult.skippedReason}"
                                    ocrResult.segmentsProcessed > 1 -> "Frame valid, OCR merged ${ocrResult.segmentsProcessed} segments"
                                    ocrResult.text.isBlank() -> "Frame valid, OCR returned no text"
                                    else -> "Frame valid, OCR complete"
                                }
                            )
                        )
                    }
                }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            validationState = CameraValidationState(
                                status = "Pipeline failed",
                                errorMessage = error.message ?: error::class.java.simpleName
                            )
                        )
                    }
                }
            }
        }
    }

    fun ingestTestImage(context: Context, navController: NavController) {
        val validation = _uiState.value.validationState
        val ocrResult = validation.ocrResult
        android.util.Log.d("NUTRIGUARD_DEBUG", "ingestTestImage CALLED: ocrResult=${ocrResult != null}, text='${ocrResult?.text?.take(50)}', isIngesting=${_uiState.value.isIngesting}")
        if (ocrResult == null || ocrResult.text.isBlank()) {
            android.util.Log.w("NUTRIGUARD_DEBUG", "ingestTestImage EARLY RETURN: ocrResult=$ocrResult, textBlank=${ocrResult?.text?.isBlank()}")
            return
        }

        _uiState.update { it.copy(isIngesting = true, errorMsg = null) }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                processAndNavigate(
                    context = context,
                    sourceName = validation.asset?.fileName ?: "Test Image",
                    ocrResult = ocrResult,
                    navController = navController
                )
            } catch (e: Throwable) {
                android.util.Log.e("NUTRIGUARD_DEBUG", "Ingestion EXCEPTION for test image: ${e::class.java.simpleName}: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMsg = "${e::class.java.simpleName}: ${e.message}") }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isIngesting = false) }
                }
            }
        }
    }

    fun ingestLiveCamera(context: Context, navController: NavController) {
        val ocrResult = _uiState.value.latestOcr
        if (ocrResult == null || ocrResult.text.isBlank()) return

        viewModelScope.launch(Dispatchers.Default) {
            try {
                processAndNavigate(
                    context = context,
                    sourceName = "Live Camera Scan",
                    ocrResult = ocrResult,
                    navController = navController
                )
            } catch (e: Throwable) {
                android.util.Log.e("NUTRIGUARD_DEBUG", "Ingestion failed for live camera", e)
            }
        }
    }

    private suspend fun processAndNavigate(
        context: Context,
        sourceName: String,
        ocrResult: OcrResult,
        navController: NavController
    ) = withContext(Dispatchers.Default) {
        android.util.Log.d("NUTRIGUARD_DEBUG", "processAndNavigate START for $sourceName")
        val executionId = java.util.UUID.randomUUID()
        val ocrText = ocrResult.text
        val ocrLatency = ocrResult.processingLatencyMs
        val ocrConfidence = ocrResult.averageConfidence ?: 0.8f

        val pipeline = semanticPipeline

        val ocrMetadata = OcrMetadata(
            ocrConfidence = ocrConfidence,
            blurScore = ocrResult.blurScore,
            contrastScore = ocrResult.contrastScore,
            brightnessScore = ocrResult.brightnessScore
        )
        val ingestionResult = pipeline(Pair(ocrText, ocrMetadata))

        // Execute PipelineRunner if useExecutionGraph is active
        var pipelineResult: PipelineResult? = null
        if (FeatureFlags.useExecutionGraph) {
            val bitmap = if (sourceName == "Live Camera Scan") {
                latestBitmap
            } else {
                _uiState.value.validationState.asset?.bitmap ?: latestBitmap
            }
            val rotationDegrees = if (sourceName == "Live Camera Scan") {
                0
            } else {
                _uiState.value.validationState.asset?.rotationDegrees ?: 0
            }
            val imageSource = if (sourceName == "Live Camera Scan") ImageSource.CAMERA_X else ImageSource.TEST_ASSET

            if (bitmap != null) {
                try {
                    val config = PipelineConfig(
                        mode = PipelineMode.DEVELOPER,
                        enableReplay = FeatureFlags.enableReplay,
                        enableMetrics = true,
                        enableOverlayData = true
                    )
                    pipelineResult = pipelineRunner.run(
                        bitmap = bitmap,
                        rotationDegrees = rotationDegrees,
                        source = imageSource,
                        config = config,
                        context = context
                    )
                } catch (e: Throwable) {
                    android.util.Log.e("NUTRIGUARD_VAL", "PipelineRunner failed, falling back to legacy", e)
                }
            } else {
                android.util.Log.w("NUTRIGUARD_VAL", "useExecutionGraph is true but active bitmap is null. Falling back to legacy path.")
            }
        }

        // Compare Result A (Legacy) and Result B (PipelineRunner execution graph)
        if (pipelineResult != null) {
            try {
                // 1. Compare Ingredients
                val ingA = ingestionResult.correction.output.map { it.canonical }.sorted()
                val ingB = pipelineResult.semanticIngredients.map { it.canonical }.sorted()
                if (ingA != ingB) {
                    android.util.Log.w("NUTRIGUARD_VAL", "Ingredients mismatch! Legacy ($ingA) vs Execution Graph ($ingB)")
                }

                // 2. Compare Allergens
                val allergensA = ingestionResult.correction.output.flatMap { res ->
                    val contextualReconstructionText = if (res.confidenceStep != null) {
                        if (res.confidenceStep.contextBonus > 0.0f) res.canonical else null
                    } else {
                        if (res.explanationHint?.type == ExplanationType.CONTEXTUAL_RECONSTRUCTION) res.canonical else null
                    }
                    val baseConfidence = res.confidenceStep?.baseConfidence ?: res.confidence
                    IngredientInterpreter.interpret(
                        canonicalName = res.canonical,
                        confidence = res.confidence,
                        originalToken = res.originalToken,
                        contextualReconstructionText = contextualReconstructionText,
                        baseConfidence = baseConfidence,
                        provenance = DatasetProvenance.REAL_WORLD,
                        calibrationEligible = true
                    ).warnings.filter { it.startsWith("Contains allergen:") }
                }.sorted()

                val allergensB = pipelineResult.allergenInterpretation?.allergensDetected?.map { "Contains allergen: $it" }?.sorted() ?: emptyList()
                if (allergensA != allergensB) {
                    android.util.Log.w("NUTRIGUARD_VAL", "Allergens mismatch! Legacy ($allergensA) vs Execution Graph ($allergensB)")
                }

                // 3. Compare Warnings
                val warningsA = ingestionResult.correction.output.flatMap { res ->
                    val contextualReconstructionText = if (res.confidenceStep != null) {
                        if (res.confidenceStep.contextBonus > 0.0f) res.canonical else null
                    } else {
                        if (res.explanationHint?.type == ExplanationType.CONTEXTUAL_RECONSTRUCTION) res.canonical else null
                    }
                    val baseConfidence = res.confidenceStep?.baseConfidence ?: res.confidence
                    IngredientInterpreter.interpret(
                        canonicalName = res.canonical,
                        confidence = res.confidence,
                        originalToken = res.originalToken,
                        contextualReconstructionText = contextualReconstructionText,
                        baseConfidence = baseConfidence,
                        provenance = DatasetProvenance.REAL_WORLD,
                        calibrationEligible = true
                    ).warnings
                }.sorted()

                val warningsB = pipelineResult.interpretedIngredients.flatMap { it.warnings }.sorted()
                if (warningsA != warningsB) {
                    android.util.Log.w("NUTRIGUARD_VAL", "Warnings mismatch! Legacy ($warningsA) vs Execution Graph ($warningsB)")
                }

                // 4. Compare Interpretations
                val interpretationsA = ingestionResult.correction.output.map { res ->
                    val contextualReconstructionText = if (res.confidenceStep != null) {
                        if (res.confidenceStep.contextBonus > 0.0f) res.canonical else null
                    } else {
                        if (res.explanationHint?.type == ExplanationType.CONTEXTUAL_RECONSTRUCTION) res.canonical else null
                    }
                    val baseConfidence = res.confidenceStep?.baseConfidence ?: res.confidence
                    val interp = IngredientInterpreter.interpret(
                        canonicalName = res.canonical,
                        confidence = res.confidence,
                        originalToken = res.originalToken,
                        contextualReconstructionText = contextualReconstructionText,
                        baseConfidence = baseConfidence,
                        provenance = DatasetProvenance.REAL_WORLD,
                        calibrationEligible = true
                    )
                    "${interp.canonicalName}:${interp.category.name}:${interp.additiveCode}"
                }.sorted()

                val interpretationsB = pipelineResult.interpretedIngredients.map { interp ->
                    "${interp.canonicalName}:${interp.category.name}:${interp.additiveCode}"
                }.sorted()
                if (interpretationsA != interpretationsB) {
                    android.util.Log.w("NUTRIGUARD_VAL", "Interpretations mismatch! Legacy ($interpretationsA) vs Execution Graph ($interpretationsB)")
                }

                // 5. Compare Confidence
                val confidenceA = ingestionResult.correction.output.map { it.confidence }.average().takeIf { !it.isNaN() } ?: 0.0
                val confidenceB = pipelineResult.metrics.averageConfidence.toDouble()
                if (Math.abs(confidenceA - confidenceB) > 0.01) {
                    android.util.Log.w("NUTRIGUARD_VAL", "Confidence mismatch! Legacy ($confidenceA) vs Execution Graph ($confidenceB)")
                }

                // 6. Compare Replay Outputs
                val normA = ingestionResult.normalization.output
                val normB = pipelineResult.replayTrace.find { it.stageName == "normalization" }?.output ?: ""
                if (normA != normB) {
                    android.util.Log.w("NUTRIGUARD_VAL", "Replay Output (normalization) mismatch! Legacy ($normA) vs Execution Graph ($normB)")
                }
            } catch (comparisonErr: Throwable) {
                android.util.Log.e("NUTRIGUARD_VAL", "Error comparing parallel validation outputs", comparisonErr)
            }
        }

        // Replay Persistence
        if (pipelineResult != null) {
            val failuresListB = mutableListOf<Map<String, Any>>()
            pipelineResult.failures.forEach { fail ->
                failuresListB.add(mapOf(
                    "failure_type" to fail.failureType.name,
                    "stage" to fail.stage,
                    "details" to fail.details
                ))
            }
            if (FeatureFlags.enableReplay && AppSettings.replaySaving && failuresListB.isNotEmpty()) {
                val metricsB = mapOf(
                    "avg_confidence" to pipelineResult.metrics.averageConfidence.toDouble(),
                    "ingredient_count" to pipelineResult.semanticIngredients.size.toDouble(),
                    "ocr_character_count" to (pipelineResult.ocrLines.flatMap { it.words }.joinToString(" ") { it.text }.length.toDouble())
                )
                val normalizedTextB = pipelineResult.replayTrace.find { it.stageName == "normalization" }?.output ?: ""
                val extractedIngredientsB = pipelineResult.replayTrace.find { it.stageName == "extraction" }?.output?.split(", ")?.filter { it.isNotBlank() } ?: emptyList()
                val canonicalIngredientsB = pipelineResult.semanticIngredients.map { result ->
                    val contextualReconstructionText = if (result.disambiguationRule != null || result.debugSteps.any { it.contains("contextual bonus:") }) result.canonical else null
                    val baseConfLine = result.debugSteps.firstOrNull { it.startsWith("base confidence:") }
                    val baseConfidence = baseConfLine?.substringAfter("base confidence:")?.trim()?.toFloatOrNull() ?: result.confidence

                    val interpretation = IngredientInterpreter.interpret(
                        canonicalName = result.canonical,
                        confidence = result.confidence,
                        originalToken = result.originalToken,
                        contextualReconstructionText = contextualReconstructionText,
                        baseConfidence = baseConfidence,
                        provenance = DatasetProvenance.REAL_WORLD,
                        calibrationEligible = true
                    )

                    com.example.core.intelligence.correction.CorrectionResult(
                        canonical = result.canonical,
                        confidence = result.confidence,
                        failures = result.failures,
                        debugSteps = result.debugSteps,
                        phraseWindow = result.phraseWindow,
                        ontologyCategory = result.ontologyCategory,
                        disambiguationRule = result.disambiguationRule,
                        groupPath = result.groupPath,
                        interpretedCategory = interpretation.category.name,
                        additiveCode = interpretation.additiveCode,
                        explanation = interpretation.explanation,
                        warnings = interpretation.warnings
                    )
                }

                val latenciesMapB = mapOf(
                    "ocr" to pipelineResult.metrics.ocrLatencyMs,
                    "normalization" to pipelineResult.metrics.normalizationLatencyMs,
                    "extraction" to pipelineResult.metrics.extractionLatencyMs,
                    "grouping" to pipelineResult.metrics.groupingLatencyMs,
                    "phrase_correction" to pipelineResult.metrics.phraseCorrectionLatencyMs,
                    "correction" to pipelineResult.metrics.correctionLatencyMs
                )

                ReplayStorageHelper.saveReplay(
                    context = context,
                    sourceImage = sourceName,
                    ocrOutput = pipelineResult.ocrLines.flatMap { it.words }.joinToString(" ") { it.text }.ifBlank { ocrText },
                    normalizedText = normalizedTextB,
                    extractedIngredients = extractedIngredientsB,
                    canonicalIngredients = canonicalIngredientsB,
                    metrics = metricsB,
                    failures = failuresListB,
                    latencyMetrics = latenciesMapB,
                    ocrWords = pipelineResult.ocrBlocks.flatMap { it.lines }.flatMap { it.words },
                    reconstructedLines = pipelineResult.ocrLines,
                    detectedParagraphs = emptyList(),
                    passesRun = ocrResult.passesRun
                )
            }
        } else {
            val failuresList = mutableListOf<Map<String, Any>>()

            ocrResult.failures.forEach { fail ->
                failuresList.add(mapOf(
                    "failure_type" to fail.name,
                    "stage" to "ocr",
                    "details" to "OCR stage error: ${ocrResult.skippedReason ?: "unknown validation failure"}"
                ))
            }

            ingestionResult.normalization.failures.forEach { fail ->
                failuresList.add(mapOf(
                    "failure_type" to fail.name,
                    "stage" to "normalization",
                    "details" to "Normalization failed: output was blank"
                ))
            }
            ingestionResult.extraction.failures.forEach { fail ->
                failuresList.add(mapOf(
                    "failure_type" to fail.name,
                    "stage" to "extraction",
                    "details" to "Extraction failed: zero tokens parsed from input"
                ))
            }
            ingestionResult.correction.output.forEach { res ->
                res.failures.forEach { fail ->
                    failuresList.add(mapOf(
                        "failure_type" to fail.name,
                        "stage" to "correction",
                        "details" to when(fail) {
                            FailureType.UNKNOWN_INGREDIENT_FAILURE -> "Unknown ingredient \"${res.originalToken}\" not found in vocabulary or ontology."
                            FailureType.AMBIGUOUS_MATCH_FAILURE -> "Ambiguous match detected for \"${res.originalToken}\"."
                            FailureType.FUZZY_CORRECTION_FAILURE -> "Fuzzy correction quality exception for \"${res.originalToken}\"."
                            FailureType.LOW_CONFIDENCE_CORRECTION_FAILURE -> "Low correction confidence for \"${res.originalToken}\" -> \"${res.canonical}\"."
                            else -> "Correction exception detected."
                        }
                    ))
                }
            }

            if (FeatureFlags.enableReplay && AppSettings.replaySaving && failuresList.isNotEmpty()) {
                val metrics = mapOf(
                    "avg_confidence" to (ingestionResult.correction.output.map { it.confidence }.average().takeIf { !it.isNaN() } ?: 0.0),
                    "ingredient_count" to ingestionResult.correction.output.size.toDouble(),
                    "ocr_character_count" to ocrText.length.toDouble()
                )
                val latenciesMap = mapOf(
                    "ocr" to ocrLatency,
                    "normalization" to ingestionResult.normalization.latencyMs,
                    "extraction" to ingestionResult.extraction.latencyMs,
                    "grouping" to ingestionResult.grouping.latencyMs,
                    "phrase_correction" to ingestionResult.phraseCorrection.latencyMs,
                    "correction" to ingestionResult.correction.latencyMs
                )
                ReplayStorageHelper.saveReplay(
                    context = context,
                    sourceImage = sourceName,
                    ocrOutput = ocrText,
                    normalizedText = ingestionResult.normalization.output,
                    extractedIngredients = ingestionResult.extraction.output,
                    canonicalIngredients = ingestionResult.correction.output,
                    metrics = metrics,
                    failures = failuresList,
                    latencyMetrics = latenciesMap,
                    ocrWords = ocrResult.ocrWords,
                    reconstructedLines = ocrResult.reconstructedLines,
                    detectedParagraphs = ocrResult.detectedParagraphs,
                    passesRun = ocrResult.passesRun
                )
            }
        }

        // Cache Snapshot to Repository
        if (pipelineResult == null) {
            val semanticIngredients = ingestionResult.correction.output.map { result ->
                val contextualReconstructionText = if (result.confidenceStep != null) {
                    if (result.confidenceStep.contextBonus > 0.0f) result.canonical else null
                } else {
                    if (result.explanationHint?.type == ExplanationType.CONTEXTUAL_RECONSTRUCTION) result.canonical else null
                }
                val baseConfidence = result.confidenceStep?.baseConfidence ?: result.confidence

                val interpretation = IngredientInterpreter.interpret(
                    canonicalName = result.canonical,
                    confidence = result.confidence,
                    originalToken = result.originalToken,
                    contextualReconstructionText = contextualReconstructionText,
                    baseConfidence = baseConfidence
                )
                com.example.core.pipeline.SemanticIngredient(
                    canonical = result.canonical,
                    originalToken = result.originalToken,
                    confidence = result.confidence,
                    failures = result.failures,
                    debugSteps = result.debugSteps,
                    phraseWindow = result.phraseWindow,
                    ontologyCategory = result.ontologyCategory,
                    disambiguationRule = result.disambiguationRule,
                    groupPath = result.groupPath,
                    interpretedCategory = interpretation.category.name,
                    additiveCode = interpretation.additiveCode,
                    explanation = interpretation.explanation,
                    warnings = interpretation.warnings
                )
            }

            val interpretedIngredients = semanticIngredients.map { ing ->
                val baseConfLine = ing.debugSteps.firstOrNull { it.startsWith("base confidence:") }
                val baseConfidence = baseConfLine?.substringAfter("base confidence:")?.trim()?.toFloatOrNull() ?: ing.confidence
                val contextualReconstructionText = if (ing.disambiguationRule != null || ing.debugSteps.any { it.contains("contextual bonus:") }) ing.canonical else null

                IngredientInterpreter.interpret(
                    canonicalName = ing.canonical,
                    confidence = ing.confidence,
                    originalToken = ing.originalToken,
                    contextualReconstructionText = contextualReconstructionText,
                    baseConfidence = baseConfidence
                )
            }

            val replayTraceList = listOf(
                com.example.core.replay.ReplayStageTrace(
                    stageName = "ocr",
                    input = "image",
                    output = ocrText,
                    latencyMs = ocrLatency
                ),
                com.example.core.replay.ReplayStageTrace(
                    stageName = "normalization",
                    input = ocrText,
                    output = ingestionResult.normalization.output,
                    latencyMs = ingestionResult.normalization.latencyMs
                ),
                com.example.core.replay.ReplayStageTrace(
                    stageName = "extraction",
                    input = ingestionResult.normalization.output,
                    output = ingestionResult.extraction.output.joinToString(", "),
                    latencyMs = ingestionResult.extraction.latencyMs
                ),
                com.example.core.replay.ReplayStageTrace(
                    stageName = "correction",
                    input = ingestionResult.phraseCorrection.output.joinToString(", "),
                    output = ingestionResult.correction.output.map { it.canonical }.joinToString(", "),
                    latencyMs = ingestionResult.correction.latencyMs
                )
            )

            val pipelineFailuresList = mutableListOf<com.example.core.pipeline.PipelineFailure>()
            ocrResult.failures.forEach { fail ->
                pipelineFailuresList.add(com.example.core.pipeline.PipelineFailure(fail, "ocr", "OCR stage error: ${ocrResult.skippedReason ?: "unknown validation failure"}"))
            }
            ingestionResult.normalization.failures.forEach { fail ->
                pipelineFailuresList.add(com.example.core.pipeline.PipelineFailure(fail, "normalization", "Normalization failed: output was blank"))
            }
            ingestionResult.extraction.failures.forEach { fail ->
                pipelineFailuresList.add(com.example.core.pipeline.PipelineFailure(fail, "extraction", "Extraction failed: zero tokens parsed from input"))
            }
            ingestionResult.correction.output.forEach { res ->
                res.failures.forEach { fail ->
                    pipelineFailuresList.add(com.example.core.pipeline.PipelineFailure(fail, "correction", "Token correction warning on '${res.originalToken}': ${fail.name}"))
                }
            }

            val latenciesMap = mapOf(
                "ocr" to ocrLatency,
                "normalization" to ingestionResult.normalization.latencyMs,
                "extraction" to ingestionResult.extraction.latencyMs,
                "grouping" to ingestionResult.grouping.latencyMs,
                "phrase_correction" to ingestionResult.phraseCorrection.latencyMs,
                "correction" to ingestionResult.correction.latencyMs
            )

            val pipelineMetrics = com.example.core.pipeline.PipelineMetrics(
                ocrLatencyMs = ocrLatency,
                normalizationLatencyMs = ingestionResult.normalization.latencyMs,
                extractionLatencyMs = ingestionResult.extraction.latencyMs,
                groupingLatencyMs = ingestionResult.grouping.latencyMs,
                phraseCorrectionLatencyMs = ingestionResult.phraseCorrection.latencyMs,
                correctionLatencyMs = ingestionResult.correction.latencyMs,
                totalLatencyMs = latenciesMap.values.sum(),
                memoryUsageKb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024L,
                averageConfidence = ocrConfidence
            )

            val preprocessingProfile = com.example.core.pipeline.PreprocessingProfile(
                blurScore = ocrResult.blurScore,
                contrastScore = ocrResult.contrastScore,
                brightnessScore = ocrResult.brightnessScore,
                complexityRating = ocrResult.complexityRating,
                routedStrategy = ocrResult.routedStrategy
            )

            val legacyPipelineResult = com.example.core.pipeline.PipelineResult(
                executionId = executionId,
                ocrBlocks = ocrResult.ocrBlocks,
                ocrLines = ocrResult.reconstructedLines,
                semanticIngredients = semanticIngredients,
                interpretedIngredients = interpretedIngredients,
                replayTrace = replayTraceList,
                metrics = pipelineMetrics,
                preprocessingProfile = preprocessingProfile,
                failures = pipelineFailuresList
            )

            val renamedPaths = com.example.core.export.PipelineSnapshotRepository.renameTempFiles(context, executionId.toString())
            val snapshot = com.example.core.export.PipelineSnapshot(
                executionId = executionId.toString(),
                rawImagePath = renamedPaths.first,
                preprocessedImagePath = renamedPaths.second,
                result = legacyPipelineResult,
                timestamp = System.currentTimeMillis(),
                scanSource = sourceName
            )
            println("ScanViewModel: Adding snapshot with executionId = '${executionId.toString()}' to PipelineSnapshotRepository")
            com.example.core.export.PipelineSnapshotRepository.add(snapshot)
        }

        // Build routeArgs
        val routeArgs = if (pipelineResult != null) {
            val canonicalJsonB = JSONArray().apply {
                pipelineResult.semanticIngredients.forEach { result ->
                    put(JSONObject().apply {
                        put("canonical", result.canonical)
                        put("confidence", result.confidence.toDouble())
                        put("originalToken", result.originalToken)
                        put("ontologyCategory", result.ontologyCategory ?: "")
                        put("disambiguationRule", result.disambiguationRule ?: "")
                        put("groupPath", result.groupPath)
                        put("interpretedCategory", result.interpretedCategory ?: "")
                        put("additiveCode", result.additiveCode ?: "")
                        put("explanation", result.explanation ?: "")

                        val warningsArr = JSONArray()
                        result.warnings.forEach { warningsArr.put(it) }
                        put("warnings", warningsArr)

                        val stepsArr = JSONArray()
                        result.debugSteps.forEach { stepsArr.put(it) }
                        put("debugSteps", stepsArr)

                        val failsArr = JSONArray()
                        result.failures.forEach { failsArr.put(it.name) }
                        put("failures", failsArr)

                        val phraseArr = JSONArray()
                        result.phraseWindow.forEach { phraseArr.put(it) }
                        put("phraseWindow", phraseArr)
                    })
                }
            }.toString()

            val latenciesJsonB = JSONObject().apply {
                put("ocr", pipelineResult.metrics.ocrLatencyMs)
                put("normalization", pipelineResult.metrics.normalizationLatencyMs)
                put("extraction", pipelineResult.metrics.extractionLatencyMs)
                put("grouping", pipelineResult.metrics.groupingLatencyMs)
                put("phrase_correction", pipelineResult.metrics.phraseCorrectionLatencyMs)
                put("correction", pipelineResult.metrics.correctionLatencyMs)
            }.toString()

            Screen.Results(
                rawOcrText = pipelineResult.ocrLines.flatMap { it.words }.joinToString(" ") { it.text }.ifBlank { ocrText },
                normalizedText = pipelineResult.replayTrace.find { it.stageName == "normalization" }?.output ?: "",
                extractedTokens = pipelineResult.replayTrace.find { it.stageName == "extraction" }?.output?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
                canonicalJson = canonicalJsonB,
                latencyJson = latenciesJsonB,
                executionId = pipelineResult.executionId.toString()
            )
        } else {
            val canonicalJsonA = JSONArray().apply {
                ingestionResult.correction.output.forEach { result ->
                    put(JSONObject().apply {
                        put("canonical", result.canonical)
                        put("confidence", result.confidence.toDouble())
                        put("originalToken", result.originalToken)
                        put("ontologyCategory", result.ontologyCategory ?: "")
                        put("disambiguationRule", result.disambiguationRule ?: "")
                        put("groupPath", result.groupPath)

                        val contextualReconstructionText = if (result.confidenceStep != null) {
                            if (result.confidenceStep.contextBonus > 0.0f) result.canonical else null
                        } else {
                            if (result.explanationHint?.type == ExplanationType.CONTEXTUAL_RECONSTRUCTION) result.canonical else null
                        }
                        val baseConfidence = result.confidenceStep?.baseConfidence ?: result.confidence

                        val interpretation = IngredientInterpreter.interpret(
                            canonicalName = result.canonical,
                            confidence = result.confidence,
                            originalToken = result.originalToken,
                            contextualReconstructionText = contextualReconstructionText,
                            baseConfidence = baseConfidence,
                            provenance = DatasetProvenance.REAL_WORLD,
                            calibrationEligible = true
                        )
                        put("interpretedCategory", interpretation.category.name)
                        put("additiveCode", interpretation.additiveCode ?: "")
                        put("explanation", interpretation.explanation ?: "")
                        put("provenance", interpretation.provenance.name)
                        put("calibrationEligible", interpretation.calibrationEligible)


                        val warningsArr = JSONArray()
                        interpretation.warnings.forEach { warningsArr.put(it) }
                        put("warnings", warningsArr)

                        val stepsArr = JSONArray()
                        result.debugSteps.forEach { stepsArr.put(it) }
                        put("debugSteps", stepsArr)

                        val failsArr = JSONArray()
                        result.failures.forEach { failsArr.put(it.name) }
                        put("failures", failsArr)

                        val phraseArr = JSONArray()
                        result.phraseWindow.forEach { phraseArr.put(it) }
                        put("phraseWindow", phraseArr)

                        if (result.confidenceStep != null) {
                            put("confidenceStep", JSONObject().apply {
                                put("baseConfidence", result.confidenceStep.baseConfidence.toDouble())
                                put("contextBonus", result.confidenceStep.contextBonus.toDouble())
                                put("finalConfidence", result.confidenceStep.finalConfidence.toDouble())
                                put("reason", result.confidenceStep.reason ?: "")
                                val infTokensArr = JSONArray()
                                result.confidenceStep.influencingTokens.forEach { infTokensArr.put(it) }
                                put("influencingTokens", infTokensArr)
                            })
                        }
                        val infArr = JSONArray()
                        result.influencingTokens.forEach { infArr.put(it) }
                        put("influencingTokens", infArr)

                        if (result.explanationHint != null) {
                            put("explanationHint", JSONObject().apply {
                                put("type", result.explanationHint.type.name)
                                put("originalText", result.explanationHint.originalText ?: "")
                                put("reconstructedText", result.explanationHint.reconstructedText ?: "")
                                put("reason", result.explanationHint.reason)
                            })
                        }
                    })
                }
            }.toString()

            val latenciesMap = mapOf(
                "ocr" to ocrLatency,
                "normalization" to ingestionResult.normalization.latencyMs,
                "extraction" to ingestionResult.extraction.latencyMs,
                "grouping" to ingestionResult.grouping.latencyMs,
                "phrase_correction" to ingestionResult.phraseCorrection.latencyMs,
                "correction" to ingestionResult.correction.latencyMs
            )
            val latenciesJsonA = JSONObject().apply {
                latenciesMap.forEach { (k, v) -> put(k, v) }
            }.toString()

            Screen.Results(
                rawOcrText = ocrText,
                normalizedText = ingestionResult.normalization.output,
                extractedTokens = ingestionResult.extraction.output,
                canonicalJson = canonicalJsonA,
                latencyJson = latenciesJsonA,
                executionId = executionId.toString()
            )
        }

        android.util.Log.d("NUTRIGUARD_DEBUG", "processAndNavigate END - navigating to ResultsScreen")
        withContext(Dispatchers.Main) {
            navController.navigateTo(routeArgs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        ocrPipeline.close()
    }
}
