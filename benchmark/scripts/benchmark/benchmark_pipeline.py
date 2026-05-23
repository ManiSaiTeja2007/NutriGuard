#!/usr/bin/env python3
import os
import sys

# Compute Levenshtein distance in Python for CER/WER calculations
def levenshtein_distance(s1, s2):
    if len(s1) < len(s2):
        return levenshtein_distance(s2, s1)
    if len(s2) == 0:
        return len(s1)

    previous_row = range(len(s2) + 1)
    for i, c1 in enumerate(s1):
        current_row = [i + 1]
        for j, c2 in enumerate(s2):
            insertions = previous_row[j + 1] + 1
            deletions = current_row[j] + 1
            substitutions = previous_row[j] + (c1 != c2)
            current_row.append(min(insertions, deletions, substitutions))
        previous_row = current_row

    return previous_row[-1]

def calculate_cer(ground_truth, hypothesis):
    """Calculates Character Error Rate (CER)"""
    if not ground_truth:
        return 0.0 if not hypothesis else 1.0
    dist = levenshtein_distance(ground_truth, hypothesis)
    return float(dist) / len(ground_truth)

def calculate_wer(ground_truth, hypothesis):
    """Calculates Word Error Rate (WER)"""
    gt_words = ground_truth.split()
    hyp_words = hypothesis.split()
    if not gt_words:
        return 0.0 if not hyp_words else 1.0
    dist = levenshtein_distance(gt_words, hyp_words)
    return float(dist) / len(gt_words)

def calculate_precision_recall_f1(expected_set, actual_set):
    """Calculates Precision, Recall, and F1 Score for ingredient extraction"""
    expected = set(expected_set)
    actual = set(actual_set)
    
    if not expected and not actual:
        return 1.0, 1.0, 1.0
    if not expected or not actual:
        return 0.0, 0.0, 0.0
        
    true_positives = len(expected.intersection(actual))
    
    precision = float(true_positives) / len(actual)
    recall = float(true_positives) / len(expected)
    
    if precision + recall == 0.0:
        f1 = 0.0
    else:
        f1 = 2 * (precision * recall) / (precision + recall)
        
    return precision, recall, f1

def run_evaluation():
    print("[*] Initializing NutriGuard Evaluation Pipeline...")
    
    # Mock data validation pass to verify formulas operate correctly
    mock_gt_text = "ingredients: sugar, salt, citric acid"
    mock_hyp_text = "ingredients: suagr, salt, citnc acid"
    
    print("\n--- OCR Text Metrics Mock Run ---")
    print(f"Ground Truth : '{mock_gt_text}'")
    print(f"Hypothesis   : '{mock_hyp_text}'")
    
    cer = calculate_cer(mock_gt_text, mock_hyp_text)
    wer = calculate_wer(mock_gt_text, mock_hyp_text)
    
    print(f"Calculated CER: {cer:.4f}  (Character Error Rate)")
    print(f"Calculated WER: {wer:.4f}  (Word Error Rate)")
    
    print("\n--- Ingredient Extraction Metrics Mock Run ---")
    expected_ingredients = ["sugar", "salt", "citric acid"]
    # Mock OCR output normalization resolves suagr -> sugar, salt -> salt, but fails on citnc acid
    resolved_ingredients = ["sugar", "salt", "citnc acid"]
    
    p, r, f1 = calculate_precision_recall_f1(expected_ingredients, resolved_ingredients)
    print(f"Expected: {expected_ingredients}")
    print(f"Actual  : {resolved_ingredients}")
    print(f"Precision: {p:.4f}")
    print(f"Recall   : {r:.4f}")
    print(f"F1 Score : {f1:.4f}")
    
    print("\n[+] Evaluation Pipeline placeholder execution completed.")

if __name__ == "__main__":
    run_evaluation()
