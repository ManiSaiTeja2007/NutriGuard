#!/usr/bin/env python3
import sys
import re
from pathlib import Path

# Setup paths for import parity
PROJECT_ROOT = Path(__file__).resolve().parents[4]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from benchmark.scripts.benchmark.pipeline_stages import IngredientExtractor, IngredientVocabulary
from benchmark.scripts.benchmark.metrics import calculate_precision_recall_f1
from benchmark.scripts.benchmark.failures.failure_classifier import FailureClassifier

class ExtractionEvaluator:
    @staticmethod
    def extract_nested_subtokens(tokens: list) -> list:
        """
        Helper to parse nested sub-tokens from parenthetical expressions.
        e.g., "enriched flour (wheat flour, niacin, reduced iron)" -> ["wheat flour", "niacin", "reduced iron"]
        """
        nested = []
        for tok in tokens:
            matches = re.findall(r'\((.*?)\)|\[(.*?)\]|\{(.*?)\}', tok)
            for m in matches:
                content = next((group for group in m if group), "")
                if content:
                    for sub in re.split(r'[,;]', content):
                        sub_clean = sub.strip().lower()
                        if sub_clean:
                            nested.append(sub_clean)
        return nested

    @staticmethod
    def evaluate(normalized_text: str, expected_ingredients: list, vocabulary: IngredientVocabulary,
                 subset: str, replay_path: str, pipeline_version: str) -> dict:
        # Get raw section first
        section = IngredientExtractor.extract_raw_section(normalized_text)
        
        # Tokenize using vocab spacing recovery
        vocab_set = vocabulary.get_vocabulary()
        actual_tokens = IngredientExtractor.tokenize(section, vocab_set)

        # 1. Top-Level Metrics
        p, r, f1 = calculate_precision_recall_f1(expected_ingredients, actual_tokens)

        # 2. Nested Hierarchical Validation
        actual_nested = ExtractionEvaluator.extract_nested_subtokens(actual_tokens)
        expected_nested = ExtractionEvaluator.extract_nested_subtokens(expected_ingredients)

        nested_p, nested_r, nested_f1 = calculate_precision_recall_f1(expected_nested, actual_nested)

        failures = FailureClassifier.classify_extraction(
            actual=actual_tokens,
            expected=expected_ingredients,
            precision=p,
            recall=r,
            f1=f1,
            subset=subset,
            replay_path=replay_path,
            pipeline_version=pipeline_version
        )

        return {
            "extracted_tokens": actual_tokens,
            "nested_tokens": actual_nested,
            "metrics": {
                "precision": p,
                "recall": r,
                "f1": f1,
                "nested_precision": nested_p,
                "nested_recall": nested_r,
                "nested_f1": nested_f1
            },
            "failures": failures
        }
