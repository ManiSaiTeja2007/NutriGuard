#!/usr/bin/env python3
import sys
import os
from pathlib import Path

# Setup paths for imports
SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parents[2] # d:\projects\Ongoing\nutriguard
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from benchmark.scripts.benchmark.pipeline_stages import TextNormalizer, IngredientExtractor, IngredientCanonicalizer, IngredientVocabulary

def preprocess():
    print("[*] Beginning dataset preprocessing pipeline...")
    
    datasets_dir = PROJECT_ROOT / "benchmark" / "datasets"
    raw_dir = datasets_dir / "raw"
    processed_dir = datasets_dir / "processed"
    
    normalized_dir = processed_dir / "normalized"
    canonical_dir = processed_dir / "canonical"
    
    # Ensure processed target folders exist
    normalized_dir.mkdir(parents=True, exist_ok=True)
    canonical_dir.mkdir(parents=True, exist_ok=True)
    
    if not raw_dir.exists():
        print(f"[-] Raw directory not found: {raw_dir}")
        return
        
    vocab_engine = IngredientVocabulary()
    vocabulary = vocab_engine.get_vocabulary()
    
    txt_files = list(raw_dir.glob("**/*.txt"))
    print(f"[+] Found {len(txt_files)} raw annotation files to process.")
    
    processed_count = 0
    for txt_path in txt_files:
        try:
            # Skip if the text file is inside the processed directory itself (in case glob matches them)
            if "processed" in txt_path.parts:
                continue
                
            with open(txt_path, "r", encoding="utf-8") as f:
                content = f.read()
                
            # Extract raw ingredients section
            raw_ingredients_text = ""
            if "[RAW INGREDIENTS]" in content:
                parts = content.split("[RAW INGREDIENTS]")
                if len(parts) > 1:
                    rest = parts[1]
                    # split by next marker
                    next_markers = ["[EXPECTED CANONICAL]", "[NUTRITION VALUES]", "[FAILURE_TAGS]"]
                    for marker in next_markers:
                        if marker in rest:
                            rest = rest.split(marker)[0]
                    raw_ingredients_text = rest.strip()
            
            if not raw_ingredients_text:
                continue
                
            # Extract raw section (remove headers)
            extracted_section = IngredientExtractor.extract_raw_section(raw_ingredients_text)
            
            # Normalize text
            normalized_text = TextNormalizer.normalize(extracted_section)
            
            # Save normalized text
            norm_output_path = normalized_dir / f"{txt_path.stem}_normalized.txt"
            with open(norm_output_path, "w", encoding="utf-8") as out:
                out.write(normalized_text)
                
            # Tokenize normalized text
            tokens = IngredientExtractor.tokenize(normalized_text, vocabulary)
            
            # Canonicalize tokens
            canonical_tokens = [IngredientCanonicalizer.canonicalize(t) for t in tokens if t.strip()]
            canonical_text = "\n".join(canonical_tokens)
            
            # Save canonical text
            canon_output_path = canonical_dir / f"{txt_path.stem}_canonical.txt"
            with open(canon_output_path, "w", encoding="utf-8") as out:
                out.write(canonical_text)
                
            processed_count += 1
        except Exception as e:
            print(f"[-] Failed to preprocess {txt_path.name}: {e}")
            
    print(f"[+] Successfully preprocessed {processed_count} files into normalized and canonical directories.")

if __name__ == "__main__":
    preprocess()
