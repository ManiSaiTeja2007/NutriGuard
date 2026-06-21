package com.example

import com.example.core.ingredient.*
import com.example.core.pipeline.graph.SpecializedInterpretationStage
import com.example.core.pipeline.graph.RoutingResult
import com.example.core.pipeline.graph.SemanticRoutingContext
import com.example.core.pipeline.graph.ExecutionProfiler
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.normalization.TextNormalizer
import com.example.core.ocr.routing.OCRPipelineRouter
import com.example.core.ocr.routing.OCRComplexityAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TextIntelligenceTest {

    /**
     * Verifies text normalization operations: casing unification, collapse of extra spaces,
     * hyphen-newline merging, and noisy punctuation/junk character removal.
     */
    @Test
    fun testTextNormalization() {
        val input = "INGREDIENTS:  SUAGR,\n SLT , CITNC- \n ACID"
        val normalized = TextNormalizer.normalize(input)

        // Casing normalized, hyphens/newlines merged, duplicate spaces collapsed
        assertEquals("ingredients: suagr, slt, citnc acid", normalized)

        // Noisy punctuation/junk removal (replaces junk with space and trims/collapses)
        val noisy = "|*ingredients:• sugar_ and ~ salt^"
        assertEquals("ingredients: sugar and salt", TextNormalizer.normalize(noisy))
    }

    /**
     * Verifies that the tokenizer correctly extracts sub-ingredients nested inside parentheses
     * as a single token, preserving parenthesis groupings.
     */
    @Test
    fun testParenthesisAwareExtraction() {
        val input = "ingredients: enriched flour (wheat flour, niacin, iron), sugar, salt"
        val section = IngredientExtractor.extractRawSection(input)
        val tokens = IngredientExtractor.tokenize(section)

        assertEquals(3, tokens.size)
        assertEquals("enriched flour (wheat flour, niacin, iron)", tokens[0])
        assertEquals("sugar", tokens[1])
        assertEquals("salt", tokens[2])
    }

    /**
     * Verifies exact spelling corrections, typo mapping, and Levenshtein fuzzy match
     * behavior based on camera blur metadata.
     */
    @Test
    fun testOCRTypoCorrection() {
        val vocab = IngredientVocabulary()
        val engine = com.example.core.intelligence.correction.OcrCorrectionEngine(vocab)

        // Vocabulary exact match
        val resSugar = engine.correct(listOf("sugar"), 0.8f)
        assertEquals(1, resSugar.size)
        assertEquals("sugar", resSugar.first().canonical)

        // Curated correction map
        val resSlt = engine.correct(listOf("slt"), 0.8f)
        assertEquals(1, resSlt.size)
        assertEquals("salt", resSlt.first().canonical)

        // Fuzzy correction (requires lower threshold profile, e.g. blurScore = 5.0f)
        val resFuzzy = engine.correct(listOf("suuugar"), OcrMetadata(ocrConfidence = 0.8f, blurScore = 5.0f))
        assertEquals("sugar", resFuzzy.first().canonical)
    }

    /**
     * Tests canonical name mapping (e.g. "vitamin c" -> "ascorbic acid") via the dictionary database.
     */
    @Test
    fun testCanonicalization() {
        assertEquals("salt", IngredientCanonicalizer.canonicalize("sodium chloride"))
        assertEquals("ascorbic acid", IngredientCanonicalizer.canonicalize("vitamin c"))
        assertEquals("monosodium glutamate", IngredientCanonicalizer.canonicalize("msg"))
        assertEquals("sugar", IngredientCanonicalizer.canonicalize("sugar")) // no-op
    }

    /**
     * Verifies that exact matches, typo maps, and fuzzy lookups score confidence correctly.
     */
    @Test
    fun testConfidenceScoring() {
        val vocab = IngredientVocabulary()
        val engine = com.example.core.intelligence.correction.OcrCorrectionEngine(vocab)
        val metadata = OcrMetadata(ocrConfidence = 0.8f)

        // Exact
        val exact = engine.correct(listOf("water"), metadata)
        assertEquals("water", exact.first().canonical)

        // Typo Map
        val typo = engine.correct(listOf("suagr"), metadata)
        assertEquals("sugar", typo.first().canonical)

        // Fuzzy (Levenshtein)
        val fuzzy = engine.correct(listOf("wateer"), metadata)
        assertEquals("water", fuzzy.first().canonical)
    }

    /**
     * Verifies that the specialized interpretation stage operates deterministically
     * when executed multiple times on identical inputs.
     */
    @Test
    fun testPipelineDeterminism() = runBlocking {
        val vocab = IngredientVocabulary()
        val stage = SpecializedInterpretationStage(vocab)
        val input = RoutingResult(null, null, null, null, listOf("ingredients: sugar, salt, msg, citric acid"))
        val context = SemanticRoutingContext(UUID.randomUUID(), 500, 500, OcrMetadata(ocrConfidence = 0.8f))

        val run1 = requireNotNull(stage.execute(input, context, ExecutionProfiler()).output).correction.output
        val run2 = requireNotNull(stage.execute(input, context, ExecutionProfiler()).output).correction.output

        assertEquals(run1.size, run2.size)
        run1.forEachIndexed { i, res ->
            assertEquals(res.originalToken, run2[i].originalToken)
            assertEquals(res.canonical, run2[i].canonical)
            assertEquals(res.confidence, run2[i].confidence, 0.001f)
            assertEquals(res.failures, run2[i].failures)
        }
    }

    /**
     * Verifies recovery of independent ingredient tokens from text missing comma delimiters.
     */
    @Test
    fun testCatastropheNoCommasSpacingRecovery() = runBlocking {
        val vocab = IngredientVocabulary()
        val stage = SpecializedInterpretationStage(vocab)
        val input = RoutingResult(null, null, null, null, listOf("ingredients: sugar salt citric acid msg"))
        val context = SemanticRoutingContext(UUID.randomUUID(), 500, 500, OcrMetadata(ocrConfidence = 0.8f))

        val result = requireNotNull(stage.execute(input, context, ExecutionProfiler()).output).correction.output
        val canonicals = result.map { it.canonical }

        assertTrue(canonicals.contains("sugar"))
        assertTrue(canonicals.contains("salt"))
        assertTrue(canonicals.contains("citric acid"))
        assertTrue(canonicals.contains("monosodium glutamate"))
    }

    /**
     * Verifies semantic recovery for text formatted in uppercase with frequent newlines.
     */
    @Test
    fun testCatastropheAllCapsAndNewlines() = runBlocking {
        val vocab = IngredientVocabulary()
        val stage = SpecializedInterpretationStage(vocab)
        val input = RoutingResult(null, null, null, null, listOf("INGREDIENTS: SUGAR,\nSALT,\nCITRIC ACID"))
        val context = SemanticRoutingContext(UUID.randomUUID(), 500, 500, OcrMetadata(ocrConfidence = 0.8f))

        val result = requireNotNull(stage.execute(input, context, ExecutionProfiler()).output).correction.output

        assertEquals(3, result.size)
        assertEquals("sugar", result[0].canonical)
        assertEquals("salt", result[1].canonical)
        assertEquals("citric acid", result[2].canonical)
    }

    /**
     * Verifies fallback behavior when words are catastrophically merged by OCR.
     */
    @Test
    fun testCatastropheOCRMergedWords() {
        val vocab = IngredientVocabulary()
        val engine = com.example.core.intelligence.correction.OcrCorrectionEngine(vocab)

        // "sugarandsalt" remains raw due to low confidence / high edit distance
        val result = engine.correct(listOf("sugarandsalt"), 0.8f)
        assertEquals("sugarandsalt", result.first().canonical)
        assertTrue(result.first().failures.contains(com.example.core.intelligence.correction.FailureType.UNKNOWN_INGREDIENT_FAILURE))
    }

    /**
     * Verifies fuzzy correction when letters are repeated in OCR output (e.g. "suuugar").
     */
    @Test
    fun testCatastropheRepeatedCharacters() {
        val vocab = IngredientVocabulary()
        val engine = com.example.core.intelligence.correction.OcrCorrectionEngine(vocab)

        // "suuugar" -> fuzzy-matches to "sugar" (requires blurry profile context)
        val repeated = engine.correct(listOf("suuugar"), OcrMetadata(ocrConfidence = 0.8f, blurScore = 5.0f))
        assertEquals("sugar", repeated.first().canonical)
    }

    /**
     * Verifies filter cleaning of invalid/noise ingredients (like ellipsis).
     */
    @Test
    fun testMalformedIngredients() = runBlocking {
        val vocab = IngredientVocabulary()
        val stage = SpecializedInterpretationStage(vocab)
        val input = RoutingResult(null, null, null, null, listOf("ingredients: sugar, ..., salt., water (enriched"))
        val context = SemanticRoutingContext(UUID.randomUUID(), 500, 500, OcrMetadata(ocrConfidence = 0.8f))

        val result = requireNotNull(stage.execute(input, context, ExecutionProfiler()).output).correction.output
        val originals = result.map { it.originalToken }

        assertTrue(originals.contains("sugar"))
        assertTrue(originals.contains("salt"))
        assertTrue(originals.contains("water (enriched"))
        assertFalse(originals.contains("..."))
    }

    /**
     * Verifies preservation of duplicate ingredient entries in the raw sequence.
     */
    @Test
    fun testDuplicateIngredients() = runBlocking {
        val vocab = IngredientVocabulary()
        val stage = SpecializedInterpretationStage(vocab)
        val input = RoutingResult(null, null, null, null, listOf("ingredients: sugar, salt, sugar, salt"))
        val context = SemanticRoutingContext(UUID.randomUUID(), 500, 500, OcrMetadata(ocrConfidence = 0.8f))

        val result = requireNotNull(stage.execute(input, context, ExecutionProfiler()).output).correction.output

        // Duplicates preserved in raw output list
        assertEquals(4, result.size)

        // Stage distinct check
        val deduplicated = result.distinctBy { it.canonical }
        assertEquals(2, deduplicated.size)
        assertEquals("sugar", deduplicated[0].canonical)
        assertEquals("salt", deduplicated[1].canonical)
    }

    /**
     * Verifies that the routing matrix maps raw image metadata (blur, light, density, dimensions)
     * to the correct image processing strategy (e.g. UPSCALE, TILED, Low Light).
     */
    @Test
    fun testOCRPipelineRouting() {
        val lowComplexityMetrics = OCRComplexityAnalyzer.AnalysisMetrics(
            brightness = 120f,
            contrast = 35f,
            blurScore = 15f,
            estimatedTextDensity = 0.05f,
            complexityRating = "LOW"
        )

        // 1. Tiny images upscale routing
        val upscaleStrategy = OCRPipelineRouter.route(100, 100, lowComplexityMetrics)
        assertEquals(OCRPipelineRouter.OcrStrategy.UPSCALE, upscaleStrategy)

        // 2. Wide aspect ratio tiled routing
        val tiledStrategy = OCRPipelineRouter.route(2000, 500, lowComplexityMetrics)
        assertEquals(OCRPipelineRouter.OcrStrategy.TILED, tiledStrategy)

        // 3. Blurry image routing
        val blurryMetrics = lowComplexityMetrics.copy(blurScore = 4.5f)
        val blurryStrategy = OCRPipelineRouter.route(640, 480, blurryMetrics)
        assertEquals(OCRPipelineRouter.OcrStrategy.SHARPENED, blurryStrategy)

        // 4. Low light image routing
        val lowLightMetrics = lowComplexityMetrics.copy(brightness = 75f)
        val lowLightStrategy = OCRPipelineRouter.route(640, 480, lowLightMetrics)
        assertEquals(OCRPipelineRouter.OcrStrategy.LOW_LIGHT, lowLightStrategy)

        // 5. Low contrast image routing
        val lowContrastMetrics = lowComplexityMetrics.copy(contrast = 18f)
        val lowContrastStrategy = OCRPipelineRouter.route(640, 480, lowContrastMetrics)
        assertEquals(OCRPipelineRouter.OcrStrategy.THRESHOLDED, lowContrastStrategy)

        // 6. Normal image routing
        val normalStrategy = OCRPipelineRouter.route(640, 480, lowComplexityMetrics)
        assertEquals(OCRPipelineRouter.OcrStrategy.STANDARD, normalStrategy)
    }

    @Test
    fun testFuzzyHeaderAndMixedDelimiters() {
        // Test fuzzy headers
        val input1 = "1ngred1ents: sugar, salt"
        val section1 = IngredientExtractor.extractRawSection(input1)
        assertEquals("sugar, salt", section1)

        val input2 = "OTHER INGREDlENTS; water, citric acid"
        val section2 = IngredientExtractor.extractRawSection(input2)
        assertEquals("water, citric acid", section2)

        val vocab = IngredientVocabulary().getVocabulary()

        // Test mixed delimiters with spaces
        val tokens = IngredientExtractor.tokenize("sugar salt, citric acid, msg", vocab)
        assertEquals(4, tokens.size)
        assertEquals("sugar", tokens[0])
        assertEquals("salt", tokens[1])
        assertEquals("citric acid", tokens[2])
        assertEquals("msg", tokens[3])

        // Verify multi-word preservation ("organic sugar" is not split, "salt" is separate)
        val tokensMultiWord = IngredientExtractor.tokenize("organic sugar, salt", vocab)
        assertEquals(2, tokensMultiWord.size)
        assertEquals("organic sugar", tokensMultiWord[0])
        assertEquals("salt", tokensMultiWord[1])

        // Verify mixed: multi-word preservation + merged word splitting
        val tokensMixed = IngredientExtractor.tokenize("whole wheat flour, sugar salt", vocab)
        assertEquals(3, tokensMixed.size)
        assertEquals("whole wheat flour", tokensMixed[0])
        assertEquals("sugar", tokensMixed[1])
        assertEquals("salt", tokensMixed[2])
    }
}
