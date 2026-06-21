package com.example.ui.features.production

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.frame.FrameAnalysisResult
import com.example.core.frame.FramePipeline
import com.example.core.imaging.ImageFrame
import com.example.core.imaging.ImageSource
import com.example.core.pipeline.OCRPipeline
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

    private val framePipeline by lazy { FramePipeline(throttleMs = 0L) }
    private val ocrPipeline by lazy { OCRPipeline() }
    private val vocabulary by lazy { IngredientVocabulary() }
    private val pipelineRunner by lazy { com.example.core.pipeline.PipelineRunner(ocrPipeline, vocabulary) }

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
        ocr.frameBitmap?.let { newBitmap ->
            // ISSUE-002 FIX: Recycle the previous bitmap before overwriting
            // to prevent unbounded bitmap accumulation in memory.
            latestBitmap?.recycle()
            latestBitmap = newBitmap
        }
    }

    /**
     * Initializes the list of test images available for validation mode.
     * Sets the default selected index to 0 and triggers loading the first image.
     *
     * @param imageNames List of assets representing test label images.
     * @param repository Test label asset loader helper.
     */
    fun initializeTestImages(imageNames: List<String>, repository: TestLabelAssetRepository) {
        _uiState.update { it.copy(imageNames = imageNames, selectedIndex = 0) }
        loadTestImage(repository)
    }

    /**
     * Cycles to the previous test image in the list and triggers loading it.
     */
    fun selectPreviousTestImage(repository: TestLabelAssetRepository) {
        val names = _uiState.value.imageNames
        if (names.isEmpty()) return
        val currentIdx = _uiState.value.selectedIndex
        val newIdx = if (currentIdx == 0) names.lastIndex else currentIdx - 1
        _uiState.update { it.copy(selectedIndex = newIdx) }
        loadTestImage(repository)
    }

    /**
     * Cycles to the next test image in the list and triggers loading it.
     */
    fun selectNextTestImage(repository: TestLabelAssetRepository) {
        val names = _uiState.value.imageNames
        if (names.isEmpty()) return
        val currentIdx = _uiState.value.selectedIndex
        val newIdx = if (currentIdx == names.lastIndex) 0 else currentIdx + 1
        _uiState.update { it.copy(selectedIndex = newIdx) }
        loadTestImage(repository)
    }

    /**
     * Loads the test image asset asynchronously, executes the frame analysis pipeline and
     * OCR detection, and updates the validation UI state.
     *
     * Steps:
     * 1. Recycle previously loaded bitmap to prevent leaks.
     * 2. Load the target asset from repository.
     * 3. Construct an [ImageFrame.BitmapFrame].
     * 4. Execute [framePipeline] and [ocrPipeline].
     * 5. Update UI state with status messages indicating validation success or failure.
     */
    private fun loadTestImage(repository: TestLabelAssetRepository) {
        val idx = _uiState.value.selectedIndex
        val names = _uiState.value.imageNames
        if (names.isEmpty() || idx !in names.indices) return

        val fileName = names[idx]
        _uiState.update { it.copy(validationState = CameraValidationState(status = "Loading bitmap")) }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                _uiState.value.validationState.ocrResult?.frameBitmap?.recycle()
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

    /**
     * Triggers the semantic analysis pipeline on the currently loaded test image and
     * navigates to the results screen.
     *
     * @param context Android context for storage access.
     * @param navController Controller for navigating between views.
     */
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

    /**
     * Triggers the semantic analysis pipeline on the most recently captured live camera frame
     * and navigates to the results screen.
     *
     * @param context Android context for storage access.
     * @param navController Controller for navigating between views.
     */
    fun ingestLiveCamera(context: Context, navController: NavController) {
        val ocrResult = _uiState.value.latestOcr
        // BLACK-003 FIX: Check CURRENT state at invocation time (not at button-render time)
        // to avoid silent no-ops from race condition between button enable and tap.
        if (ocrResult == null || ocrResult.text.isBlank()) {
            android.util.Log.w("NUTRIGUARD_DEBUG", "ingestLiveCamera EARLY RETURN: no OCR text available")
            return
        }

        // BLACK-003 FIX: Set isIngesting state for live camera (same as test image path)
        _uiState.update { it.copy(isIngesting = true, errorMsg = null) }

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

    /**
     * Core orchestrator that runs the converged semantic interpretation pipeline on the selected image,
     * extracts structured JSON output, records replay metrics, and triggers navigation to the Results view.
     *
     * Steps:
     * 1. Resolve active bitmap and rotation angles based on scan source.
     * 2. Configure [PipelineConfig] and execute [pipelineRunner.run] with the pre-existing OCR results.
     * 3. Parse [PipelineResult] semantic ingredient listings into a serialized JSON array structure.
     * 4. Serialize step-by-step processing latency metrics.
     * 5. Record execution metadata trace via [ReplayStorageHelper] if replay telemetry is enabled.
     * 6. Navigate to the results screen passing the serialized pipeline outputs.
     */
    private suspend fun processAndNavigate(
        context: Context,
        sourceName: String,
        ocrResult: OcrResult,
        navController: NavController
    ) = withContext(Dispatchers.Default) {
        android.util.Log.d("NUTRIGUARD_DEBUG", "processAndNavigate START for $sourceName")
        val ocrText = ocrResult.text

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

        if (bitmap == null) {
            throw IllegalStateException("Active bitmap is null; cannot process label.")
        }

        val config = PipelineConfig(
            mode = PipelineMode.DEVELOPER,
            enableReplay = FeatureFlags.enableReplay,
            enableMetrics = true,
            enableOverlayData = true
        )
        val pipelineResult = pipelineRunner.run(
            bitmap = bitmap,
            rotationDegrees = rotationDegrees,
            source = imageSource,
            config = config,
            context = context,
            preExistingOcr = ocrResult
        )

        // Authoritative path: PipelineRunner -> PipelineResult
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

        // Save Replay trace
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
                com.example.core.intelligence.correction.CorrectionResult(
                    canonical = result.canonical,
                    confidence = result.confidence,
                    failures = result.failures,
                    debugSteps = result.debugSteps,
                    phraseWindow = result.phraseWindow,
                    ontologyCategory = result.ontologyCategory,
                    disambiguationRule = result.disambiguationRule,
                    groupPath = result.groupPath,
                    interpretedCategory = result.interpretedCategory,
                    additiveCode = result.additiveCode,
                    explanation = result.explanation,
                    warnings = result.warnings
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

        val routeArgs = Screen.Results(
            rawOcrText = pipelineResult.ocrLines.flatMap { it.words }.joinToString(" ") { it.text }.ifBlank { ocrText },
            normalizedText = pipelineResult.replayTrace.find { it.stageName == "normalization" }?.output ?: "",
            extractedTokens = pipelineResult.replayTrace.find { it.stageName == "extraction" }?.output?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
            canonicalJson = canonicalJsonB,
            latencyJson = latenciesJsonB,
            executionId = pipelineResult.executionId.toString()
        )

        android.util.Log.d("NUTRIGUARD_DEBUG", "processAndNavigate END - navigating to ResultsScreen")
        withContext(Dispatchers.Main) {
            navController.navigateTo(routeArgs)
        }
    }

    /**
     * Cleans up ViewModel state when destroyed:
     * 1. Closes the [ocrPipeline].
     * 2. Closes the [pipelineRunner] to release native ML Kit resources.
     * 3. Recycles all referenced temporary bitmaps.
     */
    override fun onCleared() {
        super.onCleared()
        ocrPipeline.close()
        // ISSUE-004 FIX: Close PipelineRunner to release StructuralLayoutAnalyzer.fastRecognizer
        // (ML Kit TextRecognizer — native resource that must be explicitly freed).
        pipelineRunner.close()
        // ISSUE-002 FIX: Recycle the last stored bitmap when ViewModel is destroyed
        // to release bitmap memory held across the ViewModel lifecycle.
        latestBitmap?.recycle()
        latestBitmap = null
        _uiState.value.validationState.ocrResult?.frameBitmap?.recycle()
        _uiState.value.latestOcr?.frameBitmap?.recycle()
    }

}
