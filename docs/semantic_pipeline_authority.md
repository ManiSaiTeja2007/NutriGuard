# NutriGuard - Semantic Pipeline Authority

This document defines the ownership boundaries, pipeline execution order, and integration interfaces for the Stage 11.3 Semantic Pipeline. 

---

## 1. Pipeline Execution Flow

The semantic reconstruction pipeline operates in **two passes** on a list of extracted and normalized ingredient tokens.

```
                  ┌────────────────────────────────────────┐
                  │          Input Token List              │
                  └──────────────────┬─────────────────────┘
                                     │
                                     ▼
                  ┌────────────────────────────────────────┐
                  │   Pre-Pass Context Extraction          │
                  │   - Categories & Keyword collection    │
                  └──────────────────┬─────────────────────┘
                                     │
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│ PASS 1: Single-Token Correction Pipeline                               │
│                                                                        │
│  [Stage 1: Normalization & Phrase Cleaning]                            │
│     OCR Token ──► Locale Normalization ──► PhraseNormalizer            │
│                                                                        │
│  [Stage 2: Deterministic Fast Paths]                                   │
│     IngredientOntology Hit? ─────────► [YES] ──► 1.0f (Direct Exit)    │
│            │ [NO]                                                      │
│     ENumberRepairEngine Repair? ─────► [YES] ──► 0.90f - 1.0f (Exit)   │
│            │ [NO]                                                      │
│     OCRConfusionResolver Match? ─────► [YES] ──► Calibrated Exit       │
│                                                                        │
│  [Stage 3: Fuzzy Expansion & Scoring]                                  │
│     Vocabulary Fuzzy Search ──► Levenshtein distance candidate expansion │
│                                    │                                   │
│                                    ▼                                   │
│                         ContextualSemanticScorer                       │
│                         - Category proximity & sequence bonus          │
│                                                                        │
│  [Stage 4: False-Correction Safeguards]                                │
│     Check Ambiguity & Confidence Thresholds                            │
│     - Below threshold? ────────► Preserve Raw Token (Rejection)        │
│     - Accept? ─────────────────► Corrected Token                       │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│ PASS 2: Contextual Disambiguation                                      │
│                                                                        │
│  [Stage 5: Neighbor-Aware Reconstruction]                              │
│     ContextualDisambiguator checks surrounding tokens (Window: ±3)     │
│     - Resolve ambiguity & correct context mismatches                   │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     ▼
                  ┌────────────────────────────────────────┐
                  │       Final Correction Results         │
                  └────────────────────────────────────────┘
```

---

## 2. Component Boundaries & Ownership

### 2.1 Phrase Cleaning (`PhraseNormalizer`)
- **Location**: [PhraseNormalizer.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/parsing/PhraseNormalizer.kt)
- **Responsibility**: Per-token cleaning. Handles removing trailing punctuation, parenthetical noise, and standardizing spaces for individual tokens.

### 2.2 Deterministic Ontology Resolver (`IngredientOntology`)
- **Location**: [IngredientOntology.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/ontology/IngredientOntology.kt)
- **Responsibility**: Direct vocabulary verification and category mapping. Maintains the core classification namespaces.
- **Rules**: Delegates abbreviation and alias repairs directly to the `AliasRepairEngine` to prevent redundant static mappings.

### 2.3 Additive Repair Engine (`ENumberRepairEngine`)
- **Location**: [ENumberRepairEngine.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/enumbers/ENumberRepairEngine.kt)
- **Responsibility**: Identifies, sanitizes, and canonicalizes E-number formats (e.g. `e330` -> `Citric Acid`).

### 2.4 OCR Confusion Resolver (`OCRConfusionResolver`)
- **Location**: [OCRConfusionResolver.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/ambiguity/OCRConfusionResolver.kt)
- **Responsibility**: Resolves common visual substitutions (e.g., zero `0` replacing letter `o`, or `1` replacing `l`) by checking a positional character confusion table.

### 2.5 Contextual Semantic Scorer (`ContextualSemanticScorer`)
- **Location**: [ContextualSemanticScorer.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/context/ContextualSemanticScorer.kt)
- **Responsibility**: Evaluates fuzzy candidates using contextual proximity scoring.
- **Rules**:
  - Dynamically loads rule weights from [context_scoring_rules.json](file:///d:/projects/Ongoing/nutriguard/app/src/main/assets/knowledge/context_scoring_rules.json).
  - Categorizes neighboring active tokens to apply:
    - `same_category_bonus` (e.g., neighbor acidity regulators).
    - `additive_neighbor_bonus` (e.g., neighbor E-numbers).
    - `ingredient_sequence_bonus` (generic sequence proximity).
  - **Explainability**: Returns a structured bonus score and clear textual reason for logging.

### 2.6 Multi-Pass Contextual Disambiguator (`ContextualDisambiguator`)
- **Location**: [ContextualDisambiguator.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/contextual/ContextualDisambiguator.kt)
- **Responsibility**: Second-pass correction. Uses a contextual window of `±3` tokens to resolve remaining semantic ambiguities (e.g., distinguishing multi-use ingredients based on context).

---

## 3. Strict Safety Safeguards & Rejections

To prevent semantic hallucination drift, the following constraints are strictly enforced in [OcrCorrectionEngine](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/correction/OcrCorrectionEngine.kt):

1. **Strict Confidence Boundary**:
   - Any token with a final confidence below the `minimumConfidenceThreshold` (e.g., `0.80f`) MUST be rejected and preserved in its raw form.
   - **Reason**: We prioritize preserving the original OCR text over producing a low-confidence false-positive correction.

2. **Ambiguity Prevention**:
   - If multiple candidates have similar Levenshtein distances (within a margin of `1`) and `allowAmbiguousCorrection` is disabled, correction is rejected. The raw token is preserved, and a warning is logged.

3. **No Artificial Promotion**:
   - Contextual bonuses from `ContextualSemanticScorer` may assist candidate ranking and recovery only. They **cannot** bypass the minimum base confidence check or force a `HIGH` confidence level artificially.
