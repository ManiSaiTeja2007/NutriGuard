#!/usr/bin/env python3
import sys
import re
from pathlib import Path

# Setup paths for import parity
PROJECT_ROOT = Path(__file__).resolve().parents[3]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from benchmark.scripts.benchmark.metrics import edit_distance

class TextNormalizer:
    JUNK_CHARS = ['|', '*', '_', '•', '~', '^', '\\', '/', '#', '@', '<', '>']

    @staticmethod
    def normalize(text: str) -> str:
        if not text:
            return ""

        # 1. Convert to lowercase
        result = text.lower()

        # 2. Recover hyphenated linebreaks (e.g., "citnc-\n acid" -> "citnc acid")
        result = re.sub(r"-\s*[\r\n]+\s*", " ", result)
        result = re.sub(r"-\s+", " ", result)

        # 3. Normalize newlines, carriage returns, and tabs to spaces
        result = result.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ')

        # 4. Clean up common OCR junk characters (replace with spaces)
        for char in TextNormalizer.JUNK_CHARS:
            result = result.replace(char, ' ')

        # 5. Standardize spaces around list delimiters
        result = re.sub(r"\s*,\s*", ", ", result)
        result = re.sub(r"\s*;\s*", "; ", result)
        result = re.sub(r"\s*:\s*", ": ", result)

        # 6. Clean up duplicate spaces
        result = re.sub(r"\s+", " ", result)

        # 7. Trim leading/trailing spaces
        return result.strip()


class IngredientCanonicalizer:
    CANONICAL_MAP = {
        "sodium chloride": "salt",
        "msg": "monosodium glutamate",
        "vitamin c": "ascorbic acid",
        "l-ascorbic acid": "ascorbic acid",
        "sodium hydrogen carbonate": "sodium bicarbonate",
        "baking soda": "sodium bicarbonate",
        "high-fructose corn syrup": "high fructose corn syrup",
        "hfcs": "high fructose corn syrup",
        "lecithin": "soy lecithin",
        "lecithine": "soy lecithin",
        "sucrose": "sugar",
        "cane sugar": "sugar",
        "beet sugar": "sugar",
        "sodium mono-glutamate": "monosodium glutamate",
        "e621": "monosodium glutamate",
        "e300": "ascorbic acid"
    }

    @staticmethod
    def canonicalize(ingredient: str) -> str:
        normalized = ingredient.lower().strip()
        return IngredientCanonicalizer.CANONICAL_MAP.get(normalized, normalized)

    @staticmethod
    def is_alias(ingredient: str) -> bool:
        normalized = ingredient.lower().strip()
        return normalized in IngredientCanonicalizer.CANONICAL_MAP


class IngredientExtractor:
    HEADERS = [
        "ingredients:",
        "ingredients",
        "contains:",
        "contains less than 2% of:",
        "contains less than 2% of",
        "other ingredients:",
        "other ingredients",
        "active ingredients:",
        "active ingredients",
        "inactive ingredients:",
        "inactive ingredients"
    ]

    @staticmethod
    def extract_raw_section(text: str) -> str:
        lower_text = text.lower().strip()
        for header in IngredientExtractor.HEADERS:
            idx = lower_text.find(header)
            if idx != -1:
                start = idx + len(header)
                if start < len(text):
                    return text[start:].strip()
        return text

    @staticmethod
    def tokenize(section_text: str, vocabulary: set = None) -> list:
        trimmed = section_text.strip()
        if not trimmed:
            return []

        # Check for top-level delimiters (commas or semicolons) outside brackets
        has_top_level_delimiters = False
        depth = 0
        for char in trimmed:
            if char in ('(', '[', '{'):
                depth += 1
            elif char in (')', ']', '}'):
                if depth > 0:
                    depth -= 1
            elif char in (',', ';'):
                if depth == 0:
                    has_top_level_delimiters = True
                    break

        if has_top_level_delimiters:
            initial_tokens = IngredientExtractor.split_by_delimiter(trimmed, [',', ';'])
        else:
            initial_tokens = IngredientExtractor.split_by_delimiter(trimmed, [' '])

        if has_top_level_delimiters or not vocabulary:
            return initial_tokens

        # Spacing recovery: merge adjacent tokens if they form a known vocabulary entry or canonical alias
        merged_tokens = []
        i = 0
        while i < len(initial_tokens):
            merged = False
            # Try to match multi-word entries, from longest (4 words) down to 2 words
            for length in (4, 3, 2):
                if i + length <= len(initial_tokens):
                    candidate = " ".join(initial_tokens[i:i+length])
                    clean_candidate = candidate.lower().strip()
                    canonical = IngredientCanonicalizer.canonicalize(clean_candidate)

                    is_in_vocab = (clean_candidate in vocabulary) or (canonical in vocabulary)
                    is_known_alias = (IngredientCanonicalizer.is_alias(clean_candidate) or 
                                      IngredientCanonicalizer.is_alias(canonical))

                    if is_in_vocab or is_known_alias:
                        merged_tokens.append(candidate)
                        i += length
                        merged = True
                        break
            if not merged:
                merged_tokens.append(initial_tokens[i])
                i += 1
        return merged_tokens

    @staticmethod
    def split_by_delimiter(text: str, delimiters: list) -> list:
        tokens = []
        current = []
        paren_depth = 0
        bracket_depth = 0

        for char in text:
            if char in ('(', '{'):
                paren_depth += 1
                current.append(char)
            elif char in (')', '}'):
                if paren_depth > 0:
                    paren_depth -= 1
                current.append(char)
            elif char == '[':
                bracket_depth += 1
                current.append(char)
            elif char == ']':
                if bracket_depth > 0:
                    bracket_depth -= 1
                current.append(char)
            elif char in delimiters:
                if paren_depth == 0 and bracket_depth == 0:
                    tok = IngredientExtractor.clean_token("".join(current))
                    if tok:
                        tokens.append(tok)
                    current = []
                else:
                    current.append(char)
            else:
                current.append(char)

        last_token = IngredientExtractor.clean_token("".join(current))
        if last_token:
            tokens.append(last_token)

        return tokens

    @staticmethod
    def clean_token(token: str) -> str:
        clean = token.strip()
        # Strip leading/trailing punctuation
        while clean and (clean.startswith(",") or clean.startswith(".") or clean.startswith(":") or clean.startswith(";")):
            clean = clean[1:].strip()
        while clean and (clean.endswith(",") or clean.endswith(".") or clean.endswith(":") or clean.endswith(";")):
            clean = clean[:-1].strip()
        # Collapse duplicate spaces
        clean = re.sub(r"\s+", " ", clean)
        return clean.strip()


