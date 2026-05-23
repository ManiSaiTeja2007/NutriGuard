#!/usr/bin/env python3
import sys
from pathlib import Path

# Setup paths for import parity
PROJECT_ROOT = Path(__file__).resolve().parents[4]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from benchmark.scripts.benchmark.pipeline_stages import TextNormalizer
from benchmark.scripts.benchmark.metrics import calculate_cer, calculate_wer
from benchmark.scripts.benchmark.failures.failure_classifier import FailureClassifier

class NormalizationEvaluator:
    @staticmethod
    def evaluate(ocr_text: str, gt_text: str, subset: str, replay_path: str, pipeline_version: str) -> dict:
        # Run normalization on actual OCR input
        actual_norm = TextNormalizer.normalize(ocr_text)
        
        # Run normalization on expected ground truth input
        expected_norm = TextNormalizer.normalize(gt_text)

        # Compute character/word error rates between normalized strings
        cer = calculate_cer(expected_norm, actual_norm)
        wer = calculate_wer(expected_norm, actual_norm)

        failures = FailureClassifier.classify_normalization(
            ocr_norm=actual_norm,
            gt_norm=expected_norm,
            subset=subset,
            replay_path=replay_path,
            pipeline_version=pipeline_version
        )

        return {
            "output_text": actual_norm,
            "metrics": {
                "cer": cer,
                "wer": wer
            },
            "failures": failures
        }
