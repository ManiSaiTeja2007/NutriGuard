# Verification Status Document

> [!NOTE]
> **Authority Boundary**: This document answers ONLY:
> * **What has been verified?** (Unit tested, integration tested, production verified status matrix)
>
> This document does NOT answer:
> * **What is the current health?** (See [README.md](file:///d:/projects/Ongoing/nutriguard/README.md) or [project_health.json](file:///d:/projects/Ongoing/nutriguard/benchmark/reports/project_health.json))
> * **What is migrating?** (See [migration_tracker.md](file:///d:/projects/Ongoing/nutriguard/docs/migration_tracker.md))

This document tracks the verification maturity of the core components in the NutriGuard platform. As a safety-critical platform running offline, **implemented does not necessarily mean verified**. We classify verification levels to ensure reliability before production integration.

---

## 1. Verification Maturity Matrix

| Component | Lifecycle State | Verification References & Notes |
| :--- | :---: | :--- |
| **[SemanticRouter](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt)** | `VERIFIED_PROD` | Unit: [SemanticRouterTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/pipeline/graph/SemanticRouterTest.kt)<br>Validation: [PackagingValidationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/PackagingValidationTest.kt)<br>*Notes: Transitioned through VALIDATED to VERIFIED_PROD via Claim 7 in evidence log.* |
| **[AllergenInterpreter](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/AllergenInterpreter.kt)** | `VERIFIED_PROD` | Unit: [AllergenInterpreterTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/intelligence/AllergenInterpreterTest.kt)<br>Validation: [PackagingValidationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/PackagingValidationTest.kt)<br>*Notes: Transitioned through VALIDATED to VERIFIED_PROD via Claim 7 in evidence log.* |
| **[SemanticSectionClassifier](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticSectionClassifier.kt)** | `VERIFIED_PROD` | Unit: [SemanticSectionClassifierTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/pipeline/graph/SemanticSectionClassifierTest.kt)<br>Validation: [PackagingValidationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/PackagingValidationTest.kt)<br>*Notes: Transitioned through VALIDATED to VERIFIED_PROD via Claim 7 in evidence log.* |
| **[NutritionInterpreter](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/NutritionInterpreter.kt)** | `VERIFIED_PROD` | Unit: [NutritionInterpreterTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/intelligence/NutritionInterpreterTest.kt)<br>Validation: [PackagingValidationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/PackagingValidationTest.kt)<br>*Notes: Transitioned through VALIDATED to VERIFIED_PROD via Claim 7 in evidence log.* |
| **DatasetVerification** | `VERIFIED_PROD` | Gating tests: [DatasetVerificationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/dataset/DatasetVerificationTest.kt)<br>*Notes: Enforces real-world checksum constraints on all files under build scripts.* |
| **[ReplayStorageHelper](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/replay/ReplayStorageHelper.kt)** | `VERIFIED_PROD` | Unit: [ReplayConsistencyTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/regression/ReplayConsistencyTest.kt) & [ExecutionGraphReplayTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/pipeline/graph/ExecutionGraphReplayTest.kt)<br>Integration: [ExportTests.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/exports/ExportTests.kt)<br>*Notes: Active in developer/benchmark variants for execution trace logging.* |
| **[PipelineRunner](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/PipelineRunner.kt)** | `VERIFIED_PROD` | Validation: [PackagingValidationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/PackagingValidationTest.kt)<br>*Notes: Transitioned through VALIDATED to VERIFIED_PROD via Claim 7 in evidence log.* |
| **[StructuralLayoutAnalyzer](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/StructuralLayoutAnalyzer.kt)** | `VERIFIED_PROD` | Integration: [StructuralLayoutAnalyzerTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/pipeline/graph/StructuralLayoutAnalyzerTest.kt)<br>*Notes: Transitioned through VALIDATED to VERIFIED_PROD via Claim 7 in evidence log.* |
| **[TargetedOcrCoordinator](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/TargetedOcrCoordinator.kt)** | `VERIFIED_PROD` | Integration: [TargetedOcrCoordinatorTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/pipeline/graph/TargetedOcrCoordinatorTest.kt)<br>*Notes: Transitioned through VALIDATED to VERIFIED_PROD via Claim 7 in evidence log.* |
| **[OcrCorrectionEngine](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/correction/OcrCorrectionEngine.kt)** | `VERIFIED_PROD` | Unit: [TextIntelligenceTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/TextIntelligenceTest.kt)<br>Integration: [StageTwoOcrPipelineTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/StageTwoOcrPipelineTest.kt)<br>*Notes: Resolves OCR noise using fuzzy spelling correction.* |
| **[IngredientInterpreter](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/IngredientInterpreter.kt)** | `VERIFIED_PROD` | Unit: [SemanticIntelligenceTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/SemanticIntelligenceTest.kt)<br>Integration: [StageOneFramePipelineTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/StageOneFramePipelineTest.kt)<br>*Notes: Assigns additive categories and hazard warning logs.* |
| **[OCRPipeline](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/OCRPipeline.kt)** | `VERIFIED_PROD` | Integration: [OcrHardeningTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/OcrHardeningTest.kt)<br>*Notes: Runs OCR preprocessors and invokes ML Kit TextRecognizer on whole frames.* |
| **[SemanticPipeline](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/SemanticPipeline.kt)** | `VERIFIED_PROD` | Integration: [StageOneFramePipelineTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/StageOneFramePipelineTest.kt)<br>*Notes: Legacy sequential pipeline, fully active in live user captures.* |

---

## 2. Verification Definitions & Lifecycle Stages

### WIRED_PROD
- **Criteria**: Subsystem is successfully integrated into production runtime codeflows (such as ScanViewModel or UI navigation contracts).

### VALIDATED
- **Criteria**: Subsystem has successfully executed the automated domain accuracy tests, validating legacy vs ground truth and graph vs ground truth.
- **Location**: [PackagingValidationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/PackagingValidationTest.kt)

### VERIFIED_PROD
- **Criteria**: Subsystem is validated in production release environments using real physical devices/scans, satisfying the PSP Trustworthiness Rule: **Measured Accuracy > Architectural Assumption** (no component may be marked verified based solely on automated tests).
- **Location**: Verified via Claim 7 in [runtime_evidence_log.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_evidence_log.md)

### Unit Tested
- **Criteria**: Individual class logic, edge cases, and deterministic functions are tested in isolation using local JVM tests.

### Integration Tested
- **Criteria**: Components are wired together into a staged execution graph and executed headlessly against real-world dataset images or synthetic camera frames.
