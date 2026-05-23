#!/usr/bin/env python3
"""
Parity Validator for NutriGuard Stage 6 Ingredient Correction Pipeline.

Validates that the Python benchmark pipeline's correction outputs
match deterministic test vectors defined in benchmark/parity/correction_parity.json.

Usage:
    python benchmark/scripts/validate_correction_parity.py

Exit codes:
    0 = All parity cases pass
    1 = One or more parity cases fail
"""

import json
import sys
import re
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
PARITY_FILE = PROJECT_ROOT / "parity" / "correction_parity.json"

# ------- Embedded Python-side pipeline (mirrors Android Kotlin stages) --------

# Python equivalent of IngredientVocabulary.ocrCorruptionMap
OCR_CORRUPTION_MAP = {
    "slt": "salt",
    "suagr": "sugar",
    "citnc acid": "citric acid",
    "sodlum chloride": "sodium chloride",
    "soydum": "sodium",
    "flourr": "flour",
    "waterr": "water",
    "corn syrap": "corn syrup",
    "ascarbic": "ascorbic",
    "monosodum": "monosodium",
    "glutamatee": "glutamate",
    "veg oi1": "vegetable oil",
    "veg oil": "vegetable oil",
    "mono sodium glutamat": "monosodium glutamate",
    "acidity reg": "acidity regulator",
}

# Python equivalent of IngredientVocabulary.multilingualHooks
MULTILINGUAL_HOOKS = {
    "wasser": "water",
    "eau": "water",
    "sel": "salt",
    "salz": "salt",
    "sucre": "sugar",
    "zucker": "sugar",
    "farine de ble": "wheat flour",
    "weizenmehl": "wheat flour",
    "lecithine de soja": "soy lecithin",
    "sojalecithin": "soy lecithin",
}

# Python equivalent of IngredientOntology (abbreviations + E-numbers)
ONTOLOGY_ABBREVIATIONS = {
    "msg": "monosodium glutamate",
    "hfcs": "high fructose corn syrup",
    "slt": "salt",
}

E_NUMBER_LOOKUP = {
    "e330": "citric acid",
    "e621": "monosodium glutamate",
    "e300": "ascorbic acid",
    "e322": "soy lecithin",
    "e415": "xanthan gum",
    "e412": "guar gum",
    "e407": "carrageenan",
    "e282": "calcium propionate",
    "e211": "sodium benzoate",
    "e202": "potassium sorbate",
    "e150a": "caramel color",
    "e171": "titanium dioxide",
}

VOCABULARY = {
    "salt", "sugar", "citric acid", "water", "enriched flour", "wheat flour",
    "corn syrup", "sodium", "high fructose corn syrup", "monosodium glutamate",
    "ascorbic acid", "soy lecithin", "xanthan gum", "palm oil", "canola oil",
    "soybean oil", "natural flavor", "artificial flavor", "yeast", "calcium carbonate",
    "niacin", "reduced iron", "thiamine mononitrate", "riboflavin", "folic acid",
    "milk", "cheese", "butter", "eggs", "whey", "lactose", "dextrose",
    "modified corn starch", "gelatin", "pectin", "guar gum", "carrageenan",
    "sodium benzoate", "potassium sorbate", "calcium propionate", "baking soda",
    "sodium bicarbonate", "ammonium bicarbonate", "monocalcium phosphate",
    "disodium phosphate", "trisodium phosphate", "garlic", "onion", "spices",
    "cocoa", "chocolate", "vanilla", "malic acid", "lactic acid", "tartaric acid",
    "acetic acid", "carbonated water", "sucrose", "fructose", "glucose",
    "maltose", "stevia", "erythritol", "xylitol", "sorbitol", "mannitol",
    "aspartame", "sucralose", "acesulfame potassium", "red 40", "yellow 5",
    "yellow 6", "blue 1", "caramel color", "titanium dioxide", "sodium chloride",
    "vegetable oil", "acidity regulator",
}


