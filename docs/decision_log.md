# Decision Log Document

> [!NOTE]
> **Authority Boundary**: This document answers ONLY:
> * **Why was a decision made?** (Architectural Decision Records ADR-001 through ADR-006 history)
>
> This document does NOT answer:
> * **What architecture exists?** (See [architecture_state.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture_state.md))
> * **What actually executes?** (See [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md))
> * **What is migrating?** (See [migration_tracker.md](file:///d:/projects/Ongoing/nutriguard/docs/migration_tracker.md))

This document preserves the architectural history of the NutriGuard platform, logging key decisions, their rationale, trade-offs, and consequences.

---

## 1. Decision Log Entries

### ADR-001: README.md Declared Single Source of Truth (SSOT)
- **Date**: 2026-05-28
- **Decision**: Declare the root `README.md` file as the official, authoritative Single Source of Truth (SSOT) for the project state.
- **Reason**: Prevent documentation drift and architectural amnesia. Ensure that changes in runtime wiring, staging, and components are instantly transparent.
- **Alternatives Rejected**: Storing documentation in external wiki systems (leads to separation from code and documentation drift).
- **Impact**: Any discrepancies between the code behavior and the README matrices are treated as bugs and must be investigated immediately.

---

### ADR-002: Project State Package (PSP) Adoption
- **Date**: 2026-05-29
- **Decision**: Introduce a multi-file Project State Package (PSP) containing directories for architecture inventory, runtime audits, migration trackers, and decision logs.
- **Reason**: Consolidate human dashboards with machine-readable project states to allow rapid developer onboarding and continuous architecture transparency.
- **Alternatives Rejected**: Keeping all details in a single mammoth README.md (leads to readability problems and merge conflicts).
- **Impact**: Establishes a checklist gating criteria: no phase can be marked as complete unless the corresponding PSP files are updated.

---

### ADR-003: Developer/Production Build Separation
- **Date**: 2026-05-25
- **Decision**: Enforce strict separation between `developerDebug` and `productionRelease` builds using product flavors and source sets.
- **Reason**: Prevent developer diagnostic complexity (bounding-box overlays, metrics, replay logs, test assets) from leaking into the consumer release build.
- **Alternatives Rejected**: runtime feature flags (increases binary size, introduces security/safety risks of leaking debugging tools).
- **Impact**: Developer screens and directories are excluded from the compilation of the production variant.

---

### ADR-004: Dataset Provenance Gating
- **Date**: 2026-05-26
- **Decision**: Enforce strict dataset checksum checks and block mock fallback directories under [DatasetVerificationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/dataset/DatasetVerificationTest.kt).
- **Reason**: Mitigate the risk of synthetic self-validation drift where mock datasets silently masquerade as real-world calibration corpora.
- **Alternatives Rejected**: Dynamic downloading without checksumming (unstable builds and vulnerable to network injection).
- **Impact**: The test suite immediately fails if mock or corrupted files are detected in the dataset directory.

---

### ADR-005: Semantic Execution Graph Refactor
- **Date**: 2026-05-27
- **Decision**: Refactor the linear OCR/semantic pipeline into a staged execution graph (`SemanticExecutionGraph`).
- **Reason**: Prevent domain contamination where brand labels, storage advice, and allergen notices contaminate the main ingredient list.
- **Alternatives Rejected**: Writing ad-hoc regex filter rules inside the linear semantic tokenizer (highly brittle and unmaintainable).
- **Impact**: The parsing steps are separated into sequential execution stages, which run successfully in test environments.

---

### ADR-006: Runtime Audit Integration
- **Date**: 2026-05-29
- **Decision**: Document and audit actual data execution flows side-by-side.
- **Reason**: Expose runtime disconnects where refactored systems run inside tests but are not yet wired to live user streams.
- **Alternatives Rejected**: Assuming test code coverage represents production coverage (leads to silent legacy path retention).
- **Impact**: Instantly alerts developers that the production camera feed is still running on legacy code.
