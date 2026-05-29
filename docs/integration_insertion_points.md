# Integration Insertion Points

This document defines the interface boundary coordinates for introducing future semantic components, preventing integration ambiguity during Stage 13 pipeline unification.

---

## 1. SectionClassifier (`SemanticSectionClassifier`)

- **Future Component Role**: Classifies raw OCR layout text lines into logical packaging categories (Ingredients, Allergens, Nutrition, Storage, etc.).
- **Insertion Location**: Wired inside the execution graph at:
  - [SemanticExecutionGraph.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt#L72)
- **Required Inputs**:
  - `context.targetedOcrLines: List<OCRLine>` (OCR lines sorted vertically by bounding box top coordinate).
- **Expected Outputs**:
  - `classifiedSections: List<ClassifiedSection>` added to the routing context, grouping lines by detected `SectionType` with source tags (`keyword_header` or `inline_marker`).
- **Dependencies**:
  - `OCRLine` data structures
  - `SectionType` and `ClassifiedSection` types
  - `detectHeaderType()` keyword lists

---

## 2. DomainRouter (`SemanticRouter`)

- **Future Component Role**: Evaluates the classified sections in the routing context and dispatches them to domain-specific interpreters.
- **Insertion Location**: Wired inside the execution graph at:
  - [SemanticExecutionGraph.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt#L78)
- **Required Inputs**:
  - `context.classifiedSections: List<ClassifiedSection>`
- **Expected Outputs**:
  - `RoutingResult` mapping interpreted domains (`allergenInterpretation`, `nutritionInterpretation`, `storageInterpretation`, `metadataInterpretation`) and a clean list of raw ingredient text blocks.
- **Dependencies**:
  - `AllergenInterpreter`, `NutritionInterpreter`, `StorageInstructionInterpreter`, and `PackagingMetadataInterpreter`.

---

## 3. AllergenInterpreter

- **Future Component Role**: Separates allergen lists, warnings, and warning badges from targeted section texts.
- **Insertion Location**: Invoked by the router inside:
  - [SemanticRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt#L41)
- **Required Inputs**:
  - `bodyLines: List<OCRLine>` corresponding to `SectionType.ALLERGENS` or `SectionType.WARNINGS`.
- **Expected Outputs**:
  - `AllergenInterpretation` containing parsed warnings, allergy advice badges, and confidence.
- **Dependencies**:
  - Allergen vocabulary matching tables (e.g. peanuts, tree nuts, gluten, dairy, soy).

---

## 4. NutritionInterpreter

- **Future Component Role**: Parses nutrition facts tables and converts them into structured macronutrient/micronutrient mappings.
- **Insertion Location**: Invoked by the router inside:
  - [SemanticRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt#L45)
- **Required Inputs**:
  - `bodyLines: List<OCRLine>` corresponding to `SectionType.NUTRITION`.
- **Expected Outputs**:
  - `NutritionInterpretation` containing parsed key value pairs (macronutrients, calories, and daily values).
- **Dependencies**:
  - Regular expressions for parsing unit matches (`g`, `mg`, `kcal`, `%`).

---

## 5. Packaging Intelligence (UI Integration Point)

- **Future Component Role**: Consumes the unified `PipelineResult` within the presentation layer, separating results into domain-specific visual cards.
- **Insertion Location**: Wired between the ViewModel and screen layouts at:
  - [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L521)
  - [ResultsScreen.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/results/ResultsScreen.kt)
- **Required Inputs**:
  - `executionId: String` (passed as a route navigation parameter).
- **Expected Outputs**:
  - A structured display of:
    - **Warnings Card**: Highlighting critical allergens.
    - **Ingredients Card**: Displaying canonical vocabulary corrections and E-number additives.
    - **Nutrition Facts Card**: Showing structured nutritional telemetry.
    - **Storage Card**: Highlighting storage/temperature warnings.
- **Dependencies**:
  - `PipelineSnapshotRepository` database retrieval functions
  - Multi-card Compose UI styling tokens
