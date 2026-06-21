# Baseline Metrics — Stage 13.0E

This document records the baseline metrics for the NutriGuard application before any optimizations or streamlining are applied in Stage 13.0E.

## 1. System Inventory Baseline

* **Total Kotlin Files**: 178
  - Production (Main): 147 files
  - JVM Unit Tests (`app/src/test`): 10 files
  - Android Instrumented Tests (`app/src/androidTest`): 21 files
* **Kotlin Class Count**: 183
* **Package Count**: 72
* **Total Tests**: 67
  - JVM Unit Tests: 35
  - Android Connected Tests: 32
* **Clean Build Time (assembleDeveloperDebug)**: 24 seconds
* **APK Size (app-developer-debug.apk)**: 80.08 MB (83,974,363 bytes)

## 2. Profiled Performance Baseline

* **Cold Start Time**: ~3,200 ms (measured from launch intent to home screen render ready)
* **Warm Start Time**: ~850 ms (measured from activity resume to screen render)
* **Scan Ingestion Latency (Unoptimized)**: ~35.0 seconds
  - OCR Execution: ~34.0 seconds (~97% of scan runtime)
  - Other stages (normalization, classification, interpretation, replay, UI rendering): ~1.0 second
* **Memory Usage**: ~120 MB (average active RAM during scanning preview)

## 3. Findings Summary

The primary runtime performance bottleneck is scan ingestion, taking approximately 35 seconds. Workstream 2 and 3 will investigate this bottleneck in detail.
