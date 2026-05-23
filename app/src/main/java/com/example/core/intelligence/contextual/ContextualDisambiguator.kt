package com.example.core.intelligence.contextual

import com.example.core.intelligence.correction.FailureType
import java.util.Locale

/**
 * Lightweight rule-based contextual disambiguator.
 *
 * Applies deterministic context rules to ambiguous tokens. Rules are stored as a
 * data-driven map — not a chain of hardcoded if-else trees.
 *
 * Safety: max 8 rule lookups per token, no recursion.
 *
 * Supported rule patterns:
 *   - PRECEDING_COMPOUND: preceding token(s) form a compound with the ambiguous token
 *   - FOLLOWING_COMPOUND: following token(s) form a compound
 *   - CATEGORY_CONTEXT:   neighboring ontology category signals the resolution
 *
 * Emits [FailureType.CONTEXT_DISAMBIGUATION_FAILURE] when no rule resolves the ambiguity.
 */
object ContextualDisambiguator {

    data class DisambiguationRule(
        val triggerToken: String,
        val contextPattern: ContextPattern,
        val resolvedForm: String,
        val ruleId: String
    )

    sealed class ContextPattern {
        /** A preceding token that, when combined with the ambiguous token, forms a known compound */
        data class PrecedingCompound(val precedingToken: String) : ContextPattern()
        /** A following token that, when combined, forms a known compound */
        data class FollowingCompound(val followingToken: String) : ContextPattern()
        /** The presence of a specific ontology category in neighbors signals the resolution */
        data class CategoryContext(val requiredCategory: String) : ContextPattern()
    }

    data class DisambiguationResult(
        val resolvedForm: String?,
        val ruleId: String?,
        val failed: Boolean,
        val debugTrace: List<String>
    )

    /**
     * Deterministic rule table. Each entry defines:
     *  - which ambiguous token it targets
     *  - what context pattern triggers it
     *  - what canonical form it resolves to
     */
    private val rules: Map<String, List<DisambiguationRule>> = buildRules()

