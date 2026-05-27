package com.example

import com.example.core.normalization.DefaultIngredientNormalizer
import com.example.core.aliases.AliasRepairEngine
import com.example.core.ambiguity.AmbiguityResolver
import com.example.core.additives.AdditiveResolver
import com.example.core.confidence.ConfidenceEvaluator
import com.example.core.confidence.ConfidenceBand
import com.example.core.intelligence.IngredientInterpreter
import com.example.core.intelligence.InterpretationFailure
import com.example.core.intelligence.ResolutionSource
import com.example.core.ontology.IngredientCategory
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.intelligence.correction.CorrectionResult
import com.example.core.intelligence.correction.FailureType
import org.junit.Assert.*
import org.junit.Test

class SemanticIntelligenceTest {

    @Test
    fun testDefaultIngredientNormalizer() {
        val norm1 = DefaultIngredientNormalizer.normalize("CitricAcid.")
        assertEquals("citricacid", norm1.normalizedText)

        val norm2 = DefaultIngredientNormalizer.normalize(" E 621  ")
        assertEquals("e621", norm2.normalizedText)

        val norm3 = DefaultIngredientNormalizer.normalize("E460(i)")
        assertEquals("e460(i)", norm3.normalizedText) // Parentheses preserved
    }

    @Test
    fun testAliasRepairEngine() {
        val repair1 = AliasRepairEngine.repair("msg")
        assertTrue(repair1.isRepaired)
        assertEquals("monosodium glutamate", repair1.repairedText)
        assertFalse(repair1.isTransliteration)

        val repair2 = AliasRepairEngine.repair("ins621")
        assertTrue(repair2.isRepaired)
        assertEquals("e621", repair2.repairedText)

        val repair3 = AliasRepairEngine.repair("veg oil")
        assertTrue(repair3.isRepaired)
        assertEquals("vegetable oil", repair3.repairedText)

        val repair4 = AliasRepairEngine.repair("haldi")
        assertTrue(repair4.isRepaired)
        assertEquals("turmeric", repair4.repairedText)
        assertTrue(repair4.isTransliteration)
    }

    @Test
    fun testAdditiveResolverTokenAware() {
        val res1 = AdditiveResolver.resolve("INS 500(ii)")
        assertNotNull(res1)
        assertEquals("sodium carbonates", res1?.canonicalName)

        val res2 = AdditiveResolver.resolve("INS 500 ( II )")
        assertNotNull(res2)
        assertEquals("sodium carbonates", res2?.canonicalName)

        val res3 = AdditiveResolver.resolve("ins500(ii)")
        assertNotNull(res3)
        assertEquals("sodium carbonates", res3?.canonicalName)

        val res4 = AdditiveResolver.resolve("E 330")
        assertNotNull(res4)
        assertEquals("citric acid", res4?.canonicalName)
    }

    @Test
    fun testAdditiveResolverStrictSafeguards() {
        // INS 50O(ii) contains letter O instead of zero -> must fail
        val resMalformed = AdditiveResolver.resolve("INS 50O(ii)")
        assertNull(resMalformed)
    }

    @Test
    fun testAmbiguityPreservation() {
        assertTrue(AmbiguityResolver.isAmbiguous("natural flavors"))
        assertTrue(AmbiguityResolver.isAmbiguous("spices"))
        assertTrue(AmbiguityResolver.isAmbiguous("vegetable oil"))
        assertTrue(AmbiguityResolver.isAmbiguous("masala"))
        assertTrue(AmbiguityResolver.isAmbiguous("spice mix"))
        assertTrue(AmbiguityResolver.isAmbiguous("permitted colors"))
        assertFalse(AmbiguityResolver.isAmbiguous("citric acid"))

        // Check that ambiguity remains UNKNOWN/UNCERTAIN in interpreter
        val masalaResult = IngredientInterpreter.interpret("masala", 0.95f)
        assertEquals(ConfidenceBand.UNCERTAIN, masalaResult.confidence)
        assertEquals(IngredientCategory.UNKNOWN, masalaResult.category)
        assertNull(masalaResult.canonicalName)
    }

    @Test
    fun testTransliterationConfidenceCapping() {
        // High confidence transliteration "haldi" must cap at MODERATE
        val res = IngredientInterpreter.interpret("haldi", 0.99f, "haldi")
        assertEquals("turmeric", res.canonicalName)
        assertEquals(ConfidenceBand.MODERATE, res.confidence)
        assertEquals(ResolutionSource.TRANSLITERATION, res.resolutionSource)
    }

    @Test
    fun testConfidenceEvaluator() {
        val high = ConfidenceEvaluator.assess(0.95f, "salt")
        assertEquals(ConfidenceBand.HIGH, high.band)
        assertFalse(high.isAmbiguous)

        val moderate = ConfidenceEvaluator.assess(0.75f, "salt")
        assertEquals(ConfidenceBand.MODERATE, moderate.band)
        assertTrue(moderate.isAmbiguous)

        val uncertain = ConfidenceEvaluator.assess(0.35f, "salt")
        assertEquals(ConfidenceBand.UNCERTAIN, uncertain.band)
        assertTrue(uncertain.isAmbiguous)
    }

    @Test
    fun testIngredientInterpreterSafeFallback() {
        // Test weak confidence fallback safeguard
        val weakResult = IngredientInterpreter.interpret("citric acid", 0.40f, "citricacd")
        assertEquals(ConfidenceBand.UNCERTAIN, weakResult.confidence)
        assertEquals(IngredientCategory.UNKNOWN, weakResult.category)
        assertNull(weakResult.canonicalName)
        assertTrue(weakResult.failures.contains(InterpretationFailure.LOW_CONFIDENCE_MATCH))

        // Test unknown ingredient fallback safeguard
        val unknownResult = IngredientInterpreter.interpret("xyzunknowningredient", 0.90f)
        assertEquals(ConfidenceBand.UNCERTAIN, unknownResult.confidence)
        assertEquals(IngredientCategory.UNKNOWN, unknownResult.category)
        assertNull(unknownResult.canonicalName)
        assertTrue(unknownResult.failures.contains(InterpretationFailure.UNKNOWN_INGREDIENT))
    }

