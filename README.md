# NutriGuard — Edge AI Deterministic Packaging Semantic Understanding Platform

# README Authority Statement
This document serves as Mission Control for the Project State Package (PSP). It summarizes project state and links to authoritative subsystem documents.

---

# PSP Metadata
* **PSP Version**: 1.0
* **Current Stage**: Stage 13.0E — Platform Hardening, Performance Optimization & UX Streamlining
* **Last PSP Sync**: 2026-05-30
* **Last Runtime Audit**: 2026-05-30
* **Documentation Status**: 🟢 GREEN
* **PSP Status**: 🟢 GREEN (Last audited 0 days ago; threshold: < 30 days)
* **PSP Maturity**: **Level 3 (Runtime Visibility)** (Target: Level 4 — Continuous Intelligence)


---


## README GOVERNANCE & Discrepancy Policy
> [!IMPORTANT]
> **README.md is the human dashboard and the official Single Source of Truth (SSOT)** for the entire NutriGuard project. It is a first-class engineering artifact, not optional documentation.
> - If code and the Project State Package (PSP) disagree regarding runtime wiring, authority, or stage status, the discrepancy must be investigated immediately.
> - Updates to the README and PSP are mandatory at the completion of every implementation phase, stage, refactor, dataset update, or runtime wiring change.