    private fun buildRules(): Map<String, List<DisambiguationRule>> {
        val ruleList = listOf(
            // "acid" disambiguation: preceding metal/compound token
            DisambiguationRule("acid", ContextPattern.PrecedingCompound("citric"), "citric acid", "acid_citric"),
            DisambiguationRule("acid", ContextPattern.PrecedingCompound("malic"), "malic acid", "acid_malic"),
            DisambiguationRule("acid", ContextPattern.PrecedingCompound("lactic"), "lactic acid", "acid_lactic"),
            DisambiguationRule("acid", ContextPattern.PrecedingCompound("tartaric"), "tartaric acid", "acid_tartaric"),
            DisambiguationRule("acid", ContextPattern.PrecedingCompound("acetic"), "acetic acid", "acid_acetic"),
            DisambiguationRule("acid", ContextPattern.PrecedingCompound("ascorbic"), "ascorbic acid", "acid_ascorbic"),
            DisambiguationRule("acid", ContextPattern.PrecedingCompound("phosphoric"), "phosphoric acid", "acid_phosphoric"),
            DisambiguationRule("acid", ContextPattern.CategoryContext("acidity_regulators"), "acidity regulator", "acid_category"),

            // "oil" disambiguation: preceding adjective
            DisambiguationRule("oil", ContextPattern.PrecedingCompound("vegetable"), "vegetable oil", "oil_veg"),
            DisambiguationRule("oil", ContextPattern.PrecedingCompound("palm"), "palm oil", "oil_palm"),
            DisambiguationRule("oil", ContextPattern.PrecedingCompound("canola"), "canola oil", "oil_canola"),
            DisambiguationRule("oil", ContextPattern.PrecedingCompound("soybean"), "soybean oil", "oil_soy"),
            DisambiguationRule("oil", ContextPattern.PrecedingCompound("sunflower"), "sunflower oil", "oil_sunflower"),
            DisambiguationRule("oil", ContextPattern.PrecedingCompound("coconut"), "coconut oil", "oil_coconut"),

            // "starch" disambiguation
            DisambiguationRule("starch", ContextPattern.PrecedingCompound("corn"), "corn starch", "starch_corn"),
            DisambiguationRule("starch", ContextPattern.PrecedingCompound("modified"), "modified starch", "starch_modified"),
            DisambiguationRule("starch", ContextPattern.PrecedingCompound("tapioca"), "tapioca starch", "starch_tapioca"),
            DisambiguationRule("starch", ContextPattern.PrecedingCompound("potato"), "potato starch", "starch_potato"),

            // "flavor" disambiguation
            DisambiguationRule("flavor", ContextPattern.PrecedingCompound("natural"), "natural flavor", "flavor_natural"),
            DisambiguationRule("flavor", ContextPattern.PrecedingCompound("artificial"), "artificial flavor", "flavor_artificial"),

            // "syrup" disambiguation
            DisambiguationRule("syrup", ContextPattern.PrecedingCompound("corn"), "corn syrup", "syrup_corn"),
            DisambiguationRule("syrup", ContextPattern.PrecedingCompound("malt"), "malt syrup", "syrup_malt"),

            // "extract" disambiguation
            DisambiguationRule("extract", ContextPattern.PrecedingCompound("yeast"), "yeast extract", "extract_yeast"),
            DisambiguationRule("extract", ContextPattern.PrecedingCompound("malt"), "malt extract", "extract_malt"),
            DisambiguationRule("extract", ContextPattern.PrecedingCompound("vanilla"), "vanilla extract", "extract_vanilla"),

            // "gum" disambiguation
            DisambiguationRule("gum", ContextPattern.PrecedingCompound("xanthan"), "xanthan gum", "gum_xanthan"),
            DisambiguationRule("gum", ContextPattern.PrecedingCompound("guar"), "guar gum", "gum_guar"),
            DisambiguationRule("gum", ContextPattern.PrecedingCompound("carob"), "carob bean gum", "gum_carob"),
            DisambiguationRule("gum", ContextPattern.PrecedingCompound("locust"), "locust bean gum", "gum_locust"),

            // "color" / "colour" disambiguation
            DisambiguationRule("color", ContextPattern.PrecedingCompound("caramel"), "caramel color", "color_caramel"),
            DisambiguationRule("colour", ContextPattern.PrecedingCompound("caramel"), "caramel color", "colour_caramel"),

            // "flour" disambiguation
            DisambiguationRule("flour", ContextPattern.PrecedingCompound("wheat"), "wheat flour", "flour_wheat"),
            DisambiguationRule("flour", ContextPattern.PrecedingCompound("rice"), "rice flour", "flour_rice"),
            DisambiguationRule("flour", ContextPattern.PrecedingCompound("oat"), "oat flour", "flour_oat"),
            DisambiguationRule("flour", ContextPattern.PrecedingCompound("enriched"), "enriched flour", "flour_enriched"),

            // "protein" disambiguation
            DisambiguationRule("protein", ContextPattern.PrecedingCompound("whey"), "whey protein", "protein_whey"),
            DisambiguationRule("protein", ContextPattern.PrecedingCompound("soy"), "soy protein", "protein_soy"),
            DisambiguationRule("protein", ContextPattern.PrecedingCompound("pea"), "pea protein", "protein_pea"),
            DisambiguationRule("protein", ContextPattern.PrecedingCompound("milk"), "milk protein", "protein_milk")
        )
        return ruleList.groupBy { it.triggerToken }
    }

    /**
     * Attempts to disambiguate [token] using the provided [context].
     *
     * @param token   The potentially ambiguous canonical token (lowercased).
     * @param context Neighboring token context window.
     * @return [DisambiguationResult] with resolved form, rule ID, and debug trace.
     */
    fun disambiguate(token: String, context: DisambiguationContext): DisambiguationResult {
        val clean = token.lowercase(Locale.ROOT).trim()
        val trace = mutableListOf<String>()
        trace.add("disambiguate: \"$clean\"")
        trace.add("  preceding: ${context.precedingTokens}")
        trace.add("  following: ${context.followingTokens}")

        val applicableRules = rules[clean]
        if (applicableRules == null) {
            trace.add("  no rules for \"$clean\" — pass-through")
            return DisambiguationResult(null, null, false, trace)
        }

        var ruleCount = 0
        for (rule in applicableRules) {
            if (ruleCount >= 8) break // safety: max 8 lookups per token
            ruleCount++

            val matches = when (val pattern = rule.contextPattern) {
                is ContextPattern.PrecedingCompound ->
                    context.precedingTokens.any { it.lowercase(Locale.ROOT) == pattern.precedingToken }
                is ContextPattern.FollowingCompound ->
                    context.followingTokens.any { it.lowercase(Locale.ROOT) == pattern.followingToken }
                is ContextPattern.CategoryContext ->
                    context.ontologyCategories.contains(pattern.requiredCategory)
            }

            if (matches) {
                trace.add("  rule fired: ${rule.ruleId} -> \"${rule.resolvedForm}\"")
                return DisambiguationResult(rule.resolvedForm, rule.ruleId, false, trace)
            }
        }

        // No rule resolved — emit failure but preserve original token
        trace.add("  no disambiguation rule matched for \"$clean\" — CONTEXT_DISAMBIGUATION_FAILURE")
        return DisambiguationResult(
            resolvedForm = null,
            ruleId = null,
            failed = true,
            debugTrace = trace
        )
    }
}
