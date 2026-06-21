# Test Execution Matrix — Stage 13.0D

This document maps the verification scopes across JVM Unit Tests, Android Connected Instrumented Tests, and Manual Real Device Validation to ensure maximum coverage and zero duplication of testing efforts.

---

## 1. Test Execution Scopes

We divide verification into three distinct runtime layers:

| Layer | Execution Method | Scope & Targets | Key Advantages |
| :--- | :--- | :--- | :--- |
| **JVM Unit Tests** | `.\gradlew.bat testDeveloperDebugUnitTest` | - Core business logic (`IngredientInterpreter`, `AllergenInterpreter`, `AliasRepairEngine`).<br>- Sanitization helpers (`TextNormalizer`, `IngredientNormalizer`).<br>- Stage profiling structures (`ExecutionProfiler`).<br>- Dataset calibration sanity (`DatasetVerificationTest`). | Fast execution (seconds), runs headlessly, zero emulator dependency. |
| **Connected Android Tests**| `.\gradlew.bat connectedDeveloperDebugAndroidTest` | - Android-specific classes (`FramePipeline`, `OCRPipeline`, `Bitmap` processing).<br>- Headless graph execution (`HeadlessPipelineTest`).<br>- Packaging validation scorecard calculations (`PackagingValidationTest`).<br>- Live presentation layer wiring (`RuntimeExecutionVerificationTest`). | True Android runtime environment, tests actual OCR libraries and hardware layout bounds. |
| **Real Device Validation** | Manual developer run on physical device / emulator | - Live CameraX viewfinder overlay smoothness.<br>- Bounding box UI positioning and user guidance responsiveness.<br>- Manual end-to-end scan-to-results flow.<br>- Logcat inspection (verifying zero parallel validation telemetry or duplications). | Ensures optimal visual UX, preview performance, and real-time interaction feedback. |

---

## 2. Coverage Matrix

| Subsystem / Feature | JVM Unit Test | Connected Test | Real Device | Verification Target |
| :--- | :---: | :---: | :---: | :--- |
| **Camera Viewfinder preview** | ❌ | ❌ |  | Visual feedback overlays, preview frame rate stability. |
| **Crop & Image Prep** | ❌ |  | ❌ | `OcrPreprocessor` and CLAHE/Sharpening. |
| **OCR Text Extraction** | ❌ |  | ❌ | `OCRPipeline` text recognition and confidence. |
| **Section Identification** |  |  | ❌ | `SemanticSectionClassifier` matching headers. |
| **Domain Partition Routing** |  |  | ❌ | `SemanticRouter` dispatching parsed segments. |
| **Ingredient Category Parsing**|  |  | ❌ | `IngredientInterpreter` database dictionary. |
| **Allergen Detection** |  |  | ❌ | `AllergenInterpreter` matching allergens. |
| **Replay Serialization** |  |  | ❌ | `ReplayStorageHelper` writing JSON cache logs. |
| **ViewModel UI Navigation** | ❌ |  |  | `ScanViewModel` navigating to results screen. |
