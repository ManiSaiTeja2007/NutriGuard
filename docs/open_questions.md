# Open Questions Registry

> [!NOTE]
> **Authority Boundary**: This document answers ONLY:
> * **What remains unresolved?** (Active registry of engineering bottlenecks and open queries)
>
> This document does NOT answer:
> * **What is migrating?** (See [migration_tracker.md](file:///d:/projects/Ongoing/nutriguard/docs/migration_tracker.md))
> * **Why decisions were made?** (See [decision_log.md](file:///d:/projects/Ongoing/nutriguard/docs/decision_log.md))

This document registers unresolved architectural queries, design decisions, and engineering bottlenecks.

---

## 1. Active Registry Items

### EQ-001: Is SemanticExecutionGraph ready for production migration?
- **State**: Open
- **Description**: The graph refactor is verified under unit tests and static image benchmarks, but its latency footprint has not been profiled under real-time, high-frame-rate CameraX video stream updates on low-end hardware.
- **Next Step**: Profile memory and CPU latency thresholds on physical test devices running the staged graph under active video streams.

---

### EQ-002: Is SemanticRouter fully verified under dynamic contrast shifts?
- **State**: Open
- **Description**: If a user scans a label in low light, the coordinates returned by `StructuralLayoutAnalyzer` might shift. We need to verify if `SemanticRouter` or `SemanticSectionClassifier` can tolerate cropped lines that are slightly truncated due to coordinate rounding.
- **Next Step**: Write dynamic contrast variance test cases in instrumented tests.

---

### EQ-003: Which runtime paths still bypass PipelineRunner?
- **State**: Open
- **Description**: Currently, [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) bypasses `PipelineRunner` and invokes `SemanticPipeline` and `OCRPipeline` directly. We need to confirm if there are any other debug or secondary views invoking legacy components.
- **Next Step**: Scan the UI directories for references to `SemanticPipeline`.

---

### EQ-004: Are AllergenInterpreter outputs validated against real packaging datasets?
- **State**: Open
- **Description**: The allergen warning extraction rules have been verified against synthetic labels and a small subset of OpenFoodFacts French/English test images, but larger multi-lingual warning dictionaries are needed to prevent false negatives on imported products.
- **Next Step**: Integrate multilingual allergen warning taxonomies into the lookup catalog.
