# Integration Readiness Scorecard

This document defines the integration readiness scorecards for the core transition subsystems in Stage 13.0A, backing all assessments with direct code verification rather than synthetic estimates.

---

## 1. Subsystem Readiness Scorecard

| Subsystem | Readiness Level | Verified Compilation File | Verified Test File | Blockers to 100% Production |
| :--- | :---: | :--- | :--- | :--- |
| **SemanticExecutionGraph** | 🟢 **READY** | [SemanticExecutionGraph.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt) | [HeadlessPipelineTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/HeadlessPipelineTest.kt) | Bypassed by ScanViewModel.kt. Needs UI wiring. |
| **SemanticSectionClassifier**| 🟡 **PARTIAL** | [SemanticSectionClassifier.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticSectionClassifier.kt) | [SemanticSectionClassifierTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/pipeline/graph/SemanticSectionClassifierTest.kt) | Inactive in production. Needs camera frame coordinate alignment. |
| **SemanticRouter** | 🟡 **PARTIAL** | [SemanticRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt) | [SemanticRouterTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/pipeline/graph/SemanticRouterTest.kt) | Inactive in production. Needs wiring of output categories in ResultsScreen.kt. |
| **PipelineRunner** | 🟢 **READY** | [PipelineRunner.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/PipelineRunner.kt) | [HeadlessPipelineTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/HeadlessPipelineTest.kt) | Bypassed in ScanViewModel.kt. |
| **AllergenInterpreter** | 🟢 **READY** | [AllergenInterpreter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/AllergenInterpreter.kt) | [AllergenInterpreterTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/intelligence/AllergenInterpreterTest.kt) | Router inactive. Results UI lacks warning container layout. |
| **StructuralLayoutAnalyzer** | 🟡 **PARTIAL** | [StructuralLayoutAnalyzer.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/StructuralLayoutAnalyzer.kt) | [StructuralLayoutAnalyzerTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/pipeline/graph/StructuralLayoutAnalyzerTest.kt) | Graph inactive in production UI. Camera crop boundaries translation pending. |
| **TargetedOcrCoordinator** | 🟡 **PARTIAL** | [TargetedOcrCoordinator.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/TargetedOcrCoordinator.kt) | [TargetedOcrCoordinatorTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/pipeline/graph/TargetedOcrCoordinatorTest.kt) | Graph inactive. Need to measure crop overhead on device. |
| **NutritionInterpreter** | 🟢 **READY** | [NutritionInterpreter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/NutritionInterpreter.kt) | [SemanticRouterTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/pipeline/graph/SemanticRouterTest.kt) | Router inactive. Results UI lacks nutrition cards layout. |

---

## 2. Standardized Scorecard Gates & Metrics

To claim **READY (🟢)** for production, a subsystem must fulfill all of the following:
1. **Compilation Gate**: Code compiles under the active Gradle build profile (`.\gradlew.bat assembleDeveloperDebug`).
2. **Unit Test Gate**: Achieves 100% success rate on host-side JVM unit tests under `app/src/test/java/com/example/`.
3. **Integration Test Gate**: Executes successfully inside the staged execution graph wrapper in instrumented tests (`HeadlessPipelineTest.kt`).
4. **Wired Gate**: Integrated into both developer debug screens and live CameraX view flows.

Subsystems labeled **PARTIAL (🟡)** satisfy Compile, Unit, and Integration test gates, but their production execution path is currently bypassed or blocked by coordinate scaling issues.
