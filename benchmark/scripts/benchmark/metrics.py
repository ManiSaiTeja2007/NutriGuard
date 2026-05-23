#!/usr/bin/env python3
import sys

def edit_distance(s1: str, s2: str) -> int:
    """
    Calculates the Levenshtein distance between two strings or lists.
    Optimized to match the behavior and logic of the Kotlin Levenshtein implementation.
    """
    len1 = len(s1)
    len2 = len(s2)
    if len1 == 0:
        return len2
    if len2 == 0:
        return len1

    # Ensure s2 is the shorter sequence to optimize DP array space
    str1 = s1 if len1 >= len2 else s2
    str2 = s2 if len1 >= len2 else s1

    dp = list(range(len(str2) + 1))
    for i in range(1, len(str1) + 1):
        prev = dp[0]
        dp[0] = i
        for j in range(1, len(str2) + 1):
            temp = dp[j]
            if str1[i - 1] == str2[j - 1]:
                dp[j] = prev
            else:
                dp[j] = min(dp[j] + 1, dp[j - 1] + 1, prev + 1)
            prev = temp
    return dp[len(str2)]

def calculate_cer(ground_truth: str, hypothesis: str) -> float:
    """
    Calculates Character Error Rate (CER).
    Matches the formula: LevenshteinDistance(gt, hyp) / len(gt)
    """
    if not ground_truth:
        return 0.0 if not hypothesis else 1.0
    dist = edit_distance(ground_truth, hypothesis)
    return float(dist) / len(ground_truth)

def calculate_wer(ground_truth: str, hypothesis: str) -> float:
    """
    Calculates Word Error Rate (WER).
    Matches the formula: LevenshteinDistance(gt_words, hyp_words) / len(gt_words)
    """
    gt_words = ground_truth.split()
    hyp_words = hypothesis.split()
    if not gt_words:
        return 0.0 if not hyp_words else 1.0
    dist = edit_distance(gt_words, hyp_words)
    return float(dist) / len(gt_words)

def calculate_precision_recall_f1(expected_list: list, actual_list: list) -> tuple:
    """
    Calculates Precision, Recall, and F1-score for two collections.
    """
    expected = [x.strip().lower() for x in expected_list if x.strip()]
    actual = [x.strip().lower() for x in actual_list if x.strip()]

    # Convert to sets for comparison
    expected_set = set(expected)
    actual_set = set(actual)

    if not expected_set and not actual_set:
        return 1.0, 1.0, 1.0
    if not expected_set or not actual_set:
        return 0.0, 0.0, 0.0

    true_positives = len(expected_set.intersection(actual_set))
    precision = float(true_positives) / len(actual_set)
    recall = float(true_positives) / len(expected_set)

    if precision + recall == 0.0:
        f1 = 0.0
    else:
        f1 = 2 * (precision * recall) / (precision + recall)

    return precision, recall, f1