def correct_token(token: str) -> tuple[str, str]:
    """
    Mirrors OcrCorrectionEngine.correct() logic deterministically.
    Returns (canonical, match_class).
    """
    clean = token.strip().lower()

    # 1. Ontology: E-number lookup
    if clean in E_NUMBER_LOOKUP:
        return E_NUMBER_LOOKUP[clean], "ONTOLOGY_ENUMBER"

    # 2. Ontology: Abbreviation lookup
    if clean in ONTOLOGY_ABBREVIATIONS:
        return ONTOLOGY_ABBREVIATIONS[clean], "ONTOLOGY_ABBREVIATION"

    # 3. Corruption map
    if clean in OCR_CORRUPTION_MAP:
        return OCR_CORRUPTION_MAP[clean], "VOCABULARY_CORRUPTION_MAP"

    # 4. Multilingual hooks
    if clean in MULTILINGUAL_HOOKS:
        return MULTILINGUAL_HOOKS[clean], "VOCABULARY_MULTILINGUAL"

    # 5. Exact vocabulary
    if clean in VOCABULARY:
        return clean, "VOCABULARY_EXACT"

    # 6. Fuzzy matching (Levenshtein)
    best_candidate = None
    best_ratio = 1.0
    for vocab_term in VOCABULARY:
        len_token = len(clean)
        len_cand = len(vocab_term)
        if abs(len_token - len_cand) > 4:
            continue
        dist = levenshtein(clean, vocab_term)
        max_len = max(len_token, len_cand)
        if max_len == 0:
            continue
        ratio = dist / max_len
        if ratio < 0.40 and ratio < best_ratio:
            best_ratio = ratio
            best_candidate = vocab_term

    if best_candidate:
        return best_candidate, "FUZZY_MATCH"

    return clean, "UNKNOWN"


def levenshtein(s1: str, s2: str) -> int:
    """Space-optimized DP Levenshtein, mirrors Kotlin implementation."""
    if not s1: return len(s2)
    if not s2: return len(s1)
    if len(s1) < len(s2):
        s1, s2 = s2, s1
    dp = list(range(len(s2) + 1))
    for i, c1 in enumerate(s1, 1):
        prev = dp[0]
        dp[0] = i
        for j, c2 in enumerate(s2, 1):
            temp = dp[j]
            if c1 == c2:
                dp[j] = prev
            else:
                dp[j] = 1 + min(dp[j], dp[j - 1], prev)
            prev = temp
    return dp[len(s2)]


# ------- Parity Runner -------

def run_parity(parity_path: Path) -> bool:
    with open(parity_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    cases = data.get("test_cases", [])
    passed = 0
    failed = 0

    print(f"\nNutriGuard Correction Parity Validator")
    print(f"Pipeline Version: {data.get('pipeline_version', 'unknown')}")
    print(f"Total Test Cases: {len(cases)}\n")

    for case in cases:
        case_id = case["id"]
        raw_input = case["input"]
        expected_canonical = case["expected_canonical"]
        expected_match_class = case.get("expected_match_class")
        notes = case.get("notes", "")

        actual_canonical, actual_match_class = correct_token(raw_input)

        canonical_ok = actual_canonical == expected_canonical
        class_ok = (expected_match_class is None) or (actual_match_class == expected_match_class)
        all_ok = canonical_ok and class_ok

        status = "[PASS]" if all_ok else "[FAIL]"
        if all_ok:
            passed += 1
            print(f"  [{case_id}] {status}  \"{raw_input}\" -> \"{actual_canonical}\"  [{actual_match_class}]")
        else:
            failed += 1
            print(f"  [{case_id}] {status}  \"{raw_input}\"")
            if not canonical_ok:
                print(f"           canonical: expected=\"{expected_canonical}\" actual=\"{actual_canonical}\"")
            if not class_ok:
                print(f"           match_class: expected=\"{expected_match_class}\" actual=\"{actual_match_class}\"")
            if notes:
                print(f"           note: {notes}")

    print(f"\nResults: {passed}/{len(cases)} passed, {failed} failed")
    return failed == 0


if __name__ == "__main__":
    success = run_parity(PARITY_FILE)
    sys.exit(0 if success else 1)
