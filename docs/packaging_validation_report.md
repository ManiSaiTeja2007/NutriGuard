# Packaging Intelligence Validation Report

This report presents the validation outcomes comparing the Legacy Flat-Text Pipeline and the new Staged Execution Graph with domain routing, executed over the Stage 13.1 validation dataset (15 sample scenarios, including real image assets and expanded failure corpus cases).

---

## 1. Domain Accuracy Comparison

The table below summarizes the Precision, Recall, and F1 Score improvements achieved by the Stage Execution Graph over the Legacy flat-text parser:

| Domain | Sample Count | Pipeline | Precision | Recall | F1 Score | TP | FP | Improvement % | Confidence |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Ingredients** | 15 | Legacy <br> **Graph** | 0.1193 <br> **0.3023** | 0.6190 <br> **0.6190** | 0.2000 <br> **0.4063** | 13 <br> **13** | 96 <br> **30** | **+103.15%** | **HIGH** |
| **Allergens** | 15 | Legacy <br> **Graph** | 0.0000 <br> **1.0000** | 0.0000 <br> **0.5000** | 0.0000 <br> **0.6667** | 0 <br> **3** | 0 <br> **0** | **+∞%** | **HIGH** |
| **Nutrition** | 15 | Legacy <br> **Graph** | 0.0000 <br> **1.0000** | 0.0000 <br> **0.2143** | 0.0000 <br> **0.3529** | 0 <br> **3** | 0 <br> **0** | **+∞%** | **HIGH** |
| **Warnings** | 15 | Legacy <br> **Graph** | 0.0000 <br> **1.0000** | 0.0000 <br> **0.5000** | 0.0000 <br> **0.6667** | 0 <br> **4** | 0 <br> **0** | **+∞%** | **HIGH** |
| **Storage** | 15 | Legacy <br> **Graph** | 0.0000 <br> **1.0000** | 0.0000 <br> **1.0000** | 0.0000 <br> **1.0000** | 0 <br> **2** | 0 <br> **0** | **+∞%** | **HIGH** |
| **Manufacturer**| 15 | Legacy <br> **Graph** | 0.0000 <br> **1.0000** | 0.0000 <br> **0.0833** | 0.0000 <br> **0.1538** | 0 <br> **1** | 0 <br> **0** | **+∞%** | **MEDIUM** |

---

## 2. Key Observed Improvements

1. **Massive Reduction in False Positive Ingredients**:
   - The legacy flat-text pipeline treated warnings, storage instructions, nutrition numbers, and manufacturer metadata as flat ingredients. This led to **96 False Positives** (misclassifying non-ingredient tokens as ingredients).
   - The Staged Execution Graph, by leveraging `SemanticSectionClassifier` and `SemanticRouter`, isolated the non-ingredient text blocks and prevented them from entering the ingredient tokenizer, reducing False Positives from **96 to 30** (a **68.75% reduction**).
2. **First-Class Domain Recovery**:
   - The legacy pipeline was completely blind to nutrition facts, warnings, storage conditions, and manufacturer details, scoring **0.0 F1** in those domains.
   - The Staged Execution Graph introduced specialized interpreters, achieving **100% Precision** in all structured domains, capturing critical allergens (F1: 0.6667), warnings (F1: 0.6667), storage conditions (F1: 1.0000), and nutrition parameters (F1: 0.3529) with zero false reports.

---

## 3. Remaining Failures & Future Focus Areas

1. **Ground-Truth Splitting Inconsistencies**:
   - In some test annotations (e.g. `label_000007.txt`), parenthetical percentages like `tomatoes (78,1%)` were comma-split by the human annotators into `tomatoes (78` and `1%)`, representing split mismatch noise. Future work will align ground-truth normalization.
2. **Distributor Address Line-Wraps**:
   - Multi-line manufacturer/distributor addresses that wrap across line boundaries are sometimes split into separate text blocks, bypassing the single-line regex rules of `PackagingMetadataInterpreter`.
3. **Complex Nutrition Unit Variations**:
   - Variations in spacing, case (e.g. `kcal` vs `Kcal`), or non-standard characters in nutrition labels sometimes prevent exact regex matches in `NutritionInterpreter`.
