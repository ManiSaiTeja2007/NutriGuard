# Runtime Performance Baseline — Stage 13.0D

This document logs the runtime performance metrics of the NutriGuard ingestion pipeline, comparing the dual-execution path (before convergence) and the unified execution graph path (after convergence).

---

## 1. Latency Metrics Comparison

| Processing Stage / Metric | Before Convergence (Dual Path, ms) | After Convergence (Unified Path, ms) | Delta / Savings (ms) | Notes |
| :--- | :---: | :---: | :---: | :--- |
| **OCR Time** | 260 – 770 ms | 110 – 320 ms | **-150 to -450 ms** | Before convergence, OCR was run twice (full-frame preview + crops). After convergence, OCR runs only on zoned crops during ingestion. |
| **Semantic Time** | 8 – 37 ms | 3 – 12 ms | **-5 to -25 ms** | Runs spelling correction only on the isolated ingredients block. |
| **Routing Time** | 1 – 2 ms | 1 – 2 ms | **0 ms** | Fast keyword-based anchors dispatching in `SemanticRouter`. |
| **Replay Time** | 4 – 15 ms | 2 – 8 ms | **-2 to -7 ms** | Replay trace compiled and saved only once for the graph result. |
| **Total Ingestion Time** | **280 – 835 ms** | **120 – 352 ms** | **-160 to -483 ms** | Combined total ingestion latency. Unifying the path yields **>55% speedup**. |

---

## 2. Key Performance Observations

### 2.1 Elimination of Duplicated OCR
Previously, the runtime environment executed ML Kit's text recognizer on the full preview frame within `OcrCameraFrameAnalyzer`, and then executed it again on cropped sections inside `TargetedOcrCoordinator` during ingestion. Unifying the path eliminates this double execution, keeping the preview analyzer strictly for visual feedback and using only cropped zoning for final ingestion.

### 2.2 Parallel Execution Removal
Bypassing the legacy sequential pipeline (`SemanticPipeline`) during execution graph runs saves memory allocation, GC pause cycles, and CPU time. Discrepancy comparison loops (`NUTRIGUARD_VAL`) have been completely decoupled from the production runtime, moving parity checks entirely to unit and integration testing.
