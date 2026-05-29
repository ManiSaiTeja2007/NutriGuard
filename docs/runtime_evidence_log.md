# Runtime Evidence Log

This document serves as the official registry of all runtime claims verified within the NutriGuard platform. Consistent with the PSP Trustworthiness guidelines, every claim must specify its concrete evidence source, tests executed, dates, and verification results.

---

## 1. Verified Runtime Claims

### Claim 1: Live Ingestion Executes Both Runtimes in Parallel Validation Mode
- **Description**: When the feature flag is active, both the legacy linear pipeline (`SemanticPipeline`) and the new unified execution graph (`PipelineRunner`) run in parallel.
- **Evidence Source**: [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) `processAndNavigate(...)`
- **Test Executed**: `PipelineIntegrationSmokeTest.testPipelineRunnerIntegrationSmoke` & `WorkflowTests`
- **Date**: 2026-05-29
- **Files Observed**:
  - [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L241-L283)
  - [PipelineIntegrationSmokeTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/PipelineIntegrationSmokeTest.kt)
- **Verification Result**: 🟢 **PASS (VERIFIED)**

### Claim 2: Difference Comparison Logging
- **Description**: Mismatches in ingredients, allergens, warnings, interpretations, confidence, and replay outputs between legacy (Result A) and execution graph (Result B) are logged to Android logcat.
- **Evidence Source**: [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) `processAndNavigate(...)` (using tag `NUTRIGUARD_VAL`).
- **Test Executed**: Instrumented manual capture + log verification
- **Date**: 2026-05-29
- **Files Observed**:
  - [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L286-L395)
- **Verification Result**: 🟢 **PASS (VERIFIED)**

### Claim 3: Temporary Rollback Switch
- **Description**: The boolean flag `FeatureFlags.useExecutionGraph` disables graph processing and rolls back cleanly to legacy-only execution on false.
- **Evidence Source**: [FeatureFlags.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/config/FeatureFlags.kt)
- **Test Executed**: Toggle flag checks + local JVM tests
- **Date**: 2026-05-29
- **Files Observed**:
  - [FeatureFlags.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/config/FeatureFlags.kt#L20)
  - [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L243)
- **Verification Result**: 🟢 **PASS (VERIFIED)**

### Claim 4: Headless Execution Graph Pipeline Processing
- **Description**: The `PipelineRunner` successfully executes layout analysis, targeted sub-bitmap OCR zoning, section classification, and routing on headless environments.
- **Evidence Source**: [PipelineRunner.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/PipelineRunner.kt)
- **Test Executed**: `PipelineIntegrationSmokeTest` & `HeadlessPipelineTest`
- **Date**: 2026-05-29
- **Files Observed**:
  - [PipelineRunner.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/PipelineRunner.kt#L21)
  - [HeadlessPipelineTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/HeadlessPipelineTest.kt)
- **Verification Result**: 🟢 **PASS (VERIFIED)**

### Claim 5: Replay Log Capture & Persistence
- **Description**: Execution graph failures are captured, formatted as standard JSON replay entities, and saved to the device cache.
- **Evidence Source**: [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) `processAndNavigate(...)`
- **Test Executed**: `ExportTests` & `ExecutionGraphReplayTest`
- **Date**: 2026-05-29
- **Files Observed**:
  - [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L398-L459)
  - [ReplayStorageHelper.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/replay/ReplayStorageHelper.kt)
- **Verification Result**: 🟢 **PASS (VERIFIED)**

### Claim 6: Staged Execution Graph Packaging Validation Suite
- **Description**: The `PackagingValidationTest` compares Legacy vs Ground Truth and Graph vs Ground Truth, measuring Precision, Recall, F1, Accuracy, FP, and FN across 6 domains (Ingredients, Allergens, Nutrition, Warnings, Storage, Manufacturer) over 15 validation samples.
- **Evidence Source**: [PackagingValidationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/PackagingValidationTest.kt)
- **Test Executed**: `PackagingValidationTest.executePackagingValidationSuite`
- **Date**: 2026-05-29
- **Files Observed**:
  - [PackagingValidationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/PackagingValidationTest.kt)
- **Verification Result**: 🟢 **PASS (VALIDATED)**

### Claim 7: Real Packaging Scan Developer Runtime Validation
- **Description**: Running the developer build on device using real physical scans (including high sugar/sodium items and complex allergen lists) successfully routes sections (Ingredients, Nutrition, Allergens, Storage) to their correct interpreters, and displays the diagnostic trace overlays.
- **Evidence Source**: [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt)
- **Test Executed**: Developer runtime scan deployment and Logcat telemetry check
- **Date**: 2026-05-29
- **Files Observed**:
  - [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L221-L420)
- **Verification Result**: 🟢 **PASS (VERIFIED)**

---

## 2. Evidence Log Governance & Discrepancy Policy
- All claims listed in this registry must be backed by unit, integration, or manual test runs.
- If a claim is refuted by code observation, the entry must be updated immediately and downgraded to "UNKNOWN" or "FAILED".
