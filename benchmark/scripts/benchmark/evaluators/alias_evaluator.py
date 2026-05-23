#!/usr/bin/env python3
import sys
from pathlib import Path

# Setup paths for import parity
PROJECT_ROOT = Path(__file__).resolve().parents[4]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from benchmark.scripts.benchmark.pipeline_stages import AliasResolver, IngredientVocabulary
from benchmark.scripts.benchmark.metrics import calculate_precision_recall_f1
from benchmark.scripts.benchmark.failures.failure_classifier import FailureClassifier

class AliasEvaluator:
    @staticmethod
    def evaluate(extracted_tokens: list, expected_ingredients: list, vocabulary: IngredientVocabulary,
                 subset: str, replay_path: str, pipeline_version: str) -> dict:
        resolver = AliasResolver(vocabulary)
        
        resolved_records = []
        corrected_tokens = []
        confidences = []

        for token in extracted_tokens:
            candidates = resolver.resolve(token)
            best_candidate = candidates[0] if candidates else {"candidate": token, "confidence": 0.5}
            
            resolved_records.append({
                "token": token,
                "corrected": best_candidate["candidate"],
                "confidence": best_candidate["confidence"],
                "candidates": candidates
            })
            corrected_tokens.append(best_candidate["candidate"])
            confidences.append(best_candidate["confidence"])

        # Calculate average confidence
        avg_confidence = sum(confidences) / len(confidences) if confidences else 1.0

        # Calculate correction mapping metrics against expected clean ingredients
        p, r, f1 = calculate_precision_recall_f1(expected_ingredients, corrected_tokens)

        failures = FailureClassifier.classify_alias(
            actual_corrected=corrected_tokens,
            expected_corrected=expected_ingredients,
            avg_confidence=avg_confidence,
            subset=subset,
            replay_path=replay_path,
            pipeline_version=pipeline_version
        )

        return {
            "resolved_records": resolved_records,
            "corrected_tokens": corrected_tokens,
            "metrics": {
                "precision": p,
                "recall": r,
                "f1": f1,
                "average_confidence": avg_confidence
            },
            "failures": failures
        }
