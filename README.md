# NutriGuard — Edge AI Ingredient Intelligence Platform

NutriGuard is an offline-first Android OCR ingredient analysis platform focused on:

- ingredient label extraction
- adaptive OCR
- semantic ingredient correction
- additive recognition
- deterministic replay systems
- benchmark-driven evaluation
- production-grade OCR architecture

The project uses:
- CameraX
- Google ML Kit OCR
- Jetpack Compose
- Kotlin
- adaptive preprocessing
- semantic correction pipelines
- replay + benchmark infrastructure

---

# Current Architecture

NutriGuard now supports:

✅ Adaptive OCR pipelines  
✅ Image preprocessing  
✅ OCR overlays  
✅ Structured OCR reconstruction  
✅ Semantic correction  
✅ Replay infrastructure  
✅ Benchmark execution  
✅ Build separation  
✅ Developer tooling  
✅ Production UI separation  

The project now uses:

```text
One Shared OCR/Semantic Engine
Multiple Controlled Entry Points
````

---

# Build Variants

NutriGuard currently supports multiple build variants.

| Build Variant     | Purpose                               |
| ----------------- | ------------------------------------- |
| developerDebug    | OCR debugging, overlays, replay tools |
| benchmarkDebug    | dataset benchmarking + metrics        |
| internalRelease   | beta/internal testing                 |
| productionRelease | consumer-facing release               |

---

# Developer Build

Contains:

* OCR overlays
* preprocessing previews
* replay traces
* benchmark tools
* semantic debugging
* test image import

Install:

```powershell
.\gradlew.bat clean
.\gradlew.bat installDeveloperDebug
```

---

# Benchmark Build

Contains:

* dataset execution
* CER/WER evaluation
* replay export
* automated benchmarking

Install:

```powershell
.\gradlew.bat clean
.\gradlew.bat installBenchmarkDebug
```

---

# Internal Release Build

Contains:

* production-like UI
* lightweight diagnostics
* internal testing utilities

Install:

```powershell
.\gradlew.bat clean
.\gradlew.bat installInternalRelease
```

---

# Production Release Build

Contains:

* clean consumer UX
* ingredient understanding
* semantic correction
* simplified UI

No debugging overlays or benchmark tooling included.

Install:

```powershell
.\gradlew.bat clean
.\gradlew.bat installProductionRelease
```

---

# Running The Project

Open project in Android Studio.

Recommended:

* latest Android Studio
* Android SDK 35+
* Kotlin latest stable
* Gradle latest compatible

Then:

```powershell
.\gradlew.bat clean
.\gradlew.bat assembleDeveloperDebug
```

Run from Android Studio or install manually.

---

# Project Structure

```text
core/
 ├── ocr/
 ├── preprocessing/
 ├── semantic/
 ├── replay/
 ├── ontology/
 ├── benchmark/
 ├── pipeline/
 └── config/

features/
 ├── production/
 ├── developer/
 └── benchmark/

platform/
 ├── settings/
 ├── navigation/
 ├── health/
 ├── verification/
 └── ui/
```

---

# OCR Pipeline

Current OCR flow:

```text
Image
↓
Adaptive Preprocessing
↓
OCR
↓
Line Reconstruction
↓
Semantic Correction
↓
Canonicalization
↓
Replay + Metrics
↓
Result UI
```

---

# Benchmarking

NutriGuard supports deterministic benchmark execution.

Metrics include:

* CER
* WER
* semantic accuracy
* OCR latency
* preprocessing latency
* false correction rate

Benchmark datasets are NOT included in production builds.

---

# Replay System

Replay traces support:

* OCR inspection
* preprocessing inspection
* semantic correction tracing
* candidate ranking visibility
* debugging + regression validation

Replay systems are available only in:

* developerDebug
* benchmarkDebug

---

# Developer Features

Developer builds support:

* OCR overlays
* replay inspector
* preprocessing previews
* zoom + pan debugging
* semantic candidate tracing
* benchmark execution
* overlay diagnostics

---

# Production Philosophy

NutriGuard follows:

```text
Developer Complexity Internally
Consumer Simplicity Externally
```

Production users should see:

* simple scanning
* ingredient understanding
* clean explanations

NOT:

* OCR complexity
* debug overlays
* replay traces

---

# Important Engineering Principles

NutriGuard is designed to remain:

* deterministic
* replayable
* benchmarkable
* explainable
* offline-first

The project intentionally avoids:

* cloud dependency
* black-box AI
* opaque semantic reasoning

---

# Current Focus

Current development focuses on:

* semantic reliability
* OCR ambiguity handling
* replay infrastructure
* benchmark stability
* production UX
* deterministic evaluation

---

# Future Goals

Planned future directions include:

* ingredient education
* multilingual OCR
* additive intelligence
* hazard explanations
* scan history
* product understanding

These are intentionally staged gradually.

---

# License

Work in progress.

---

# Status

NutriGuard is currently in:
Stage 9+ Productization & Unified Pipeline Architecture
