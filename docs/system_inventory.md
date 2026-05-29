# System Inventory Document

> [!NOTE]
> **Authority Boundary**: This document answers ONLY:
> * **What systems exist and who owns them?** (Subsystems catalog, physical file directory structure, package modularity boundaries)
>
> This document does NOT answer:
> * **What architecture exists?** (See [architecture_state.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture_state.md))
> * **What actually executes?** (See [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md))
> * **What is migrating?** (See [migration_tracker.md](file:///d:/projects/Ongoing/nutriguard/docs/migration_tracker.md))
> * **What has been verified?** (See [verification_status.md](file:///d:/projects/Ongoing/nutriguard/docs/verification_status.md))

This document provides a high-level catalog of all major subsystems within the NutriGuard platform, detailing package modularity, separation boundaries, directory layouts, and conceptual subsystem scopes.

---

## 1. Subsystem Catalog

| System | Code Owner | Authority Status | System Purpose |
| :--- | :--- | :--- | :--- |
| **OCR** | `com.example.core.ocr` | Authoritative | Reconstructs paragraphs and line segments from frame bitmaps (ML Kit Latin OCR). |
| **Preprocessing** | `com.example.core.ocr.preprocessing` | Authoritative | Applies image enhancement filters based on blur and light conditions. |
| **Dataset Governance** | `com.example.core.intelligence.vocabulary` | Authoritative | Enforces checksum boundaries on ingredient/additive vocabularies. |
| **Semantic Routing** | `com.example.core.pipeline.graph` | Transitional | Partitions layout text sections and routes text lines to domain-specific interpreters. |
| **Replay Infrastructure**| `com.example.core.replay` | Authoritative | Saves JSON execution snapshots to local cache directory for playback. |
| **Export System** | `com.example.core.export` | Authoritative | Generates signed ZIP archives containing capture frames, OCR texts, and metrics. |
| **Developer Tools** | `com.example.ui.features.debug` | Authoritative | Houses benchmark switches, overlays, and manual test image injection. |
| **Interpretation** | `com.example.core.intelligence` | Authoritative | Core intelligence mapping OCR corrected tokens to warning tags, categories, and E-numbers. |

---

## 2. Package Modularity Layout

The physical structure of the Kotlin source code is modularly grouped as follows:

```
com.example/
 ├── core/              <-- Ingestion and Processing Domain Layer (No UI dependencies)
 │    ├── frame/        <-- CameraX frame analysis validators
 │    ├── imaging/      <-- Image representations (bitmaps, frames, sources)
 │    ├── ingredient/   <-- Extraction, normalization pipelines, and vocabularies
 │    ├── matching/     <-- Alias resolver logic
 │    ├── normalization <-- Text cleaning algorithms
 │    ├── ocr/          <-- ML Kit OCR engine wrappers and result structures
 │    └── replay/       <-- Local serialization helpers for failed scans
 │
 ├── data/              <-- App state configurations (e.g. AppSettings)
 │
 ├── ui/                <-- Presentation Layer (Jetpack Compose)
 │    ├── components/   <-- Shared/Reusable UI layouts
 │    ├── features/     <-- Modular screen feature directories
 │    │    ├── home/    <-- Main dashboard entry point
 │    │    ├── scan/    <-- CameraX live scanning and test image ingestion
 │    │    ├── results/ <-- Visualizer for canonical mappings & latencies
 │    │    ├── debug/   <-- Local benchmarking and logged replay lists
 │    │    ├── replay/  <-- Interactive inspector for JSON replays
 │    │    └── settings <-- Switches for developers to toggle modes
 │    └── navigation/   <-- Custom state-based Compose router
 │
 └── utils/             <-- Shared helpers (e.g. asset loader and repository)
```

---

## 3. Separation Boundaries

1. **Domain Core vs. UI Layer**:
   - Subsystem folders under `com.example.core` represent domain processing logic and **MUST NOT** import Jetpack Compose, Compose views, screen navigation controllers, or Android UI classes.
   - The presentation layer `com.example.ui` interacts with domain core engines by instantiating them or launching execution graph pipelines inside Coroutine scopes.
2. **Feature Boundaries**:
   - Each view folder under `ui/features/` is isolated. Screen navigation parameters are passed strictly via route arguments defined in Compose sealed screen routes to prevent tight coupling.

---

## 4. Benchmarking Conceptual Boundaries

To prevent high resource overhead and execution coupling inside the Kotlin app, the benchmarking subsystem divides tasks cleanly:

* **Kotlin Runtime In-App Benchmarks**:
  - Measures execution timings (OCR, Preprocessing, Token Normalization, Alias Mappings) over local asset files to capture latency statistics.
  - *Does not perform scientific metric evaluations (CER, WER, F1) to conserve CPU performance.*
* **Python Scientific Evaluation Suite**:
  - Run offline inside the python environment `/benchmark` to compare exported JSON trace replays against ground-truth labels.
  - Computes scientific metrics:
    - **CER (Character Error Rate)**: character-level edit distance.
    - **WER (Word Error Rate)**: word-level edit distance.
    - **F1-Score**: harmonic mean of Precision and Recall for ingredient extraction and canonical mappings.

---

## 5. Directory Mappings & Active Source Files

- **OCR Preprocessing & Recognition Routing**:
  - Preprocessor: [OcrPreprocessor.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/ocr/preprocessing/OcrPreprocessor.kt)
  - Routing Strategy: [OCRPipelineRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/ocr/routing/OCRPipelineRouter.kt)
  - Complexity Check: [OCRComplexityAnalyzer.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/ocr/routing/OCRComplexityAnalyzer.kt)
- **Dataset Mappings**:
  - Verification test: [DatasetVerificationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/dataset/DatasetVerificationTest.kt)
  - Hash manifests: [dataset_versions.json](file:///d:/projects/Ongoing/nutriguard/benchmark/semantic/manifests/dataset_versions.json)
- **Execution Graph Abstractions**:
  - Graph folder: [graph/](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/)
  - Orchestrator: [SemanticExecutionGraph.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt)
- **Developer UI Views**:
  - Benchmark view: [DebugBenchmarkScreen.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/debug/DebugBenchmarkScreen.kt)
  - Replay view: [ReplayViewerScreen.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/replay/ReplayViewerScreen.kt)

---

## 6. Future Automation Placeholders

To transition the PSP to **Level 4 — Continuous Intelligence**, the following automated hooks are planned:

1. **Automatic `project_health.json` Generation**:
   - *Plan*: A post-build Gradle script will parse test task outputs and automatically write test metrics (`tests_passed`, `tests_failed`) to `project_health.json` to prevent manual count drift.
2. **Automatic Runtime Audit Generation**:
   - *Plan*: A custom compiler plugin will audit runtime execution paths of initialized ViewModels at compile time, automatically asserting the flow graph details in [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md).
3. **Automatic PSP Synchronization**:
   - *Plan*: A pre-commit git hook will compare metadata tables and dates across [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) and all `/docs/` tables, halting commits if synchronization requirements fail.
4. **Automatic Consistency Validation**:
   - *Plan*: A static analysis script will parse all markdown matrices to ensure vocabulary consistency.
5. **Automatic Verification Reporting**:
   - *Plan*: An instrumented test runner listener will automatically output the verification status matrix to [verification_status.md](file:///d:/projects/Ongoing/nutriguard/docs/verification_status.md) on every emulator test run.

