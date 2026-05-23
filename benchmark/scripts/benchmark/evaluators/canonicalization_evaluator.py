#!/usr/bin/env python3
import sys
from pathlib import Path

# Setup paths for import parity
PROJECT_ROOT = Path(__file__).resolve().parents[4]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from benchmark.scripts.benchmark.pipeline_stages import IngredientCanonicalizer, MatchConfidence
from benchmark.scripts.benchmark.metrics import calculate_precision_recall_f1
from benchmark.scripts.benchmark.failures.failure_classifier import FailureClassifier

class CanonicalizationEvaluator:
    @staticmethod
    def evaluate(resolved_records: list, expected_canonical: list,
                 subset: str, replay_path: str, pipeline_version: str) -> dict:
        canonical_ingredients = []
        canonical_tokens = []

        for record in resolved_records:
            original = record["token"]
            corrected = record["corrected"]
            confidence = record["confidence"]
            
            canonical = IngredientCanonicalizer.canonicalize(corrected)
            canonical_tokens.append(canonical)

            # Determine MatchType
            # EXACT (confidence == 1.0), ALIAS_MAP (confidence == 0.95), FUZZY (other)
            if confidence == MatchConfidence.EXACT_MATCH:
                match_type = "EXACT"
            elif confidence == MatchConfidence.OCR_CORRECTION_MAP:
                match_type = "ALIAS_MAP"
            else:
                match_type = "FUZZY"

            # Check if it was canonicalized to a different name (meaning it was a canonical alias)
            if original != canonical and match_type == "EXACT":
                # In Kotlin, it maps aliases like sucrose -> sugar or e621 -> monosodium glutamate
                if IngredientCanonicalizer.is_alias(corrected):
                    match_type = "ALIAS_MAP"

            canonical_ingredients.append({
                "originalToken": original,
                "correctedToken": corrected,
                "canonicalToken": canonical,
                "confidence": confidence,
                "matchType": match_type
            })

        # Calculate metrics against expected canonical set
        p, r, f1 = calculate_precision_recall_f1(expected_canonical, canonical_tokens)

        # Accuracy is 1.0 if both expected and actual canonical sets are identical, otherwise 0.0
        actual_set = {t.strip().lower() for t in canonical_tokens if t.strip()}
        expected_set = {t.strip().lower() for t in expected_canonical if t.strip()}
        accuracy = 1.0 if actual_set == expected_set else 0.0

        failures = FailureClassifier.classify_canonicalization(
            actual_canonical=canonical_tokens,
            expected_canonical=expected_canonical,
            f1=f1,
            subset=subset,
            replay_path=replay_path,
            pipeline_version=pipeline_version
        )

        return {
            "canonicalized_ingredients": canonical_ingredients,
            "metrics": {
                "precision": p,
                "recall": r,
                "f1": f1,
                "accuracy": accuracy
            },
            "failures": failures
        }
