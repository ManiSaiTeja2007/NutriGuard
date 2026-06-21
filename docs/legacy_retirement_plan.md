# Legacy Retirement Plan — Stage 13.0D

This document details the retirement plan for legacy subsystems of the NutriGuard platform, specifying their current references, replacement pathways, and exit conditions.

---

## 1. Legacy Retirement Roadmap

We have cataloged every legacy component scheduled for retirement. Deletion of these components is strictly prohibited until their respective retirement conditions are satisfied.

| Legacy Component | Reason for Existence | Current References | Replacement Pathway | Retirement Conditions | Target Retirement Stage |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **`SemanticPipeline`** | Sequential text parser pipeline. | - `ScanViewModel.kt` (fallback branch)<br>- `SpecializedInterpretationStage.kt` (graph wrapper) | Staged execution graph stages (Stages 1-9). | - `RuntimeExecutionVerificationTest` passes.<br>- `PackagingValidationTest` passes.<br>- Connected tests pass.<br>- Real device validation passes.<br>- Rollback safety verified.<br>- PSP synchronized. | **Stage 13.0D.5** |
| **Legacy VM Interpretation Loops** | Manual iteration over tokens to build categories and warnings. | - `ScanViewModel.kt` (fallback branch) | - `AggregationStage`<br>- `ConfidenceCalibrationStage` | Deprecation of fallback branch in ViewModel. | **Stage 13.0D.5** |
| **Legacy Replay Helpers** | Manual trace serializations inside ViewModel. | - `ScanViewModel.kt` (fallback branch) | - `ReplayGenerationStage`<br>- `ReplayStorageHelper` | Deprecation of fallback branch in ViewModel. | **Stage 13.0D.5** |
| **`NUTRIGUARD_VAL` Discrepancy Logs** | Parallel validation checks for discrepancy mapping. | - Reference docs and logs. | - `PackagingValidationTest` (offline validation suite) | Completed convergence. | **Stage 13.0D.5** |

---

## 2. Gate Verification Process

Before entering Stage 13.0D.5 (Legacy Retirement), the following sequence of validation must occur:

1. **Verify Staged Graph Stability**: Ensure the graph runs flawlessly with zero regression over the full packaging corpus.
2. **Execute Rollback Validation**: Confirm that toggling `FeatureFlags.useExecutionGraph` to `false` successfully routes execution through legacy paths in developer builds.
3. **Execute Clean-up**: Strip out the fallback branches, imports, and references.
4. **Compile & Run Test Suites**: Run the verification ladder to assert that code compiles and tests pass.
5. **Final Removal**: Safely delete the `SemanticPipeline.kt` file.
