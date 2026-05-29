# Section Classification & Routing Architecture

This document defines the layout recovery, classification, and routing mechanisms that transform raw food packaging text blocks into structured documents. 

---

## 1. Target End-to-End Runtime Flow

To resolve the legacy layout bypass, the pipeline transitions from flat sequential string parsing to a zoned, multi-stage document interpreter:

```text
  [CameraX Frame] / [Asset Image]
        │
        ▼
  [OCRPipeline.kt] (processes bitmap)
        │
        ▼
  [Layout Recovery] (groups visual text blocks using bounding box coordinates)
        │
        ▼
  [Section Detection] (identifies spatial separators, delimiters, and block headers)
        │
        ▼
  [Section Classification] (classifies raw text blocks into the 8 packaging domains)
        │
        ▼
  [Domain Routing] (SemanticRouter dispatches text blocks to their specific interpreters)
        │
        ├─► [SpecializedInterpretationStage] (fuzzy-corrects ingredients lists)
        ├─► [AllergenInterpreter] (extracts allergen badges and May Contain warning flags)
        ├─► [NutritionInterpreter] (extracts macronutrient lists)
        └─► [StorageInstructionInterpreter] (checks preservation guidelines)
        │
        ▼
  [Aggregation & Calibration] (consolidates outputs and runs contrast confidence corrections)
        │
        ▼
  [Compose UI] (renders cards for Ingredients, Allergen alerts, and Nutrition facts)
```

---

## 2. Why Allergen Notices Must Bypass IngredientInterpreter

Currently, the production runtime suffers from **domain contamination** because all text is fed into a single flat loop inside `IngredientInterpreter.kt`. 

### The Problem (Direct Contamination)
Let us analyze a real-world example from our failure logs:
* **Failure ID**: `FAIL-001` (Allergen statement parsed as ingredients)
* **Observed Text**: `"ALLERGY ADVICE: Contains Wheat, Soy. May contain milk."`
* **Flat Parsing Outcome**:
  1. The comma splitter splits tokens into: `"ALLERGY ADVICE: Contains Wheat"`, `"Soy. May contain milk"`.
  2. The normalizer strips punctuation resulting in: `"allergy advice contains wheat"`, `"soy may contain milk"`.
  3. The spelling corrector tries to map `"allergy advice contains wheat"` against vocabulary lists. Since it fails to match a single ingredient, it either falls back to a low-confidence raw token or incorrectly maps it to a nearby edit-distance ingredient.
  4. `"soy may contain milk"` is mapped as a single ingredient token, masking the fact that "milk" is a voluntary cross-contact trace risk rather than a direct recipe ingredient.

### The Solution (Domain Routing Bypass)
By introducing the `SectionClassifier` before the interpreter:
1. The block `"Contains: Wheat, Soy. May contain milk."` is identified as the `ALLERGENS` domain.
2. It bypasses `IngredientInterpreter` and is routed directly to `AllergenInterpreter`.
3. `AllergenInterpreter` parses the text using structured rules:
   - Identifies direct allergens: `Wheat`, `Soy`.
   - Identifies precautionary traces: `Milk`.
4. Renders these as high-visibility allergen warnings and discrete allergen badges in the UI, completely keeping them out of the ingredients list.

---

## 3. Stage 13.0 Success Criteria

The boundaries for Stage 13.0 completion are strictly defined as follows to prevent scope creep:

### Required (Must exist and be verified)
* **Packaging Taxonomy**: Standardized domain classification mappings ([packaging_taxonomy.md](file:///d:/projects/Ongoing/nutriguard/docs/packaging_taxonomy.md)).
* **Packaging Corpus**: Structured JSON evidence records for all 8 domains inside `benchmark/packaging_corpus/`.
* **Packaging Failure Corpus**: Initial category failure files under `benchmark/packaging_failures/`.
* **Section Classification Architecture**: Documented layout recovery, classification, and routing flow.
* **Runtime Convergence Plan**: Transition backlog and blockers mapped in the runtime audit.
* **PSP Synchronization**: All governance reports and snapshots generated and matching current code.

### Not Required Yet (Deferred to Stage 13.x implementation)
* **Production Runtime Integration**: Live wiring of `PipelineRunner` to `ScanViewModel.kt`.
* **Allergen Badge UI**: Jetpack Compose allergen overlays.
* **Packaging Segmentation Runtime**: Compiler plugins or model integrations for section classification.
* **Live Domain Routing**: Dynamic execution graph routing on CameraX streams.
