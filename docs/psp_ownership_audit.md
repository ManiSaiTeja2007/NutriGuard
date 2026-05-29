# PSP Ownership Audit

> [!NOTE]
> **Authority Boundary**: This document answers ONLY:
> * **What were the topic ownership transitions during PSP Consolidation?** (Tracing ownership reassignments)
>
> This document does NOT answer:
> * **What architecture exists?** (See [architecture_state.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture_state.md))
> * **What actually executes?** (See [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md))

This document traces the audit of Project State Package (PSP) topics, identifying current documentation owners, proposed consolidated owners, and the consolidation status to ensure zero duplication and clear authority.

---

## Topic Ownership Map

| Topic | Current Owner | Proposed Owner | Status |
| :--- | :--- | :--- | :--- |
| **Navigation** | [app_navigation.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture/app_navigation.md) | [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md) | Merged & Deprecated |
| **Replay System** | [replay_system.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture/replay_system.md) | [replay_system.md](file:///d:/projects/Ongoing/nutriguard/docs/replay_system.md) | Moved to root docs (Retained as unique Spec) |
| **Normalization** | [normalization_pipeline.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture/normalization_pipeline.md) | [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md) (Flow) & [architecture_state.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture_state.md) (Rules) | Merged & Deprecated |
| **Semantic Pipeline** | [semantic_pipeline_authority.md](file:///d:/projects/Ongoing/nutriguard/docs/semantic_pipeline_authority.md) | [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md) (Flow) & [architecture_state.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture_state.md) (Components) | Merged & Deprecated |
| **Runtime Flow** | [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md) & [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) | [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md) | Hardened |
| **Architecture Inventory** | [architecture_state.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture_state.md) & [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) | [architecture_state.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture_state.md) | Hardened |
| **Verification** | [verification_status.md](file:///d:/projects/Ongoing/nutriguard/docs/verification_status.md) | [verification_status.md](file:///d:/projects/Ongoing/nutriguard/docs/verification_status.md) | Hardened |
| **Decisions** | [decision_log.md](file:///d:/projects/Ongoing/nutriguard/docs/decision_log.md) | [decision_log.md](file:///d:/projects/Ongoing/nutriguard/docs/decision_log.md) | Hardened |
| **Open Questions** | [open_questions.md](file:///d:/projects/Ongoing/nutriguard/docs/open_questions.md) | [open_questions.md](file:///d:/projects/Ongoing/nutriguard/docs/open_questions.md) | Hardened |
| **System Inventory** | [system_inventory.md](file:///d:/projects/Ongoing/nutriguard/docs/system_inventory.md) & [feature_structure.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture/feature_structure.md) | [system_inventory.md](file:///d:/projects/Ongoing/nutriguard/docs/system_inventory.md) | Hardened |
| **Machine State** | [project_health.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/project_health.json) | [project_health.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/project_health.json) | Hardened |
| **Benchmark Boundaries** | [benchmark_pipeline.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture/benchmark_pipeline.md) | [system_inventory.md](file:///d:/projects/Ongoing/nutriguard/docs/system_inventory.md) (Boundaries) & [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md) (Flows) | Merged & Deprecated |


---

## Audit Status Rules

- **Pending Merge**: Content has not yet been extracted and integrated into the proposed owner.
- **Merged & Deprecated**: Content successfully migrated, source file marked `DEPRECATED`.
- **Deleted**: Redundant source file deleted after validation.
