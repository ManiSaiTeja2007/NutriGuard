# Architecture State Document

> [!NOTE]
> **Authority Boundary**: This document answers ONLY:
> * **What architecture exists?** (Authoritative, Transitional, and Deprecated systems inventory)
>
> This document does NOT answer:
> * **What actually executes?** (See [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md))
> * **What is migrating?** (See [migration_tracker.md](file:///d:/projects/Ongoing/nutriguard/docs/migration_tracker.md))
> * **What has been verified?** (See [verification_status.md](file:///d:/projects/Ongoing/nutriguard/docs/verification_status.md))

This document serves as the authoritative architecture inventory for the NutriGuard platform, detailing active, planned, and deprecated components.

---

## 1. System Authority Map

> [!NOTE]
> **Updated in Stage 13.0B (PSP Consistency Audit 2026-05-29)**: All execution graph components are now classified as `Active (Dual Validation)` — they execute in production alongside the legacy path under `FeatureFlags.useExecutionGraph = true`. Evidence: `ScanViewModel.kt` dual-execution block, `PipelineIntegrationSmokeTest.kt` passing.

| System | Authority Status | Lifecycle State | Runtime State Category | Notes |
| :--- | :--- | :---: | :--- | :--- |
| **PipelineRunner** | Authoritative | `WIRED_PROD` | Active (Dual Validation — Production + Dev + Tests) | Orchestrates the staged execution graph in parallel with the legacy path. [ScanViewModel.kt#L68](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L68) |
| **SemanticExecutionGraph** | Authoritative | `WIRED_PROD` | Active (Dual Validation — Production + Dev + Tests) | 9-stage pipeline: structural analysis → OCR → section classification → routing → interpretation → aggregation → calibration → replay. |
| **StructuralLayoutAnalyzer** | Authoritative | `WIRED_PROD` | Active (Dual Validation — Production + Dev + Tests) | Low-overhead layout partitioning analyzer. Stage 1 of execution graph. |
| **TargetedOcrCoordinator** | Authoritative | `WIRED_PROD` | Active (Dual Validation — Production + Dev + Tests) | Triggers OCR on targeted crop bitmaps. Stage 2 of execution graph. |
| **SemanticSectionClassifier** | Authoritative | `WIRED_PROD` | Active (Dual Validation — Production + Dev + Tests) | Segments OCR lines into Ingredients / Allergens / Nutrition / Storage sections. Stage 3 of execution graph. |
| **SemanticRouter** | Authoritative | `WIRED_PROD` | Active (Dual Validation — Production + Dev + Tests) | Routes classified sections to domain interpreters. Stage 4 of execution graph. |
| **AllergenInterpreter** | Authoritative | `WIRED_PROD` | Active (Dual Validation — Production + Dev + Tests) | Isolates allergen warnings and taxonomies. Invoked by SemanticRouter. |
| **NutritionInterpreter** | Authoritative | `WIRED_PROD` | Active (Dual Validation — Production + Dev + Tests) | Parses nutrition facts tables. Invoked by SemanticRouter. |
| **StorageInstructionInterpreter** | Authoritative | `WIRED_PROD` | Active (Dual Validation — Production + Dev + Tests) | Identifies storage and temperature criteria. Invoked by SemanticRouter. |
| **PackagingMetadataInterpreter** | Authoritative | `WIRED_PROD` | Active (Dual Validation — Production + Dev + Tests) | Extracts manufacturer details, distributor, and batch codes. Invoked by SemanticRouter. |
| **OcrCorrectionEngine** | Authoritative | `WIRED_PROD` | Active (Both paths) | Staged spelling engine correcting visual character noise. |
| **IngredientInterpreter** | Authoritative | `WIRED_PROD` | Active (Both paths) | Maps tokens to categories, additive E-numbers, and hazard warnings. |
| **DatasetVerification** | Authoritative | `VERIFIED_PROD` | Active (Build verification gate) | Verifies taxonomy SHA-256 integrity on startup. |
| **ReplayStorageHelper** | Authoritative | `VERIFIED_PROD` | Active (Dev/Benchmark + Execution Graph) | Serializes execution traces to JSON cache on error detection. |
| **SemanticPipeline** | Deprecated (Co-Authority) | `WIRED_PROD` | Active (Legacy Result A — co-authority under dual validation) | Legacy linear text parser. Retirement scheduled after full execution graph standalone validation. |
| **OcrCameraFrameAnalyzer** | Authoritative | `WIRED_PROD` | Active (Production UI runtime) | Captures camera frame buffers in live scan UI. |

---

## 2. Component Boundaries & Responsibilities

For the core semantic correction engine and tokenizer, files are bounded by these explicit responsibilities:

1. **Phrase Cleaning (`PhraseNormalizer`)**:
   - **Location**: [PhraseNormalizer.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/parsing/PhraseNormalizer.kt)
   - **Role**: Removes trailing punctuation and standardizes spacing for single words.
2. **Deterministic Ontology Resolver (`IngredientOntology`)**:
   - **Location**: [IngredientOntology.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/ontology/IngredientOntology.kt)
   - **Role**: Standard vocabulary checking and category lookup. Delegates alias remapping directly to `AliasRepairEngine` to prevent redundant static mapping declarations.
3. **Additive Repair Engine (`ENumberRepairEngine`)**:
   - **Location**: [ENumberRepairEngine.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/enumbers/ENumberRepairEngine.kt)
   - **Role**: Canonicalizes E-number formats (e.g. `e330` ➔ `Citric Acid`).
4. **OCR Confusion Resolver (`OCRConfusionResolver`)**:
   - **Location**: [OCRConfusionResolver.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/ambiguity/OCRConfusionResolver.kt)
   - **Role**: Performs visual character swap checking (e.g. `0` ➔ `o`).
5. **Contextual Semantic Scorer (`ContextualSemanticScorer`)**:
   - **Location**: [ContextualSemanticScorer.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/context/ContextualSemanticScorer.kt)
   - **Role**: Evaluates fuzzy candidates using proximity sequence bonuses loaded dynamically from config file.
6. **Multi-Pass Contextual Disambiguator (`ContextualDisambiguator`)**:
   - **Location**: [ContextualDisambiguator.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/contextual/ContextualDisambiguator.kt)
   - **Role**: Runs a neighbor-aware window (±3 tokens) to resolve domain semantic ambiguities.

---

## 3. Strict Safety Safeguards & Constraints

To prevent semantic hallucination and incorrect canonical mappings, the following rules are strictly enforced at the compiler/runtime level:

* **Strict Base Confidence Threshold**:
  - Any token with final corrected confidence below the minimum threshold (e.g. `0.80f`) MUST be rejected. The spelling engine preserves the raw OCR token rather than emitting a low-confidence guess.
* **Ambiguity Prevention**:
  - If multiple dictionary candidates result in equivalent minimum edit distance ranges (within a margin of `1`), correction is aborted and raw OCR text is preserved to block incorrect promotion.
* **Contextual Bonus Caps**:
  - Semantic boosts from neighbor proximity context serve only for tie-breaking. They **cannot** elevate a token's confidence above the base threshold if the base OCR character match is corrupt.
* **Depth Parenthesis Safety**:
  - Comma and semicolon splitting is ignored when processing contents nested inside parentheses `( )` or brackets `[ ]` to protect complex grouped items from token truncation.
* **Punctuation Trimming boundaries**:
  - Cleans parsed tokens by stripping only leading/trailing dots, colons, commas, and duplicate whitespace, retaining internal hyphen spaces.
