# Pipeline Ownership Matrix

This document maps code ownership, consumed inputs, produced outputs, structural dependencies, and downstream consumers for every major subsystem in the NutriGuard ingestion pipeline.

---

## 1. Subsystem Ownership & In/Out Contracts

| Component | Code Owner Package | Consumes (Inputs) | Produces (Outputs) | Dependencies | Downstream Consumers |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **OCRPipeline** | `com.example.core.ocr` | - `ImageFrame`<br>- Frame preprocessing parameters | - `OcrResult` (raw texts, confidence scores, bounds, blur/contrast scores) | - ML Kit TextRecognizer<br>- `OcrPreprocessor` | - `ScanViewModel`<br>- `TargetedOcrCoordinator` |
| **SemanticPipeline** | `com.example.core.ingredient` | - OCR text string<br>- `OcrMetadata` | - `IngredientIngestionResult` (normalized, extracted, corrected tokens) | - `IngredientVocabulary`<br>- `OcrCorrectionEngine` | - `ScanViewModel`<br>- `SpecializedInterpretationStage` |
| **PipelineRunner** | `com.example.core.pipeline` | - Raw Bitmap<br>- `PipelineConfig` | - `PipelineResult` (consolidated domain outputs & metrics) | - `SemanticExecutionGraph`<br>- `PipelineSnapshotRepository` | - `ScanViewModel` (Target Integration)<br>- `HeadlessPipelineTest` |
| **SemanticExecutionGraph** | `com.example.core.pipeline.graph` | - Raw Bitmap<br>- `OcrMetadata` | - `GraphResult` (executed stages list & unified outputs) | - All 9 graph execution stages | - `PipelineRunner` |
| **StructuralLayoutAnalyzer** | `com.example.core.pipeline.graph` | - Raw Bitmap | - `StageResult<PreprocessingProfile>` (contrast, blur, density zones) | - OpenCV-equivalent scaling / downsampling utils | - `TargetedOcrCoordinator` |
| **TargetedOcrCoordinator** | `com.example.core.pipeline.graph` | - Raw Bitmap<br>- Density zones | - `StageResult<OcrResult>` (character bounds within crop regions) | - `OCRPipeline` | - `SemanticSectionClassifier` |
| **SemanticSectionClassifier** | `com.example.core.pipeline.graph` | - `List<OCRLine>` (sorted bounds) | - `StageResult<List<ClassifiedSection>>` (Ingredients/Allergens groups) | - Keyword anchor rules | - `SemanticRouter` |
| **SemanticRouter** | `com.example.core.pipeline.graph` | - `List<ClassifiedSection>` | - `StageResult<RoutingResult>` (individual domain interpretations) | - `AllergenInterpreter`<br>- `NutritionInterpreter`<br>- `StorageInstructionInterpreter`<br>- `PackagingMetadataInterpreter` | - `SpecializedInterpretationStage` |
| **AllergenInterpreter** | `com.example.core.intelligence` | - `List<OCRLine>` (allergen section lines) | - `AllergenInterpretation` (badges, warnings) | - Allergen vocabulary files | - `SemanticRouter` |
| **NutritionInterpreter** | `com.example.core.intelligence` | - `List<OCRLine>` (nutrition section lines) | - `NutritionInterpretation` (macronutrients map) | - Regex quantity filters | - `SemanticRouter` |
| **StorageInstructionInterpreter**| `com.example.core.intelligence` | - `List<OCRLine>` (storage section lines) | - `StorageInterpretation` (temperature/cautions) | - Regex warning anchors | - `SemanticRouter` |
| **PackagingMetadataInterpreter** | `com.example.core.intelligence` | - `List<OCRLine>` (manufacturer section lines) | - `MetadataInterpretation` (distributors, batches) | - Keyword distributors regex | - `SemanticRouter` |
| **IngredientInterpreter** | `com.example.core.intelligence` | - Canonical name string<br>- Match confidence | - `InterpretedIngredient` (additive E-codes, categories, hazards) | - `IngredientVocabulary`<br>- `IngredientOntology` | - `ScanViewModel`<br>- `AggregationStage` |

---

## 2. Exposing Hidden Coupling & Duplications

1. **ScanViewModel and SemanticPipeline Coupling**:
   - `ScanViewModel` depends on both `OCRPipeline` and `SemanticPipeline` individually, repeating the sequence logic that is encapsulated by `PipelineRunner`. This creates a coupling where changes to the pipeline pipeline structure (e.g. adding a preprocessing filter or validation gate) requires manual adjustments inside `ScanViewModel.kt` instead of editing `PipelineRunner` configurations.
2. **IngredientInterpreter Loop Duplication**:
   - Both the manual ViewModel parser and the graph `AggregationStage` invoke `IngredientInterpreter.interpret`. When the graph path runs inside tests, this logic is executed inside `SpecializedInterpretationStage` and `AggregationStage`. In production, it is executed manually inside `ScanViewModel`.
3. **Replay Ingestion Coupling**:
   - In production runtime, replays are written inside `ScanViewModel.kt` via `ReplayStorageHelper` conditional blocks. In the test runtime, replays are generated in `ReplayGenerationStage` inside the graph. This duplicate path creates risk of mismatched JSON schema outputs.
