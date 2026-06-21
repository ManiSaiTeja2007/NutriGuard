# Architecture Simplification Audit — Stage 13.0D

This document records the architectural simplification audit of the NutriGuard project, highlighting oversized classes, mixed responsibilities, excessive coupling, and naming improvements.

---

## 1. Class & File Complexity Audit

We audited the codebase to locate files and classes that violate clean architecture principles (e.g. Single Responsibility Principle).

| File / Class | Complexity Findings | Recommendations |
| :--- | :--- | :--- |
| **`ScanViewModel.kt`** | - **Oversized**: 682 lines.<br>- **Mixed Responsibilities**: Manages camera preview frames, handles test asset file loading, runs ingestion pipelines, maps outputs to JSON, and maintains legacy rollback paths. | - Extract test image asset loader logic into a dedicated helper class.<br>- Remove legacy fallback code branch once the Stage 13.0D.5 retirement gate is passed, reducing the file size by ~200 lines. |
| **`ProjectHealthGenerator.kt`**| - **Oversized**: 352 lines.<br>- **Mixed Responsibilities**: Ingests XML test reports, parses JSON files, runs file integrity checks on datasets and failure files, and writes the health report. | - Separate XML test results parsing and corpus file validations into dedicated utility classes. |
| **`SpecializedInterpretationStage`**| - **Excessive Coupling**: Explicitly coupled to the deprecated `SemanticPipeline`. | - Replace this stage in future stages (e.g. Stage 14.0) with independent domain stages that parse text segments directly, breaking the dependency on the legacy pipeline. |
| **`TargetedOcrCoordinator.kt`**| - **High Complexity**: Manages layout coordinates cropping and multi-pass OCR binarization. | - Keep intact for now, but monitor for future decomposition if more preprocessing steps are added. |

---

## 2. Naming Audit & Standardization

We performed a scan across all classes and filenames in the codebase:
- **`Old` / `New` / `V2` / `Experimental` Suffixes**: Zero occurrences found in production source files. Names are clean and functional.
- **`Temp` Suffix**: Used in `PipelineSnapshotRepository.kt` to designate short-lived, session-scoped image cache files (e.g. `raw` and `prep` bitmaps before renaming them to the final `executionId`). This is appropriate and represents no structural debt.
- **Stage Name Suffixes**: No temporary stage suffix naming remains. All modules conform to the converged graph stage layout.
