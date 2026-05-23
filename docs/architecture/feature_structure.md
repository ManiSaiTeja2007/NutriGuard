# NutriGuard - Feature & Package Structure

This document details the modular package boundaries, component roles, and dependency layers established in the NutriGuard codebase.

## Package Architecture Diagram

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
 │    ├── components/   <-- Shared/Reusable engineering panels
 │    ├── features/     <-- Modular screen feature directories
 │    │    ├── home/    <-- Main dashboard entry point
 │    │    ├── scan/    <-- CameraX live scanning and test image ingestion
 │    │    ├── results/ <-- Visualizer for canonical mappings & latencies
 │    │    ├── debug/   <-- Local benchmarking and logged replay lists
 │    │    ├── replay/  <-- Interactive inspector for JSON replays
 │    │    └── settings <-- Switches for developers to toggle modes
 │    └── navigation/   <-- Custom state-based router (Screen, NavController)
 │
 └── utils/             <-- Shared helpers (e.g. asset loader and repository)
```

---

## Separation Boundaries

### 1. Presentation vs. Domain Core
- Classes inside the `core` package must remain pure Kotlin components and **MUST NOT** import Android UI, Jetpack Compose, or screen navigation elements.
- The `ui` features interact with `core` engines by instantiating them (e.g., `OcrPipeline`, `IngredientVocabulary`) or invoking pipeline stages within Coroutine scopes.

### 2. Feature Boundaries
- Screens under `ui/features/` should be self-contained:
  - `home` is the central menu.
  - `scan` is the data ingest interface.
  - `results` is the preview page.
  - `debug` is the developer profiling environment.
  - `replay` is the offline diagnostic viewer.
  - `settings` is the control panel.
- Route arguments are passed via sealed class models in `ui/navigation/Screen.kt` to ensure loose coupling.

### 3. Isolated Benchmarking
- In-app benchmarking performs timing calculations over seeded assets.
- Ground-truth evaluation tests remain inside the separate `/benchmark` directory (Python evaluation tools) to keep the app runtime lightweight.
- The Android application only imports the final results of these pipelines through serialized replays.