class IngredientVocabulary:
    def __init__(self):
        self.static_vocabulary = {
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
            "yellow 6", "blue 1", "caramel color", "titanium dioxide", "sodium chloride"
        }
        self.learned_cache = set()

    def contains(self, ingredient: str) -> bool:
        normalized = ingredient.lower().strip()
        return normalized in self.static_vocabulary or normalized in self.learned_cache

    def learn(self, ingredient: str):
        normalized = ingredient.lower().strip()
        if normalized:
            self.learned_cache.add(normalized)

    def get_vocabulary(self) -> set:
        return self.static_vocabulary.union(self.learned_cache)

    def clear_learned(self):
        self.learned_cache.clear()


class MatchConfidence:
    EXACT_MATCH = 1.0
    OCR_CORRECTION_MAP = 0.95
    FUZZY_RATIO_THRESHOLD = 0.34

    @staticmethod
    def calculate_fuzzy_confidence(token: str, candidate: str, distance: int) -> float:
        len_token = len(token)
        len_candidate = len(candidate)
        max_len = max(len_token, len_candidate)
        if max_len == 0:
            return 0.0

        ratio = float(distance) / max_len
        if ratio > MatchConfidence.FUZZY_RATIO_THRESHOLD:
            return 0.0

        base_confidence = 1.0 - ratio

        # Penalize short tokens
        if len_token <= 3:
            return max(0.0, min(1.0, base_confidence * 0.75))
        else:
            return max(0.0, min(1.0, base_confidence))


class AliasResolver:
    COMMON_OCR_CORRECTIONS = {
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
        "glutamatee": "glutamate"
    }

    def __init__(self, vocabulary: IngredientVocabulary):
        self.vocabulary = vocabulary

    def resolve(self, token: str) -> list:
        clean_token = token.lower().strip()
        if not clean_token:
            return []

        # 1. Exact match in vocabulary
        if self.vocabulary.contains(clean_token):
            return [{"candidate": clean_token, "confidence": MatchConfidence.EXACT_MATCH}]

        # 2. Hardcoded OCR correction map
        hardcoded = AliasResolver.COMMON_OCR_CORRECTIONS.get(clean_token)
        if hardcoded:
            return [{"candidate": hardcoded, "confidence": MatchConfidence.OCR_CORRECTION_MAP}]

        # 3. Levenshtein fuzzy matching
        candidates = []
        vocab = self.vocabulary.get_vocabulary()
        for cand in vocab:
            len_token = len(clean_token)
            len_cand = len(cand)
            max_len = max(len_token, len_cand)

            length_diff = abs(len_token - len_cand)
            if max_len > 0 and (float(length_diff) / max_len) > MatchConfidence.FUZZY_RATIO_THRESHOLD:
                continue

            dist = edit_distance(clean_token, cand)
            conf = MatchConfidence.calculate_fuzzy_confidence(clean_token, cand, dist)
            if conf > 0.0:
                candidates.append({"candidate": cand, "confidence": conf})

        # Sort descending by confidence
        candidates.sort(key=lambda x: x["confidence"], reverse=True)

        if candidates:
            return candidates
        else:
            # Fallback
            return [{"candidate": clean_token, "confidence": 0.5}]