    @Test
    fun testNegativeSemanticTests() {
        val cases = listOf("msgggg", "e99999", "randomnoise", "xylqz", "e62lll", "citricac", "turmric")
        for (token in cases) {
            val result = IngredientInterpreter.interpret(token, 0.95f)
            assertEquals(ConfidenceBand.UNCERTAIN, result.confidence)
            assertEquals(IngredientCategory.UNKNOWN, result.category)
            assertNull(result.canonicalName)
        }
    }

    @Test
    fun testIngredientInterpreterTraceAndWarnings() {
        // High confidence match
        val res = IngredientInterpreter.interpret("msg", 0.95f, "msg")
        assertEquals("monosodium glutamate", res.canonicalName)
        assertEquals(ConfidenceBand.HIGH, res.confidence)
        assertEquals(IngredientCategory.FLAVOUR_ENHANCER, res.category)
        assertEquals(ResolutionSource.ALIAS_MATCH, res.resolutionSource)
        
        // Assert conservative risk warnings are correct and lack alarmist words
        assertTrue(res.warnings.contains("commonly found in ultra-processed foods"))
        assertTrue(res.warnings.contains("contains artificial flavoring"))
        assertTrue(res.warnings.contains("high sodium content"))
        
        for (w in res.warnings) {
            assertFalse(w.contains("unsafe"))
            assertFalse(w.contains("dangerous"))
            assertFalse(w.contains("causes disease"))
            assertFalse(w.contains("toxic"))
        }

        // Trace evaluation
        val trace = res.trace
        assertNotNull(trace)
        assertEquals("msg", trace?.ocrText)
        assertEquals("msg", trace?.normalizedText)
        assertEquals("monosodium glutamate", trace?.aliasRepairedText)
        assertEquals("monosodium glutamate", trace?.ontologyMatchedName)
        assertEquals("HIGH", trace?.confidenceBand)
        assertTrue(trace?.finalInterpretation?.contains("flavour enhancer") == true)
        
        // Assert flowchart format has correct steps
        val flowchart = trace?.flowchart
        assertNotNull(flowchart)
        assertTrue(flowchart!!.contains("OCR:"))
        assertTrue(flowchart.contains("Normalization:"))
        assertTrue(flowchart.contains("Alias Repair:"))
        assertTrue(flowchart.contains("Additive Resolution:"))
        assertTrue(flowchart.contains("Confidence Calibration:"))
        assertTrue(flowchart.contains("Final Interpretation:"))
    }

    @Test
    fun testContextualSemanticScorerDirectly() {
        val scorer = com.example.core.intelligence.context.ContextualSemanticScorer
        val categories = setOf("acidity_regulator")
        val keywords = setOf("preservative")

        // 1. Same category bonus
        val res1 = scorer.scoreCandidate("citric acid", 0.65f, categories, emptySet())
        assertEquals(0.77f, res1.finalConfidence, 0.001f)
        assertEquals(0.12f, res1.bonusApplied, 0.001f)
        assertEquals("neighbor category: acidity_regulator", res1.reason)

        // 2. Keyword context bonus (using a preservative like sodium benzoate)
        val res2 = scorer.scoreCandidate("sodium benzoate", 0.65f, emptySet(), keywords)
        assertEquals(0.77f, res2.finalConfidence, 0.001f)
        assertEquals(0.12f, res2.bonusApplied, 0.001f)
        assertEquals("neighbor keyword match: preservative", res2.reason)

        // 3. Additive proximity bonus
        val res3 = scorer.scoreCandidate("e330", 0.65f, categories, emptySet())
        assertEquals(0.85f, res3.finalConfidence, 0.001f)
        assertEquals(0.20f, res3.bonusApplied, 0.001f) // category bonus (0.12) + additive proximity bonus (0.08)
    }

    @Test
    fun testOcrCorrectionEngineContextualScoring() {
        val vocab = IngredientVocabulary()
        val engine = com.example.core.intelligence.correction.OcrCorrectionEngine(vocab)
        val metadata = OcrMetadata(ocrConfidence = 0.70f)

        // No context: "citric ac" base confidence is 0.79f, below 0.80f threshold, so it fails to correct
        val noContextResult = engine.correct(listOf("citric ac"), metadata)
        assertEquals("citric ac", noContextResult.first().canonical)
        assertTrue(noContextResult.first().failures.contains(com.example.core.intelligence.correction.FailureType.FALSE_CORRECTION_RISK_FAILURE))

        // With context: "citric ac" near acidity regulator "e330" gets contextual category bonus (+0.12f), raising it to 0.91f >= 0.80f threshold
        val contextResult = engine.correct(listOf("e330", "citric ac"), metadata)
        val correctedCitric = contextResult.firstOrNull { it.originalToken == "citric ac" }
        assertNotNull(correctedCitric)
        assertEquals("citric acid", correctedCitric?.canonical)
        
        // Assert debugSteps contains explainable trace details
        val traceStr = correctedCitric?.debugSteps?.firstOrNull { it.startsWith("context bonus applied:") }
        assertNotNull(traceStr)
        assertTrue(traceStr!!.contains("baseConfidence="))
        assertTrue(traceStr.contains("bonus=0.12"))
        assertTrue(traceStr.contains("reason=neighbor category: acidity_regulator"))
    }
}
