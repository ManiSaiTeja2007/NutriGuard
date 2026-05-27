package com.example

import com.example.core.ingredient.*
import com.example.core.pipeline.SemanticPipeline
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.normalization.TextNormalizer
import com.example.core.ocr.routing.OCRPipelineRouter
import com.example.core.ocr.routing.OCRComplexityAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TextIntelligenceTest {

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

    @Test
    fun testCanonicalization() {
        assertEquals("salt", IngredientCanonicalizer.canonicalize("sodium chloride"))
        assertEquals("ascorbic acid", IngredientCanonicalizer.canonicalize("vitamin c"))
        assertEquals("monosodium glutamate", IngredientCanonicalizer.canonicalize("msg"))
        assertEquals("sugar", IngredientCanonicalizer.canonicalize("sugar")) // no-op
    }

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

    @Test
    fun testPipelineDeterminism() = runBlocking {
        val vocab = IngredientVocabulary()
        val pipeline = SemanticPipeline(vocab)
        val input = "ingredients: sugar, salt, msg, citric acid"

        val run1 = pipeline(Pair(input, OcrMetadata(ocrConfidence = 0.8f))).correction.output
        val run2 = pipeline(Pair(input, OcrMetadata(ocrConfidence = 0.8f))).correction.output

        assertEquals(run1.size, run2.size)
        run1.forEachIndexed { i, res ->
            assertEquals(res.originalToken, run2[i].originalToken)
            assertEquals(res.canonical, run2[i].canonical)
            assertEquals(res.confidence, run2[i].confidence, 0.001f)
            assertEquals(res.failures, run2[i].failures)
        }
    }

    @Test
    fun testCatastropheNoCommasSpacingRecovery() = runBlocking {
        val vocab = IngredientVocabulary()
        val pipeline = SemanticPipeline(vocab)

        // Input has NO commas, but has multi-word ingredients in the vocabulary (like "citric acid")
        val input = "ingredients: sugar salt citric acid msg"
        val result = pipeline(Pair(input, OcrMetadata(ocrConfidence = 0.8f))).correction.output

        val canonicals = result.map { it.canonical }

        // Extractor falls back to splitting by space and merging "citric" + "acid"
        assertTrue(canonicals.contains("sugar"))
        assertTrue(canonicals.contains("salt"))
        assertTrue(canonicals.contains("citric acid"))
        assertTrue(canonicals.contains("monosodium glutamate"))
    }

    @Test
    fun testCatastropheAllCapsAndNewlines() = runBlocking {
        val vocab = IngredientVocabulary()
        val pipeline = SemanticPipeline(vocab)

        val input = "INGREDIENTS: SUGAR,\nSALT,\nCITRIC ACID"
        val result = pipeline(Pair(input, OcrMetadata(ocrConfidence = 0.8f))).correction.output

        assertEquals(3, result.size)
        assertEquals("sugar", result[0].canonical)
        assertEquals("salt", result[1].canonical)
        assertEquals("citric acid", result[2].canonical)
    }

    @Test
    fun testCatastropheOCRMergedWords() {
        val vocab = IngredientVocabulary()
        val engine = com.example.core.intelligence.correction.OcrCorrectionEngine(vocab)

        // "sugarandsalt" remains raw due to low confidence / high edit distance
        val result = engine.correct(listOf("sugarandsalt"), 0.8f)
        assertEquals("sugarandsalt", result.first().canonical)
        assertTrue(result.first().failures.contains(com.example.core.intelligence.correction.FailureType.UNKNOWN_INGREDIENT_FAILURE))
    }

    @Test
    fun testCatastropheRepeatedCharacters() {
        val vocab = IngredientVocabulary()
        val engine = com.example.core.intelligence.correction.OcrCorrectionEngine(vocab)

        // "suuugar" -> fuzzy-matches to "sugar" (requires blurry profile context)
        val repeated = engine.correct(listOf("suuugar"), OcrMetadata(ocrConfidence = 0.8f, blurScore = 5.0f))
        assertEquals("sugar", repeated.first().canonical)
    }

    @Test
    fun testMalformedIngredients() = runBlocking {
        val vocab = IngredientVocabulary()
        val pipeline = SemanticPipeline(vocab)

        // Empty strings, trailing periods, unbalanced parenthesis
        val malformed = "ingredients: sugar, ..., salt., water (enriched"
        val result = pipeline(Pair(malformed, OcrMetadata(ocrConfidence = 0.8f))).correction.output

        val originals = result.map { it.originalToken }
        assertTrue(originals.contains("sugar"))
        assertTrue(originals.contains("salt"))
        assertTrue(originals.contains("water (enriched"))
        assertFalse(originals.contains("..."))
    }

    @Test
    fun testDuplicateIngredients() = runBlocking {
        val vocab = IngredientVocabulary()
        val pipeline = SemanticPipeline(vocab)

        val input = "ingredients: sugar, salt, sugar, salt"
        val result = pipeline(Pair(input, OcrMetadata(ocrConfidence = 0.8f))).correction.output

        // Duplicates preserved in raw output list
        assertEquals(4, result.size)

        // Stage distinct check
        val deduplicated = result.distinctBy { it.canonical }
        assertEquals(2, deduplicated.size)
        assertEquals("sugar", deduplicated[0].canonical)
        assertEquals("salt", deduplicated[1].canonical)
    }

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
}
