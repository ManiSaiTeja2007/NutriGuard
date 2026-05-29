# PSP Generation Architecture

This document defines the architecture, ownership boundaries, data schemas, and future roadmap for the **PSP Automation Foundation (Version 1)**. 

The core goal of this foundation is to transition the Project State Package (PSP) from manual assumptions into an evidence-driven **Engineering Intelligence System** while preserving strict human ownership of architectural governance.

---

## 1. Tooling Subproject Architecture

To prevent governance and reporting tools from inflating the production application size or leaking debugging classes into the release builds, the tooling layer is completely separated from the Android app.

```text
NutriGuard Root/
  ├── app/                  <-- Android application runtime
  └── tools/psp/            <-- Dedicated JVM-only governance subproject
        ├── ProjectHealthGenerator.kt
        ├── PSPMetricsGenerator.kt
        ├── PSPStatusEvaluator.kt
        ├── MigrationStatusFramework.kt
        ├── PSPRefresh.kt   <-- Entry point
        └── build.gradle.kts
```

* **JVM Isolation**: Compiles as a standard Kotlin/JVM application with zero Android SDK dependencies.
* **Build Dependency**: Registered as `:tools:psp` inside `settings.gradle.kts`.
* **Execution Boundary**: Run explicitly via Gradle tasks. Tooling classes are completely excluded from the compilation of any production APK variants.

---

## 2. Ingestion Flow & Verification Pipeline

The verification pipeline `pspRefresh` behaves as an validation engine that reads actual evidence, checks integrity limits, and generates consolidated reports. It never invents findings or generates documentation text.

```text
Reality ➔ [Gradle Tests] ➔ XML Reports ────────┐
Reality ➔ [Datasets] ➔ JSON Health Reports ───┼─► [pspRefresh Task] ➔ psp_metrics.json & project_health.json
Human ➔ [Manual Audits] ➔ runtime_report.json ─┘
```

### Pipeline Sequence (`pspRefresh`)
1. **Compile**: Builds the Kotlin/JVM class files directly under `tools/psp/`.
2. **Read Reports**: Parses Gradle XML test files and dataset verification health records.
3. **Validate Reports**: Reads `runtime_audit_report.json` and performs schema, timestamp, and active file existence checks.
4. **Generate Metrics**: Calculates overall test metrics, migration averages, and status colors.
5. **Serialize**: Overwrites `project_health.json` and `psp_metrics.json` in the reports directory.

---

## 3. Ownership Boundaries & Automation Boundaries

Governance integrity requires strict demarcation between what is machine-owned (regenerated automatically) and what is human-owned (requiring engineering review).

| Layer | Files | Ownership | Generation Rule |
| :--- | :--- | :--- | :--- |
| **Human Layer** | [README.md](file:///d:/projects/Ongoing/nutriguard/README.md)<br>[decision_log.md](file:///d:/projects/Ongoing/nutriguard/docs/decision_log.md)<br>[open_questions.md](file:///d:/projects/Ongoing/nutriguard/docs/open_questions.md)<br>[runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md) | **100% Human** | **NEVER** automatically overwritten or modified by scripts. |
| **Semi-Human Layer** | [runtime_audit_report.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/runtime_audit_report.json)<br>[migration_state.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/migration_state.json) | **Shared** | Written/updated manually by humans. Audited and validated automatically by the pipeline. |
| **Generated Layer** | [project_health.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/project_health.json)<br>[psp_metrics.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/psp_metrics.json) | **100% Machine** | Generated dynamically on each verification run. Manual edits are prohibited. |

---

## 4. Migration Maturity model & Progression Criteria

To prevent fabricated precision, migration percentages are mathematically derived from explicit milestone-based maturity states:

| Milestone State | Objective % | Progression Criteria & Verification Requirements |
| :--- | :---: | :--- |
| `NOT_STARTED` | **0%** | No implementation exists. |
| `IMPLEMENTED` | **15%** | Subsystem exists and compiles successfully within the source sets. |
| `UNIT_TESTED` | **25%** | Class logic is covered by passing unit tests under `app/src/test/`. |
| `INTEGRATION_TESTED` | **35%** | Component is wired into the execution graph and passes headless emulator/benchmark tests. |
| `DEV_RUNTIME_WIRED` | **50%** | Subsystem is wired into developer debug views or manual asset image loaders. |
| `PRODUCTION_RUNTIME_WIRED` | **85%** | Subsystem is fully wired into the live CameraX preview frame ingestion stream. |
| `PRODUCTION_VERIFIED` | **100%** | Subsystem has been deployed, verified under release builds, and validated with zero regressions. |

---

## 5. Explicit PSP Status Rules

The status color (`GREEN`, `YELLOW`, `RED`) of the Project State Package is evaluated on each pipeline run using explicit criteria:

* **GREEN**:
  - `tests_failed == 0` and `tests_passed > 0`
  - Audit age is < 30 days old.
  - Dataset health is Good (`calibration_ready == true`).
  - Runtime consistency passes verification checks.
* **YELLOW**:
  - Audit age is 30–90 days (Stale status).
  - OR tests passed is 0 (Partial verification).
* **RED**:
  - `tests_failed > 0` (Failing test suite).
  - OR Audit age is > 90 days (Extremely stale / missing audit).
  - OR Dataset calibration check fails.
  - OR Runtime consistency validation fails.

---

## 6. Future Automation Roadmap

* **Phase A — Project Health Generation** *(Current)*: Ingestion of unit test results, dataset health JSONs, and manual runtime report validation.
* **Phase B — Metrics Generation** *(Current)*: Serialization of automated schema versioned PSP metrics.
* **Phase C — Runtime Audit Assistance**: Auto-scanning of ViewModel source code imports during compilation to warn of undocumented legacy pipeline usage.
* **Phase D — Consistency Validation**: Automated Markdown parser checking table value alignment between `migration_tracker.md` and `project_health.json`.
* **Phase E — Governance Compliance Reporting**: Pre-commit hooks blocking git commits if `pspRefresh` fails or reports a `RED` status.

---

## 7. Version Lock

* **Architecture Version**: 1.0
* **Status**: APPROVED
* **Architecture Status**: LOCKED

*Any changes to the tooling layer build configuration, reporting JSON schemas, or status evaluation rules must log a detailed rationale entry in [decision_log.md](file:///d:/projects/Ongoing/nutriguard/docs/decision_log.md) and undergo complete PSP review.*