### Subsystem Verification Rule
> [!IMPORTANT]
> No subsystem or component may be marked GOOD, COMPLETE, VERIFIED, or PRODUCTION_READY in any Project State Package (PSP) document unless the following verification steps are successfully completed and documented:
> 1. **Compile verification passed** (`assembleDeveloperDebug` compiles cleanly).
> 2. **Unit tests passed** (JVM host tests compile and pass).
> 3. **Instrumentation tests passed** (AVD connected Android instrumentation tests pass).
> 4. **Runtime evidence captured** (A verifiable runtime trace exists in the [evidence log](file:///d:/projects/Ongoing/nutriguard/docs/runtime_evidence_log.md)).
> 5. **Walkthrough updated** (Walkthrough documentation updated with results and screenshots).

### Hierarchy of Truth
To prevent documentation overriding reality, the following hierarchy of authority is strictly enforced when discrepancies arise:
```text
Code > Runtime Audit > README.md > Other PSP Documents
```

### PSP Synchronization Requirements
A PSP document is considered synchronized only if:
* **Audit Date** is present and accurate.
* **PSP Version** metadata is present and valid.
* **Governance Matrix** matches current SSOT boundaries.
* **Runtime Findings** reflect actual code reality.
* **Verification Automation**: Whenever tests run, audits run, or dataset verification runs, all generated metrics must be refreshed using the dedicated governance pipeline task: `.\gradlew.bat pspRefresh`.
*Otherwise, the documentation status is declared **STALE**.*


### PSP Audit Quality Rule
To maintain engineering rigor, all PSP findings and progress metrics must be classified into one of the following standardized confidence levels before publication:
* **VERIFIED**: Confirmed directly from active source code analysis.
* **OBSERVED**: Confirmed through runtime execution verification.
* **ESTIMATED**: Derived from engineering judgment and timeline projections.
* **UNKNOWN**: Insufficient evidence to formulate a fact-based assessment.

### PSP Status (Staleness Detection)
The staleness of the PSP is evaluated based on the age of the last runtime audit:
* 🟢 **GREEN**: Last runtime audit performed < 30 days ago.
* 🟡 **YELLOW**: Last runtime audit performed 30–90 days ago.
* 🔴 **RED**: Last runtime audit performed > 90 days ago.

### PSP Maturity Model
The evolution of the Project State Package is evaluated against the following maturity levels:
* **Level 1 — Documentation**: Passive markdown files containing out-of-date systems logs.
* **Level 2 — Governance**: PSP checklist gated completion rules and Single Source of Truth matrices.
* **Level 3 — Runtime Visibility**: Standardized execution flows and bypass tracing mapped against code. (*Current*)
* **Level 4 — Continuous Intelligence**: Machine-readable health telemetry and automated consistency audits. (*Target*)

---


## PSP GOVERNANCE MATRIX

The table below defines the authoritative Single Source of Truth (SSOT) boundaries for every topic in the project state. Every PSP document answers exactly one primary question:

| Topic | Authority Document | Primary Question Answered |
| :--- | :--- | :--- |
| **Project & Stage Status** | [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) | Where are we, what is running, and what is next? (Mission Control) |
| **Architecture Inventory** | [architecture_state.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture_state.md) | What architecture exists? (Authoritative, Transitional, Deprecated) |
| **Runtime Execution** | [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md) | What actually executes? (Production, Developer, Test, Benchmark paths) |
| **Migration Progress** | [migration_tracker.md](file:///d:/projects/Ongoing/nutriguard/docs/migration_tracker.md) | What is migrating? (Progress, blockers, and targets) |
| **Verification Maturity** | [verification_status.md](file:///d:/projects/Ongoing/nutriguard/docs/verification_status.md) | What has been verified? (Unit, integration, and production status) |
| **Architectural Decisions** | [decision_log.md](file:///d:/projects/Ongoing/nutriguard/docs/decision_log.md) | Why was this decision made? (ADR entries history) |
| **Open Questions** | [open_questions.md](file:///d:/projects/Ongoing/nutriguard/docs/open_questions.md) | What remains unresolved? (Active registry of bottlenecks) |
| **Subsystems Catalog** | [system_inventory.md](file:///d:/projects/Ongoing/nutriguard/docs/system_inventory.md) | What systems exist and who owns them? |
| **Replay Specification** | [replay_system.md](file:///d:/projects/Ongoing/nutriguard/docs/replay_system.md) | How does the replay ingestion system work and what is its schema? |
| **Measurable State** | [project_health.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/project_health.json) | What is the current measurable project health state metrics? |

---


## PHASE COMPLETION REQUIREMENTS

No future development phase is considered complete until the following criteria are met:
- [x] **Code Complete** (Task implementation complete)
- [x] **Tests Pass** (All unit, integration, and instrumented tests pass successfully)
- [x] **Walkthrough Updated** (Walkthrough documentation updated with validation details)
- [x] **PSP Updated** (Root [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) and all PSP metadata updated)
- [x] **Runtime Audit Updated** (Execution flows verified against actual code)
- [x] **Migration Tracker Updated** (Migration progress percentages and blockers synchronized)
- [x] **Health Dashboard Updated** (Project Health and Execution Health dashboards updated)
- [x] **Verification Status Updated** (Verification status matrix synchronized)

---


## CURRENT STAGE DASHBOARD
 
| Metric | Details |
| :--- | :--- |
| **Current Stage** | **Stage 13.0D — Complete Runtime Integration, Convergence & Streamlining** |
| **Current Objective**| Converge all execution paths into a single authoritative runtime architecture (the staged execution graph managed by `PipelineRunner`), removing duplicate legacy processing and difference logging, while maintaining rollback safety and introducing the Stage 13.0D.5 Legacy Retirement Gate. |
| **Previous Stage** | **Stage 13.1 — Packaging Intelligence Validation** (COMPLETED) |
| **Next Stage** | **Stage 13.0D.5 — Legacy Retirement** |
 
### Stage 13.0D Exit Gate Checklist
* [x] **PipelineRunner Authoritative**: Unified runtime path is active for scan ingestion.
* [x] **SemanticExecutionGraph Authoritative**: Execution graph drives all 8 stages.
* [x] **SemanticRouter Authoritative**: Active for domain routing.
* [x] **Duplicate Runtime Outputs Removed**: Zero parallel run or redundant output mapping in ScanViewModel.
* [x] **Test Convergence Complete**: Redundant regression tests deleted/consolidated into DriftMetricsTest.
* [x] **File Ownership Matrix Complete**: Major files cataloged and classified under SSOT rules.
* [x] **Runtime Execution Proof Complete**: Call-chain verified with direct code evidence.
* [x] **Connected Android Tests Pass**: Verification test suites run and succeed on emulator.
* [x] **PSP Synchronized**: All documents updated and verification snapshots generated via `pspRefresh`.

---

## PSP FOUNDATION COMPLETION CHECKLIST
We have formally completed and closed the PSP Automation Foundation. All required deliverables are verified and in place:
* [x] **project_health.json**: Machine-readable health reports.
* [x] **psp_metrics.json**: Automated metadata metrics logs.
* [x] **runtime_audit_report.json**: Human-authored audited findings.
* [x] **snapshot_manifest.json**: Automated index for generated reports.
* [x] **README_STATE.md**: Automated executive one-page summary.
* [x] **docs/generated/**: Verification snapshot package directory.
* [x] **pspRefresh**: Automated Gradle validation and compilation task.
* [x] **PSP Governance**: Authoritative Single Source of Truth (SSOT) boundary rules.
* [x] **Runtime Audit**: Standardized execution flows and bypass mapping.
* [x] **Migration Tracking**: Milestone-based maturity models.
* [x] **Verification Tracking**: Pass matrices covering unit/integration/production.

**PSP Automation Foundation Status**: 🏁 **COMPLETE**

---

## PSP SNAPSHOT PACKAGE
* **Authoritative Source Folder**: [benchmark/reports/](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/) (contains latest raw output logs).
* **Review Snapshot Folder**: [docs/generated/](file:///d:/projects/Ongoing/nutriguard/docs/generated/) (contains copy snapshots with generated timestamps and source paths for single-folder upload and review).
* **Ownership**: Documents under `docs/generated/` are read-only machine-generated snapshots, while the root `README.md` and manual reports are strictly human-owned.

---

## STAGE 13 ACTIVE OBJECTIVES
To guide the next engineering focus, the following active objectives must be accomplished in Stage 13:
1. **Packaging Corpus**: Gather structured JSON evidence files for all 8 domains.
2. **Packaging Failure Corpus**: Map observed categorization failures to distinct failure logs.
3. **Packaging Taxonomy**: Finalize domain definitions and routing destinations.
4. **Section Classification Architecture**: Establish the zoned layout recovery pipelines.
5. **Domain Routing Architecture**: Detail bypass criteria for allergen statements.
6. **Runtime Convergence Backlog**: Audit ViewModel loops and list integration blocks.
7. **Packaging Intelligence Readiness**: Track component readiness levels objectively in the matrix.

---

## PROJECT HEALTH DASHBOARD

| Category | Status | Assessment |
| :--- | :--- | :--- |
| **OCR** | `VERIFIED_PROD` | High accuracy and robust multi-strategy selection via [OCRPipelineRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/ocr/routing/OCRPipelineRouter.kt). |
| **Preprocessing** | `VERIFIED_PROD` | Adaptive sharpening and low-light contrast adjustment working as expected. |
| **Dataset Governance** | `VERIFIED_PROD` | Authoritative checksum checks gated successfully under [DatasetVerificationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/dataset/DatasetVerificationTest.kt). |
| **Replay Infrastructure** | `VERIFIED_PROD` | PARITY JSON exports and consistency checks verified. |
| **Runtime Wiring** | `VERIFIED_PROD` | Production camera scanner executes dual execution graph with parallel validation comparisons. |
| **Section Classification**| `VERIFIED_PROD` | [SemanticSectionClassifier.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticSectionClassifier.kt) executed and validated in production dual validation mode. |
| **Semantic Routing** | `VERIFIED_PROD` | [SemanticRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt) executed and validated in production dual validation mode. |
| **Allergen Handling** | `VERIFIED_PROD` | [AllergenInterpreter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/AllergenInterpreter.kt) executed and validated in production dual validation mode. |
| **Production Semantic Quality**| `VERIFIED_PROD` | Parallel validation logging differences to prepare for final migration. |

---

## EXECUTION HEALTH DASHBOARD

| Category | Status |
| :--- | :---: |
| **OCR** | `VERIFIED_PROD` |
| **Preprocessing** | `VERIFIED_PROD` |
| **Dataset Governance** | `VERIFIED_PROD` |
| **Replay Infrastructure** | `VERIFIED_PROD` |
| **Runtime Wiring** | `VERIFIED_PROD` |
| **Semantic Routing** | `VERIFIED_PROD` |
| **Allergen Handling** | `VERIFIED_PROD` |
| **Production Semantic Quality** | `VERIFIED_PROD` |

### Objective Health Criteria
* **EXISTS**: Subsystem exists in codebase.
* **COMPILES**: Subsystem compiles without errors.
* **TESTED**: Subsystem is unit and integration tested.
* **WIRED_DEV**: Subsystem is wired in developer debug runtime.
* **WIRED_PROD**: Subsystem is wired in production runtime (e.g. parallel validation).
* **VERIFIED_PROD**: Subsystem is fully verified in production and stable (verified > claimed, evidence > assumption).


## RUNTIME REALITY MATRIX

| Component | Exists | Tested | Developer Runtime | Production Runtime | Authority Status |
| :--- | :---: | :---: | :---: | :---: | :--- |
| [SemanticExecutionGraph](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt) | Yes | Yes | Yes (Dual Validation) | Yes (Dual Validation) | Co-Authority (Parallel Execution) |
| [SemanticRouter](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt) | Yes | Yes | Yes (Dual Validation) | Yes (Dual Validation) | Co-Authority |
| [SemanticSectionClassifier](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticSectionClassifier.kt) | Yes | Yes | Yes (Dual Validation) | Yes (Dual Validation) | Co-Authority |
| [AllergenInterpreter](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/AllergenInterpreter.kt) | Yes | Yes | Yes (Dual Validation) | Yes (Dual Validation) | Co-Authority |
| [NutritionInterpreter](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/NutritionInterpreter.kt) | Yes | Yes | Yes (Dual Validation) | Yes (Dual Validation) | Co-Authority |
| [OcrCorrectionEngine](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/correction/OcrCorrectionEngine.kt) | Yes | Yes | Yes | Yes | Current Authority |
| [LegacySemanticPipeline](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/SemanticPipeline.kt) | Yes | Yes | Yes | Yes | Current Authority |

---

## PIPELINE AUTHORITY MATRIX

| Pipeline | Authoritative | Runtime Wired | Tested | Deprecated |
| :--- | :---: | :---: | :---: | :---: |
| **Legacy Semantic Pipeline** | Yes | Yes | Yes | **Planned** (Retire in Stage 13) |
| **Semantic Execution Graph** | **Co-Authoritative** | Yes (Dual Validation) | Yes | No |
| **Dataset Verification Pipeline** | Yes | Yes | Yes | No |
| **Replay Pipeline** | Yes | Yes | Yes | No |
| **Export Pipeline** | Yes | Yes | Yes | No |

---

## RUNTIME WIRING MATRIX

> [!NOTE]
> Updated in Stage 13.0B. `SemanticExecutionGraph` and all downstream graph components now execute in **Parallel Validation Mode** in production. Both legacy path (Result A) and execution graph path (Result B) run concurrently. Evidence: `FeatureFlags.useExecutionGraph = true` (hardcoded), `ScanViewModel.kt` dual-execution block. See [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md).

| Layer | Developer Build | Production Build | Tests | Benchmarks |
| :--- | :---: | :---: | :---: | :---: |
| [SemanticExecutionGraph](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt) | Yes | **Yes (Dual Validation)** | Yes | Yes |
| [PipelineRunner](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/PipelineRunner.kt) | Yes | **Yes (Dual Validation)** | Yes | Yes |
| [SemanticRouter](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt) | Yes | **Yes (Dual Validation)** | Yes | Yes |
| [SemanticSectionClassifier](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticSectionClassifier.kt) | Yes | **Yes (Dual Validation)** | Yes | Yes |
| [AllergenInterpreter](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/AllergenInterpreter.kt) | Yes | **Yes (Dual Validation)** | Yes | Yes |
| [NutritionInterpreter](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/NutritionInterpreter.kt) | Yes | **Yes (Dual Validation)** | Yes | Yes |
| [LegacySemanticPipeline](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/SemanticPipeline.kt) | Yes | Yes (Legacy Result A) | Yes | Yes (Legacy benchmarks) |

---

## RUNTIME MIGRATION TRACKER

> [!NOTE]
> This table was updated from Stage 13.0B values to Stage 13.1 actuals after validation suite completion (2026-05-29). Authoritative migration state is computed by `ProjectHealthGenerator.kt` from `migration_state.json`. For full details, see [migration_tracker.md](file:///d:/projects/Ongoing/nutriguard/docs/migration_tracker.md).

| Component | Lifecycle State | Migration % | Confidence | Remaining Work |
| :--- | :---: | :---: | :---: | :--- |
| **SemanticExecutionGraph** | `VERIFIED_PROD` | **100%** | **VERIFIED** | Legacy co-authority retirement; standalone production cutover in Stage 13.2 |
| **PipelineRunner** | `VERIFIED_PROD` | **100%** | **VERIFIED** | Legacy co-authority retirement; standalone production cutover in Stage 13.2 |
| **SemanticSectionClassifier** | `VERIFIED_PROD` | **100%** | **VERIFIED** | Multi-lingual classifier expansion (Stage 13.2 objective) |
| **SemanticRouter** | `VERIFIED_PROD` | **100%** | **VERIFIED** | Domain routing expansion and edge case corpus (Stage 13.2 objective) |
| **AllergenInterpreter** | `VERIFIED_PROD` | **100%** | **VERIFIED** | ResultsScreen.kt allergen badge UI; multi-lingual expansion |
| **NutritionInterpreter** | `VERIFIED_PROD` | **100%** | **VERIFIED** | ResultsScreen.kt nutrition facts display |
| **StructuralLayoutAnalyzer** | `VERIFIED_PROD` | **100%** | **VERIFIED** | Coordinate mapping under high camera frame rate |
| **TargetedOcrCoordinator** | `VERIFIED_PROD` | **100%** | **VERIFIED** | Crop rendering latency bounds under high frame rate |


### Migration Progress Methodology
To ensure migration percentages are reproducible and objective, progress is scored using the following cumulative gates:
* **Subsystem Exists & Compiles**: +15%
* **Unit Tested**: +10%
* **Integration Tested (headless emulator/benchmarks)**: +10% (or +5% if basic checks only)
* **Developer UI Wired (Debug tools/manual ingest)**: +15% (or +5% if partial)
* **Production UI Wired (Live CameraX feed)**: +50%

---

## EXAMPLE RUNTIME TRACES

### CURRENT PRODUCTION TRACE (Legacy Flow)
This path executes when running live camera captures in the production UI today:
```text
Image
  ↓
[ScanViewModel.kt] (invokes ocrPipeline on full frame)
  ↓
[SemanticPipeline.kt] (legacy pipeline runs directly on raw OCR string)
  ├─► [NormalizationStage] (standardizes spaces/delimiters)
  ├─► [ExtractionStage] (tokenizes flat list)
  ├─► [GroupingStage] (parses parenthesized brackets)
  ├─► [PhraseCorrectionStage] (joins multi-word terms)
  └─► [CorrectionStage] (evaluates spelling corrections on OcrCorrectionEngine)
  ↓
[IngredientInterpreter.kt] (evaluates category warnings item-by-item in loop)
  ↓
[ResultsScreen.kt] (renders raw categorized output, brand names are mixed as ingredients)
```

### EXPECTED FUTURE TRACE (Execution Graph Flow)
This path executes under headless instrumented tests and will be wired into production in Stage 13:
```text
Image
  ↓
[PipelineRunner.kt] (instantiates execution graph)
  ↓
[StructuralLayoutAnalyzer.kt] (detects high, medium, and low priority zones)
  ↓
[TargetedOcrCoordinator.kt] (crops bitmap by layout zones; runs OCR only on target regions)
  ↓
[SemanticSectionClassifier.kt] (groups lines into Ingredients, Allergens, or Storage sections)
  ↓
[SemanticRouter.kt] (routes section lines to AllergenInterpreter, NutritionInterpreter, etc.)
  ↓
[SpecializedInterpretationStage.kt] (runs SemanticPipeline on isolated ingredients block)
  ↓
[ContextualReconstructionStage.kt] (boosts confidence for neighbors based on ontology distance)
  ↓
[AggregationStage.kt] (merges interpretations, allergens, and nutrition structures)
  ↓
[ConfidenceCalibrationStage.kt] (runs calibration profile based on contrast/blur)
  ↓
[ResultsScreen.kt] (displays domain-isolated allergens alerts, nutrition lists, and ingredients)
```

---

## ARCHITECTURE AUTHORITY

> [!NOTE]
> Updated in Stage 13.0B (PSP Consistency Audit 2026-05-29). All execution graph components are now wired in **Parallel Validation Mode** in production. No components remain in pure test-only / awaiting-wiring state. See [architecture_state.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture_state.md) for the full inventory.

### Authoritative — Active in Dual Validation (Production + Dev + Tests)
- **PipelineRunner**: Production orchestrator. `FeatureFlags.useExecutionGraph = true` ensures it always executes via `ScanViewModel.kt` dual-execution block.
- **SemanticExecutionGraph**: Executes 9-stage layout pipeline (structural analysis → targeted OCR → section classification → routing → interpretation → aggregation → calibration → replay).
- **SemanticSectionClassifier**: Groups OCR lines into Ingredients / Allergens / Nutrition / Storage / Marketing sections.
- **SemanticRouter**: Dispatches classified sections to domain-specific interpreters (AllergenInterpreter, NutritionInterpreter, StorageInstructionInterpreter, PackagingMetadataInterpreter).
- **AllergenInterpreter / NutritionInterpreter**: Domain interpreters active under dual-validation routing.
- **Dataset Verification**: Enforces file SHA256 hashes and blocks mock drift under [DatasetVerificationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/dataset/DatasetVerificationTest.kt).
- **Replay Infrastructure**: Manages execution captures and parity checks via [ReplayStorageHelper.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/replay/ReplayStorageHelper.kt).

### Transitional — Scheduled for Legacy Retirement (Stage 13.1+)
- **LegacySemanticPipeline**: Still executes as co-authority (Result A) in parallel validation mode. Retirement scheduled after single-path execution graph is fully validated.

### Deprecated (Pending Removal Post Retirement)
- **Legacy Routing Paths**: Direct extraction of warnings or allergens inside the main ingredients tokenizer block. Superseded by `SemanticRouter`.
- **Manual IngredientInterpreter Loop in ScanViewModel**: Manual loop calling `IngredientInterpreter.interpret(...)` per token. Superseded by `AggregationStage` inside the execution graph.

---

## OPEN QUESTIONS

We track unresolved architectural questions to guide future development iterations:
1. ~~**Is SemanticExecutionGraph ready for production migration?**~~ ✅ **RESOLVED in Stage 13.0B**: Wired in dual-validation mode. `FeatureFlags.useExecutionGraph = true`. See [runtime_evidence_log.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_evidence_log.md) Claim 1.
2. **Is SemanticRouter fully verified under high contrast variance?**
   *Context*: Bounding box coordinates returned by crop zones might shift slightly under extreme brightness scales, which could corrupt classified lines in the classifier. Active research item for Stage 13.1.
3. **Are AllergenInterpreter outputs validated against real packaging?**
   *Context*: Current allergen dictionary covers 17 common allergens. Multi-lingual label expansion is a Stage 13.2 objective alongside Packaging Corpus expansion.
4. ~~**Which remaining runtime paths bypass PipelineRunner?**~~ ✅ **RESOLVED in Stage 13.0B**: None bypass PipelineRunner when `useExecutionGraph = true`. Rollback to legacy-only by setting flag to `false`. See [runtime_evidence_log.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_evidence_log.md) Claim 3.

---

## LAST VERIFIED AGAINST CODE

* **Audit Date**: 2026-05-29
* **Verification Status**: PASS
* **Verified Commit**: `dc44542b97a95bedc53213352820f35c131f50f2`
* **Verified Files**:
  - [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) (Live UI ingestion coordinator)
  - [PipelineRunner.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/PipelineRunner.kt) (Execution graph orchestrator wrapper)
  - [SemanticExecutionGraph.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt) (Staged processing layout graph)
  - [SemanticPipeline.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/SemanticPipeline.kt) (Legacy linear semantic pipeline)

### Verification Scope
* **Verified**:
  - ✓ **Runtime Wiring**: Production code references check.
  - ✓ **PSP Consistency**: Cross-file status validation.
  - ✓ **Migration State**: Blocker verification.
  - ✓ **Stage Dashboard**: Stage 12.5 timeline checks.
  - ✓ **Governance Matrix**: Master ownership validation.
* **Not Verified**:
  - ✗ **Real Packaging Accuracy**: Visual label correctness sweep.
  - ✗ **Production Semantic Quality**: Field-testing on physical packaging.

### Finding Confidence & Evidence Matrix
Every finding listed below follows the standardized confidence classification (VERIFIED, OBSERVED, ESTIMATED, UNKNOWN):

| Finding | Confidence | Traceable Evidence Links |
| :--- | :---: | :--- |
| **Production runtime uses Legacy SemanticPipeline** | **VERIFIED** | [ScanViewModel.kt:L64](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L64) and [ScanViewModel.kt:L233](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L233) |
| **ScanViewModel bypasses PipelineRunner** | **VERIFIED** | [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) (Grep check: zero imports of `PipelineRunner` or `SemanticExecutionGraph`) |
| **SemanticRouter is inactive in production UI** | **VERIFIED** | [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) and [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md) |
| **Bounding box coordinates translation is a blocker** | **ESTIMATED** | [migration_tracker.md](file:///d:/projects/Ongoing/nutriguard/docs/migration_tracker.md) and [open_questions.md:EQ-002](file:///d:/projects/Ongoing/nutriguard/docs/open_questions.md) |
| **Runtime migration completion progress is 27%** | **ESTIMATED** | Calculated using cumulative scoring gates in [migration_tracker.md](file:///d:/projects/Ongoing/nutriguard/docs/migration_tracker.md) |
| **Dataset Governance gated successfully** | **VERIFIED** | [DatasetVerificationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/dataset/DatasetVerificationTest.kt) check |

## GENERATED METRICS

To maintain transparency, ensure facts are derived from evidence, and prevent manual dashboard drift, the following artifacts are generated or validated automatically by the governance verification pipeline (`.\gradlew.bat pspRefresh`):

### Machine-Generated (ReadOnly)
* **[project_health.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/project_health.json)**: Carries runtime consistency states, overall unit test pass rates, dataset calibration statuses, and synchronizations.
* **[psp_metrics.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/psp_metrics.json)**: Holds schema-versioned build metadata, timestamp synchronization markers, and validation counts.

### Semi-Human (Validated)
* **[runtime_audit_report.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/runtime_audit_report.json)**: Human-authored audit log of verified findings and references. Validated at compile time for correct schemas, status labels, and active file references.
* **[migration_state.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/migration_state.json)**: Human-maintained component milestone assignments, compiled by the pipeline to compute exact migration progress.

### Human-Maintained Governance
* All other files, including this **README.md**, the **decision_log.md**, and **open_questions.md**, are strictly human-authored and human-reviewed to prevent git noise, conflict drift, and automated document generation errors.

---

## SECTION 1 — PROJECT OVERVIEW

NutriGuard is an offline-first Edge AI Android application.
It is actively evolving from a basic OCR text extraction application to an authoritative, deterministic packaging semantic understanding platform.

- **Project Purpose**: To analyze food ingredient labels locally, identifying and interpreting ingredients, additives, allergens, nutrition facts, storage instructions, and packaging metadata.
- **Local-First & Offline-First Philosophy**: All operations occur entirely on-device to ensure user privacy and offline availability. It runs without internet access by relying on local ML models (Google ML Kit OCR), an embedded SQLite database/local mappings, and a deterministic offline translation/spelling correction engine.
- **Build Variants**: Distinct developer, benchmark, internal, and production configurations compiled via product flavors.

---

## SECTION 2 — CURRENT ARCHITECTURE

The NutriGuard architecture consists of an end-to-end flow from image acquisition to structured UI results rendering:

### Stage Details

1. **Capture**
   - *Purpose*: Stream live camera preview frames or load static test images from assets.
   - *Inputs*: Camera feed frame data or test image byte streams.
   - *Outputs*: [ImageFrame](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/imaging/ImageFrame.kt) carrying bitmap pixels.
   - *Owning Files*: [ScanScreen.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/scan/ScanScreen.kt), [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt).

2. **Preprocessing**
   - *Purpose*: Apply environment-adaptive image filters (sharpening, contrast, thresholding, tiling) to maximize character recognition clarity.
   - *Inputs*: Raw `Bitmap`.
   - *Outputs*: Processed `Bitmap` optimized for OCR.
   - *Owning Files*: [OcrPreprocessor.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/ocr/preprocessing/OcrPreprocessor.kt), [OCRComplexityAnalyzer.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/ocr/routing/OCRComplexityAnalyzer.kt), [OCRPipelineRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/ocr/routing/OCRPipelineRouter.kt).

3. **OCR (Optical Character Recognition)**
   - *Purpose*: Run character recognition models to extract words, lines, bounding boxes, and confidence statistics.
   - *Inputs*: Processed `Bitmap` (full image or cropped layout zones).
   - *Outputs*: [OcrResult](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/ocr/OcrResult.kt) containing reconstructed line segments.
   - *Owning Files*: [OCRPipeline.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/OCRPipeline.kt), [TargetedOcrCoordinator.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/TargetedOcrCoordinator.kt).

4. **Semantic Processing**
   - *Purpose*: Classify text lines into logical functional domains (Ingredients vs. Allergens vs. Nutrition) to prevent word-set contamination.
   - *Inputs*: List of global OCR lines and coordinate bounding boxes.
   - *Outputs*: Section-isolated text blocks.
   - *Owning Files*: [StructuralLayoutAnalyzer.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/StructuralLayoutAnalyzer.kt), [SemanticSectionClassifier.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticSectionClassifier.kt), [SemanticRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt), [SemanticPipeline.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/SemanticPipeline.kt) (Legacy).

5. **Interpretation**
   - *Purpose*: Perform spelling correction, alias resolution, E-number repair, warning mapping, and decay-based contextual scoring.
   - *Inputs*: Clean text strings, neighbor contexts, and calibration profiles.
   - *Outputs*: Fully resolved [InterpretedIngredient](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/imaging/ImageFrame.kt) objects, allergen categories, and warning strings.
   - *Owning Files*: [IngredientInterpreter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/IngredientInterpreter.kt), [OcrCorrectionEngine.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/correction/OcrCorrectionEngine.kt), [AllergenInterpreter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/AllergenInterpreter.kt), [NutritionInterpreter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/NutritionInterpreter.kt), [StorageInstructionInterpreter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/StorageInstructionInterpreter.kt), [PackagingMetadataInterpreter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/PackagingMetadataInterpreter.kt).

6. **Aggregation**
   - *Purpose*: Consolidate parsed outputs, merge multi-word fragments, and evaluate overall hazard scores.
   - *Inputs*: List of parsed semantic items.
   - *Outputs*: Unified list of ingredients and warnings metadata.
   - *Owning Files*: [AggregationStage.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/AggregationStage.kt).

7. **UI**
   - *Purpose*: Display warning alerts, E-number classifications, original bounds overlays, and latency statistics.
   - *Inputs*: [PipelineResult](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/PipelineResult.kt).
   - *Outputs*: Jetpack Compose UI layout.
   - *Owning Files*: [ResultsScreen.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/results/ResultsScreen.kt).

---

## SECTION 3 — AUTHORITATIVE PIPELINES

### 1. OCR Pipeline
- **Entry Point**: [OCRPipeline.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/OCRPipeline.kt)
- **Flow**: Raw image input ➔ quality analysis (blur, light, contrast) ➔ strategy selection (STANDARD, UPSCALE, SHARPENED, THRESHOLDED, LOW_LIGHT, TILED) ➔ ML Kit execution ➔ paragraph line reconstruction.
- **Owning Classes**: `OCRPipeline`, `OCRPipelineRouter`, `OcrPreprocessor`, `OcrSegmentation`.
- **Status**: Authoritative. Actively wired to both tests and production runtime.

### 2. Semantic Pipeline (Legacy)
- **Entry Point**: [SemanticPipeline.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/SemanticPipeline.kt)
- **Flow**: OCR raw text ➔ Normalization ➔ Raw Token Extraction ➔ Group Parsing ➔ Phrase Correction ➔ OcrCorrectionEngine.
- **Owning Classes**: `SemanticPipeline`, `NormalizationStage`, `ExtractionStage`, `GroupingStage`, `PhraseCorrectionStage`, `CorrectionStage`.
- **Status**: Legacy. Still wired to the production camera UI flow, but deprecated in favor of the execution graph.

### 3. Semantic Execution Graph
- **Entry Point**: [PipelineRunner.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/PipelineRunner.kt) -> [SemanticExecutionGraph.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt)
- **Flow**: Image input ➔ StructuralLayoutAnalyzer (Layout Zones) ➔ TargetedOcrCoordinator (Targeted crops + OCR) ➔ SemanticSectionClassifier (Section domains) ➔ SemanticRouter (Routes to interpreters) ➔ SpecializedInterpretationStage ➔ ContextualReconstructionStage ➔ AggregationStage ➔ ConfidenceCalibrationStage ➔ ReplayGenerationStage.
- **Owning Classes**: `PipelineRunner`, `SemanticExecutionGraph`, and its coordinate stages under `com.example.core.pipeline.graph`.
- **Status**: Authoritative. Wired to production UI flow under dual-execution validation mode, and fully verified against ground truth.

### 4. Dataset Pipeline
- **Entry Point**: [download_real_world_datasets.py](file:///d:/projects/Ongoing/nutriguard/benchmark/scripts/download/download_real_world_datasets.py)
- **Flow**: Fetch taxomony files and datasets ➔ generate failure case high-entropy targets via [generate_failure_images.py](file:///d:/projects/Ongoing/nutriguard/benchmark/scripts/download/generate_failure_images.py) ➔ calculate SHA256 checksums ➔ write metadata to `dataset_versions.json`.
- **Owning Classes**: Python download/generation scripts and [DatasetVerificationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/dataset/DatasetVerificationTest.kt).
- **Status**: Authoritative. Runs as a gating pre-condition to the test suites.

### 5. Export Pipeline
- **Entry Point**: [SessionExporter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/export/SessionExporter.kt)
- **Flow**: Retrieve snapshot from repository ➔ write directory tree (`raw`, `preprocessed`, `overlays`, `replay`, `semantic`, `metrics`, `metadata`) ➔ calculate file hashes ➔ write signed `manifest.json`.
- **Owning Classes**: `SessionExporter`, `ExportFileWriter`, `PipelineSnapshotRepository`.
- **Status**: Authoritative. Actively wired to tests and manual debug exports.

### 6. Replay Pipeline
- **Entry Point**: [ReplayStorageHelper.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/replay/ReplayStorageHelper.kt)
- **Flow**: Capture execution metrics, corrections, and tokens ➔ write JSON execution trace logs to persistent storage.
- **Owning Classes**: `ReplayStorageHelper`.
- **Status**: Authoritative. Actively wired in developer/benchmark variants.

---

## SECTION 4 — FILE OWNERSHIP MAP

| File | Responsibility | Runtime Used? | Notes |
| :--- | :--- | :--- | :--- |
| [PipelineRunner.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/PipelineRunner.kt) | Entry point executing the `SemanticExecutionGraph` pipeline | **Yes (Dual Validation)** | Authoritative orchestrator for refactored graph flow |
| [SemanticPipeline.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/SemanticPipeline.kt) | Sequential, linear semantic processing wrapper | **Yes** (Production UI) | **Legacy**. Needs to be deprecated and replaced by graph runner |
| [IngredientInterpreter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/IngredientInterpreter.kt) | Interprets ingredients to categories, warning tags, and E-numbers | **Yes** (Both paths) | Authoritative. Invoked during post-correction interpretation |
| [OcrCorrectionEngine.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/correction/OcrCorrectionEngine.kt) | Staged spelling correction and context-based scoring | **Yes** (Both paths) | Authoritative spelling engine core |
| [SemanticSectionClassifier.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticSectionClassifier.kt) | Classifies raw text lines into functional sections | **Yes (Dual Validation)** | Authoritative layout segment classifier |
| [SemanticRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt) | Routes sections to domain interpreters | **Yes (Dual Validation)** | Authoritative routing orchestrator |
| [AllergenInterpreter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/AllergenInterpreter.kt) | Interprets allergen text warnings | **Yes (Dual Validation)** | Authoritative allergen engine |
| [DatasetVerificationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/dataset/DatasetVerificationTest.kt) | Validates download dataset checksums and eligibility | **Yes** (Test suite only) | Gating unit test checking dataset integrity |

---

## LICENSE
Work in progress.
