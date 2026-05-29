# PSP Consistency Audit

**Audit Date**: 2026-05-29  
**Auditor**: Automated PSP Governance Reconciliation  
**Authority Basis**: Code > Runtime Audit > README.md > Other PSP Documents  

> [!CAUTION]
> This document was created because multiple PSP documents were found to be in direct contradiction with one another. No further implementation may begin until all conflicts listed below are resolved and this audit is marked **RESOLVED**.

---

## Audit Summary

| Item | Count |
| :--- | :---: |
| Critical Conflicts Found | 7 |
| Documents Audited | 10 |
| Subsystems Cross-Checked | 8 |
| Correct Stage Determination | **Stage 13.0B — COMPLETE** |
| PSP Consistency Before Audit | ❌ FAIL |
| PSP Consistency After Resolution | ✅ PASS (all 7 conflicts resolved) |

---

## TASK 1 — Cross-Document Contradiction Registry

### CONFLICT 1 — Current Stage Disagreement (Critical)

| Field | Value |
| :--- | :--- |
| **Document A** | [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) line 10 (PSP Metadata block) |
| **Document B** | [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) line 114 (Current Stage Dashboard) |
| **Conflict** | Line 10 states `Current Stage: Stage 13.0A — Runtime Convergence & Pipeline Integration Audit`. Line 114 states `Stage 13.0B — Runtime Convergence Implementation`. These are in the **same file**. |
| **Correct Value** | `Stage 13.0B — Runtime Convergence Implementation` (COMPLETE). Evidence: Exit Gate Checklist at line 119 is fully checked. `README_STATE.md` correctly states `Stage 13.0B`. `migration_tracker.md` exit gate is fully passed. |
| **Evidence** | ScanViewModel.kt dual-execution wiring, PipelineIntegrationSmokeTest.kt passing, pspRefresh GREEN |
| **Resolution** | ✅ Update README.md PSP Metadata block (line 10) to `Stage 13.0B — Runtime Convergence Implementation`. |

---

### CONFLICT 2 — Runtime Wiring Matrix vs Migration Tracker (Critical)

