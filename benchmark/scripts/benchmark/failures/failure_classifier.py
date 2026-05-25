#!/usr/bin/env python3
import sys
from pathlib import Path

# Setup paths for import parity
PROJECT_ROOT = Path(__file__).resolve().parents[4]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from benchmark.scripts.benchmark.failures.failure_types import *
from benchmark.scripts.benchmark.failures.failure_models import Failure

class FailureClassifier:
    @staticmethod
    def classify_ocr(cer: float, wer: float, category: str, subset: str, replay_path: str, pipeline_version: str) -> list:
        failures = []
        if cer > 0.15 or wer > 0.35:
            failures.append(Failure(
                failure_type=OCR_FAILURE,
                stage="ocr",
                confidence=1.0 - cer,
                subset=subset,
                replay_path=replay_path,
                pipeline_version=pipeline_version,
                details=f"High OCR error rate: CER={cer:.3f}, WER={wer:.3f}"
            ))

        # Check for noise or rotation induced failures
        if "noise" in category or "blurry" in category or "blur" in category:
            if cer > 0.05:
                failures.append(Failure(
                    failure_type=NOISE_FAILURE,
                    stage="ocr",
                    confidence=1.0 - cer,
                    subset=subset,
                    replay_path=replay_path,
                    pipeline_version=pipeline_version,
                    details=f"Noise/blur distortion detected in category '{category}': CER={cer:.3f}"
                ))
        return failures

    @staticmethod
    def classify_normalization(ocr_norm: str, gt_norm: str, subset: str, replay_path: str, pipeline_version: str) -> list:
        failures = []
        if ocr_norm != gt_norm:
            # Check if there are newline or hyphen residues
            has_newline_issue = "\n" in ocr_norm or "\r" in ocr_norm or "-\n" in ocr_norm or "- " in ocr_norm
            details = "Normalization mismatch."
            if has_newline_issue:
                details += " Leftover newlines or hyphen linebreak issues detected."
            failures.append(Failure(
                failure_type=NORMALIZATION_FAILURE,
                stage="normalization",
                confidence=0.8,
                subset=subset,
                replay_path=replay_path,
                pipeline_version=pipeline_version,
                details=details
            ))
        return failures

    @staticmethod
    def classify_extraction(actual: list, expected: list, precision: float, recall: float, f1: float, subset: str, replay_path: str, pipeline_version: str) -> list:
        failures = []
        if f1 < 0.90:
            failures.append(Failure(
                failure_type=EXTRACTION_FAILURE,
                stage="extraction",
                confidence=f1,
                subset=subset,
                replay_path=replay_path,
                pipeline_version=pipeline_version,
                details=f"Low extraction F1 score: {f1:.3f} (Precision={precision:.3f}, Recall={recall:.3f})"
            ))

        # Detect tokenization failure (splitting issues, e.g. missed commas)
        if len(expected) > 1 and len(actual) == 1:
            failures.append(Failure(
                failure_type=TOKENIZATION_FAILURE,
                stage="extraction",
                confidence=f1,
                subset=subset,
                replay_path=replay_path,
                pipeline_version=pipeline_version,
                details="Delimiter splitting failure: expected multiple tokens but extracted only one."
            ))

        # Detect truncation (significantly fewer tokens than expected)
        if len(expected) > 0 and len(actual) < 0.5 * len(expected):
            failures.append(Failure(
                failure_type=TRUNCATION_FAILURE,
                stage="extraction",
                confidence=f1,
                subset=subset,
                replay_path=replay_path,
                pipeline_version=pipeline_version,
                details=f"Truncation detected: expected {len(expected)} tokens but got {len(actual)}"
            ))
        return failures

    @staticmethod
    def classify_alias(actual_corrected: list, expected_corrected: list, avg_confidence: float, subset: str, replay_path: str, pipeline_version: str) -> list:
        failures = []
        if avg_confidence < 0.75:
            failures.append(Failure(
                failure_type=LOW_CONFIDENCE_FAILURE,
                stage="alias",
                confidence=avg_confidence,
                subset=subset,
                replay_path=replay_path,
                pipeline_version=pipeline_version,
                details=f"Low average resolution confidence: {avg_confidence:.3f}"
            ))

        # Compare sets of corrected tokens
        if set(actual_corrected) != set(expected_corrected):
            failures.append(Failure(
                failure_type=ALIAS_FAILURE,
                stage="alias",
                confidence=avg_confidence,
                subset=subset,
                replay_path=replay_path,
                pipeline_version=pipeline_version,
                details="Alias resolution mismatch. Corrected tokens do not match expectations."
            ))
            
            # Check for OCR ambiguity and additive notation failure
            for act, exp in zip(actual_corrected, expected_corrected):
                if act != exp:
                    is_act_e = act.startswith("e") or any(c.isdigit() for c in act)
                    is_exp_e = exp.startswith("e") or any(c.isdigit() for c in exp)
                    if is_act_e or is_exp_e:
                        if "(" in exp or ")" in exp or "(" in act or ")" in act:
                            failures.append(Failure(
                                failure_type=ADDITIVE_NOTATION_FAILURE,
                                stage="alias",
                                confidence=avg_confidence,
                                subset=subset,
                                replay_path=replay_path,
                                pipeline_version=pipeline_version,
                                details=f"Additive notation parsing failure between '{act}' and '{exp}'"
                            ))
                        else:
                            failures.append(Failure(
                                failure_type=OCR_AMBIGUITY_FAILURE,
                                stage="alias",
                                confidence=avg_confidence,
                                subset=subset,
                                replay_path=replay_path,
                                pipeline_version=pipeline_version,
                                details=f"OCR ambiguity confusion detected between '{act}' and '{exp}'"
                            ))
                    if len(act) < 4:
                        failures.append(Failure(
                            failure_type=FALSE_CORRECTION_RISK_FAILURE,
                            stage="alias",
                            confidence=avg_confidence,
                            subset=subset,
                            replay_path=replay_path,
                            pipeline_version=pipeline_version,
                            details=f"High risk correction avoided/failed for short token '{act}'"
                        ))
        return failures

    @staticmethod
    def classify_canonicalization(actual_canonical: list, expected_canonical: list, f1: float, subset: str, replay_path: str, pipeline_version: str) -> list:
        failures = []
        if f1 < 1.0:
            failures.append(Failure(
                failure_type=CANONICALIZATION_FAILURE,
                stage="canonicalization",
                confidence=f1,
                subset=subset,
                replay_path=replay_path,
                pipeline_version=pipeline_version,
                details=f"Canonical mapping mismatch. F1={f1:.3f}"
            ))
        return failures
