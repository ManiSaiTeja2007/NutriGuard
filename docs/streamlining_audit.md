# Streamlining Audit — Stage 13.0D

This document records the file naming and ownership consolidation audit conducted for Stage 13.0D.

---

## 1. Naming Standardization Audit

We audited class names, filenames, and packages across the entire codebase to locate any references with suffixes like `Old`, `New`, `V2`, `Experimental`, or `Temp`.

### 1.1 Findings
- **Classes & Filenames**: Zero classes or source files containing `Old`, `New`, `V2`, or `Experimental` prefixes/suffixes exist in the repository. Filenames are standardized around their respective functional responsibilities (e.g. `PipelineRunner.kt`, `StructuralLayoutAnalyzer.kt`, `TargetedOcrCoordinator.kt`).
- **Temporary Files**: The prefix/suffix `temp` is used solely in `PipelineSnapshotRepository.kt` to designate session-scoped, short-lived storage files. This is functionally accurate and contains no structural naming debris.

---

## 2. File Ownership & Consolidation Matrix

Every major subsystem is classified under the Stage 13.0D convergence framework:

| Subsystem / Utility | Current Role | Target State | Classification | Rationale & Evidence |
| :--- | :--- | :--- | :---: | :--- |
| **`SemanticPipeline`** | Legacy sequential pipeline. | Ingested by `SpecializedInterpretationStage` in execution graph; acts as fallback rollback path. | `DEPRECATE` | Keep as a nested graph stage and backup switch. Cannot be deleted yet. |
| **`PipelineRunner`** | Orchestrator for the execution graph. | Primary, single authoritative ingestion runtime entry point. | `KEEP` | Authoritative. |
| **`SemanticRouter`** | Dispatches lines to domain interpreters. | Graph-based domain dispatcher. | `KEEP` | Authoritative. |
| **`IngredientInterpreter`** | Translates ingredients to categories and E-numbers. | Invoked in graph aggregation. | `KEEP` | Authoritative. |
| **`AllergenInterpreter`** | Evaluates allergen statements. | Ingested via routing. | `KEEP` | Authoritative. |
| **`NutritionInterpreter`** | Parses nutrition blocks. | Ingested via routing. | `KEEP` | Authoritative. |
| **`TextNormalizer`** | Sanitizes noisy OCR text. | Used by both legacy and graph stages. | `KEEP` | Authoritative. |
| **`ReplayStorageHelper`** | Serializes traces to JSON files. | Direct execution log writing. | `KEEP` | Authoritative. |
| **`OcrCameraFrameAnalyzer`** | Captures camera preview frames and runs OCR. | Bounding box preview feedback loop. | `KEEP` | Bypassed during final ingestion; decoupled from PipelineRunner. |
| **`OcrInputValidator`** | Filters out small/invalid bitmaps. | Validates cropped layout bitmaps. | `KEEP` | Authoritative. |

---

## 3. Runtime Protection Proof

Before any future deletion in Stage 13.0D.5 (Legacy Retirement), the following constraints must be checked:

- **Constraint 1 (Not Executing)**: System is verified as bypassed in ViewModel.
- **Constraint 2 (No References)**: Zero imports and invocations in compile-time checks.
- **Constraint 3 (No Test References)**: No test references.
- **Constraint 4 (No Replay References)**: No replay references.
- **Constraint 5 (No PSP References)**: Matrices verified.
- **Constraint 6 (Replacement Verified)**: Ground truth validation proves convergence.