| Field | Value |
| :--- | :--- |
| **Document A** | [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) — RUNTIME WIRING MATRIX (lines 235–238) |
| **Document B** | [migration_tracker.md](file:///d:/projects/Ongoing/nutriguard/docs/migration_tracker.md) — Migration Progress Matrix |
| **Conflict** | README RUNTIME WIRING MATRIX states `SemanticExecutionGraph → Production Build: No`. Migration Tracker states `SemanticExecutionGraph → WIRED_PROD (85%)`. These directly contradict. |
| **Correct Value** | `Yes (Dual Validation)` for Production Build. Evidence: ScanViewModel.kt lines 246–280 show `PipelineRunner.run(...)` is called when `FeatureFlags.useExecutionGraph == true`. The flag defaults to true. |
| **Evidence** | [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L246-L280) |
| **Resolution** | ✅ Update README.md RUNTIME WIRING MATRIX section to show `Yes (Dual)` for all execution graph components in Developer and Production columns. |

---

### CONFLICT 3 — Runtime Migration Progress Percentages (Critical)

| Field | Value |
| :--- | :--- |
| **Document A** | [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) — RUNTIME MIGRATION TRACKER (lines 244–253) |
| **Document B** | [migration_tracker.md](file:///d:/projects/Ongoing/nutriguard/docs/migration_tracker.md) — Migration Progress Matrix |
| **Conflict** | README RUNTIME MIGRATION TRACKER shows: `SemanticExecutionGraph: 40%`, `SemanticRouter: 30%`, `PipelineRunner: 30%`, `AllergenInterpreter: 25%`. Migration Tracker shows all at `WIRED_PROD (85%)`. This is a 45–60 percentage point discrepancy. |
| **Correct Value** | `WIRED_PROD (85%)` for all components. These figures are derived from `migration_state.json` by `ProjectHealthGenerator.kt`, which outputs 85% (`pspRefresh` result confirmed). |
| **Evidence** | [benchmark/reports/migration_state.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/migration_state.json), [project_health.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/project_health.json) `runtime_migration_percent: 85` |
| **Resolution** | ✅ Remove or replace stale RUNTIME MIGRATION TRACKER section in README.md with a reference to `migration_tracker.md`. |

---

### CONFLICT 4 — Architecture Authority Section (Stale Transitional Claims)

| Field | Value |
| :--- | :--- |
| **Document A** | [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) — ARCHITECTURE AUTHORITY section (lines 322–328) |
| **Document B** | [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md) — Production / Test Divergence Audit |
| **Document C** | [migration_tracker.md](file:///d:/projects/Ongoing/nutriguard/docs/migration_tracker.md) |
| **Conflict** | README ARCHITECTURE AUTHORITY states: *"SemanticExecutionGraph Integration: Currently runs in tests/benchmarks but must replace legacy pipelines inside the live view model."* This is Stage 13.0A language. `runtime_audit.md` divergence table now shows all these subsystems as CONVERGENT (dual-validation). |
| **Correct Value** | SemanticExecutionGraph is **WIRED_PROD** — running in production under dual-validation mode. The "awaiting wiring" description is false and was resolved in Stage 13.0B. |
| **Evidence** | [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L246) `if (FeatureFlags.useExecutionGraph)` block present and executes. |
| **Resolution** | ✅ Update ARCHITECTURE AUTHORITY section in README.md to reflect current dual-validation wiring state. |

---

### CONFLICT 5 — architecture_state.md Runtime State Category (Stale)

| Field | Value |
| :--- | :--- |
| **Document A** | [architecture_state.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture_state.md) — System Authority Map |
| **Document B** | [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md) — Production/Test Divergence Audit |
| **Conflict** | `architecture_state.md` classifies PipelineRunner, SemanticExecutionGraph, SemanticSectionClassifier, SemanticRouter, AllergenInterpreter, NutritionInterpreter as `Transitional (Active in Tests/Benchmarks)`. `runtime_audit.md` shows them all as `CONVERGENT: Executes in parallel validation mode`. These directly contradict. |
| **Correct Value** | Runtime State Category should be `Active (Dual Validation, Production + Tests)` for all execution graph components. |
| **Evidence** | [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L246-L280), `PipelineIntegrationSmokeTest.kt` passing |
| **Resolution** | ✅ Update `architecture_state.md` System Authority Map to `Active (Dual Validation — Production + Dev + Tests)` for all execution graph components. |

---

### CONFLICT 6 — Open Questions Section Contains Resolved Questions

| Field | Value |
| :--- | :--- |
| **Document A** | [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) — OPEN QUESTIONS (lines 332–343) |
| **Document B** | [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md) |
| **Conflict** | README OPEN QUESTIONS item 1 asks *"Is SemanticExecutionGraph ready for production migration?"* This was resolved in Stage 13.0B — it is now wired in production dual-validation mode. Question 4 asks *"Which remaining runtime paths bypass PipelineRunner?"* — resolved, none when `useExecutionGraph` is true. |
| **Correct Value** | These questions are answered. They must be moved to decision_log.md or removed from OPEN QUESTIONS. |
| **Evidence** | Stage 13.0B Exit Gate checklist, pspRefresh GREEN, ScanViewModel.kt dual-execution wiring |
| **Resolution** | ✅ Remove resolved questions from README OPEN QUESTIONS and archive answers in decision_log.md. |

---

### CONFLICT 7 — runtime_audit_report.json Findings Partially Stale

| Field | Value |
| :--- | :--- |
| **Document A** | [runtime_audit_report.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/runtime_audit_report.json) |
| **Document B** | [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md) — Divergence Audit |
| **Conflict** | `runtime_audit_report.json` evidence list contains only `ScanViewModel.kt` and `SemanticPipeline.kt` in the referenced `evidence` array. It does not reference `PipelineRunner.kt`, `SemanticExecutionGraph.kt`, `PipelineIntegrationSmokeTest.kt` — the three key files that prove dual-validation mode is operational. |
| **Correct Value** | Evidence list must include runtime proof files for all verified findings. |
| **Evidence** | `PipelineRunner.kt`, `HeadlessPipelineTest.kt`, `PipelineIntegrationSmokeTest.kt` |
| **Resolution** | ✅ Extend `runtime_audit_report.json` evidence array with dual-validation proof sources. |

---

## TASK 2 — Subsystem Lifecycle State Matrix (Evidence-Backed)

Each subsystem is evaluated against the six lifecycle states using direct code evidence.

| Subsystem | EXISTS | COMPILES | TESTED | WIRED_DEV | WIRED_PROD | VERIFIED_PROD | Current State | Evidence |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **PipelineRunner** | ✅ | ✅ | ✅ | ✅ | ✅ (Dual) | ❌ | `WIRED_PROD` | [ScanViewModel.kt#L68](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L68) `private val pipelineRunner = PipelineRunner(...)`. Executed at L267. |
| **SemanticExecutionGraph** | ✅ | ✅ | ✅ | ✅ | ✅ (Dual) | ❌ | `WIRED_PROD` | Called via `PipelineRunner.run(...)` → `SemanticExecutionGraph.execute(...)`. [HeadlessPipelineTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/HeadlessPipelineTest.kt) asserts output. |
| **SemanticSectionClassifier** | ✅ | ✅ | ✅ | ✅ | ✅ (Dual) | ❌ | `WIRED_PROD` | Invoked inside `SemanticExecutionGraph` stage chain. [SemanticSectionClassifierTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/pipeline/graph/SemanticSectionClassifierTest.kt) passes. |
| **SemanticRouter** | ✅ | ✅ | ✅ | ✅ | ✅ (Dual) | ❌ | `WIRED_PROD` | Invoked by `SemanticExecutionGraph` routing stage. [SemanticRouterTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/pipeline/graph/SemanticRouterTest.kt) passes. |
| **AllergenInterpreter** | ✅ | ✅ | ✅ | ✅ | ✅ (Dual) | ❌ | `WIRED_PROD` | Invoked by `SemanticRouter` dispatch. [AllergenInterpreterTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/intelligence/AllergenInterpreterTest.kt) passes. |
| **NutritionInterpreter** | ✅ | ✅ | ✅ | ✅ | ✅ (Dual) | ❌ | `WIRED_PROD` | Invoked by `SemanticRouter` dispatch. Verified under integration tests. |
| **Replay System** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | `VERIFIED_PROD` | [ReplayStorageHelper.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/replay/ReplayStorageHelper.kt) called from ScanViewModel.kt L443. [ExecutionGraphReplayTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/pipeline/graph/ExecutionGraphReplayTest.kt) passes. |
| **Dataset Verification** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | `VERIFIED_PROD` | [DatasetVerificationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/dataset/DatasetVerificationTest.kt) — gating test; all 43 tests pass under pspRefresh. |

> [!NOTE]
> **`WIRED_PROD` ≠ `VERIFIED_PROD`**: The distinction is critical. `WIRED_PROD` means the subsystem executes in production **under dual-validation mode alongside the legacy path**. `VERIFIED_PROD` requires the legacy path to be fully retired and the subsystem to stand as the **sole authority**. No component may be upgraded from `WIRED_PROD` to `VERIFIED_PROD` until the legacy `SemanticPipeline` co-authority is removed.

---

## TASK 3 — Runtime Wiring Verification (Call Chains)

### PipelineRunner

| Field | Value |
| :--- | :--- |
| **File** | [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) |
| **Method** | `processAndNavigate(...)` |
| **Call Chain** | `ingestLiveCamera(...)` → `processAndNavigate(...)` → `if (FeatureFlags.useExecutionGraph)` → `pipelineRunner.run(bitmap, ...)` |
| **Line References** | L246 (flag check), L267 (`pipelineRunner.run(...)`), L68 (member instantiation) |
| **Test Evidence** | `PipelineIntegrationSmokeTest.testPipelineRunnerIntegrationSmoke` — asserts non-empty output |
| **Runtime Evidence** | Claim 1 in [runtime_evidence_log.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_evidence_log.md): PASS (VERIFIED) |

### SemanticExecutionGraph

| Field | Value |
| :--- | :--- |
| **File** | [PipelineRunner.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/PipelineRunner.kt) |
| **Method** | `run(...)` |
| **Call Chain** | `ScanViewModel.processAndNavigate` → `pipelineRunner.run(...)` → `SemanticExecutionGraph.execute(...)` |
| **Test Evidence** | `HeadlessPipelineTest.testPipelineHeadlessExecutionOnLabel000006` — asserts graph stages complete |
| **Runtime Evidence** | Claim 4 in [runtime_evidence_log.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_evidence_log.md): PASS (VERIFIED) |

### SemanticSectionClassifier

| Field | Value |
| :--- | :--- |
| **File** | [SemanticExecutionGraph.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt) |
| **Method** | `execute(...)` stage pipeline |
| **Call Chain** | `PipelineRunner.run(...)` → `SemanticExecutionGraph.execute(...)` → `SemanticSectionClassifier.classify(...)` |
| **Test Evidence** | `HeadlessPipelineTest` + `SemanticSectionClassifierTest` |
| **Runtime Evidence** | Execution graph runs section classification as a stage. Verified under dual-validation mode. |

### SemanticRouter

| Field | Value |
| :--- | :--- |
| **File** | [SemanticExecutionGraph.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt) |
| **Method** | `execute(...)` — routing stage |
| **Call Chain** | `PipelineRunner.run(...)` → `SemanticExecutionGraph.execute(...)` → `SemanticRouter.route(sections)` → dispatches to `AllergenInterpreter`, `NutritionInterpreter`, `StorageInstructionInterpreter` |
| **Test Evidence** | `SemanticRouterTest`, `HeadlessPipelineTest` |
| **Runtime Evidence** | [runtime_audit_report.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/runtime_audit_report.json) finding: *"SemanticRouter is active under parallel validation — VERIFIED"* |

---

## TASK 4 — Stage Reconciliation (Definitive Determination)

### Evidence Review

| Source | Stage Claim | Confidence |
| :--- | :--- | :--- |
| README.md line 10 (PSP Metadata) | `Stage 13.0A` | ❌ STALE — conflicts with all other evidence |
| README.md line 114 (Stage Dashboard) | `Stage 13.0B` | ✅ Matches code reality |
| README.md line 119 (Exit Gate) | Exit gate fully checked (all ✅) | ✅ Gate passed = stage complete |
| `README_STATE.md` | `Stage 13.0B` (COMPLETE) | ✅ Generated from migration_state.json |
| `project_health.json` | `stage: "Stage 13.0B"` | ✅ Machine-generated, authoritative |
| `migration_tracker.md` Exit Gate | All 7 criteria PASS | ✅ Stage 13.0B exit gate passed |
| `migration_state.json` | All components `WIRED_PROD` | ✅ |
| `ScanViewModel.kt` code | PipelineRunner wired under feature flag | ✅ Code is authoritative |

### Determination

> [!IMPORTANT]
> **VERDICT: Stage 13.0B is COMPLETE.**
>
> All exit gate criteria are satisfied. The production scanner executes both legacy and execution graph paths in parallel validation mode. The rollback flag is validated. Connected tests pass. PSP snapshots are synchronized.
>
> The only stale document is README.md **line 10** which was not updated during Stage 13.0B completion. All other documents correctly reflect Stage 13.0B completion.
>
> **Stage 13.1 — Section Classification Production Alignment** may begin once this consistency audit is applied.

---

## TASK 5 — PSP Governance Upgrade

The following governance rule is hereby added to prevent future stage drift:

> **PSP Stage Governance Rule (SGR-001)**  
> No PSP document may contain a stage value that disagrees with `README.md` PSP Metadata block (lines 8–15). `README.md` is Mission Control. All generated artifacts (`project_health.json`, `psp_metrics.json`, `README_STATE.md`, `snapshot_manifest.json`) must inherit their `stage` and `current_stage` values from a single authoritative source: the `project_health.json` generated value, which itself is populated by `ProjectHealthGenerator.kt`. Human-authored documents must be synchronized within 24 hours of any stage transition.

---

## TASK 6 — Resolution Checklist

The following changes are required to bring PSP to 100% consistency:

| # | File | Action | Status |
| :--- | :--- | :--- | :---: |
| 1 | [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) line 10 | Change `Current Stage` to `Stage 13.0B — Runtime Convergence Implementation` | ✅ DONE |
| 2 | [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) RUNTIME WIRING MATRIX | Updated all execution graph components Production column to `Yes (Dual Validation)` | ✅ DONE |
| 3 | [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) RUNTIME MIGRATION TRACKER | Replaced stale 25–40% values with `WIRED_PROD (85%)` and added lifecycle state column | ✅ DONE |
| 4 | [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) ARCHITECTURE AUTHORITY | Updated Transitional section to reflect dual-validation wiring | ✅ DONE |
| 5 | [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) OPEN QUESTIONS | Marked questions 1 and 4 as RESOLVED with evidence links | ✅ DONE |
| 6 | [architecture_state.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture_state.md) | Updated Runtime State Category to `Active (Dual Validation)` and added Lifecycle State column | ✅ DONE |
| 7 | [runtime_audit_report.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/runtime_audit_report.json) | Extended evidence array with dual-validation proof files; added 4 new findings | ✅ DONE |
| 8 | Run `pspRefresh` | Regenerate all snapshots after changes | ✅ DONE |
