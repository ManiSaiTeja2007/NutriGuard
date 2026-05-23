#!/usr/bin/env python3
import os
import json
from datetime import datetime

# Target paths
BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RAW_DIR = os.path.join(BASE_DIR, "raw", "openfoodfacts")
PROCESSED_OCR_DIR = os.path.join(BASE_DIR, "processed", "ocr_ground_truth")
PROCESSED_NORM_DIR = os.path.join(BASE_DIR, "processed", "normalized_ground_truth")
PROCESSED_CANON_DIR = os.path.join(BASE_DIR, "processed", "canonical_ground_truth")

# Ensure processed target folders exist
os.makedirs(PROCESSED_OCR_DIR, exist_ok=True)
os.makedirs(PROCESSED_NORM_DIR, exist_ok=True)
os.makedirs(PROCESSED_CANON_DIR, exist_ok=True)

RAW_FILE = os.path.join(RAW_DIR, "off_products_subset.json")

def preprocess():
    print("[*] Beginning dataset preprocessing pipeline...")
    
    if not os.path.exists(RAW_FILE):
        print(f"[-] Raw file not found: {RAW_FILE}")
        print("    Please run download_openfoodfacts.py first.")
        return
        
    try:
        with open(RAW_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
            
        products = data.get("products", [])
        print(f"[+] Found {len(products)} products in raw OpenFoodFacts JSON.")
        
        ground_truth_records = []
        
        for idx, product in enumerate(products):
            # Extract basic product details
            prod_name = product.get("product_name", "Unknown Product")
            ingredients_text = product.get("ingredients_text_en", product.get("ingredients_text", ""))
            
            if not ingredients_text:
                continue
                
            # Create a mock label image name based on index
            image_name = f"label_{idx:03d}.jpg"
            
            # Simple tokenization for expected lists (lowercase, clean spaces, split by comma)
            expected_ingredients = []
            for token in ingredients_text.split(","):
                clean_tok = token.strip().lower().rstrip(".").rstrip(",")
                if clean_tok and not clean_tok.startswith("ingredients"):
                    expected_ingredients.append(clean_tok)
                    
            # Basic canonical mapping placeholder
            expected_canonical = []
            for ing in expected_ingredients:
                if "sodium chloride" in ing or "baking soda" in ing:
                    expected_canonical.append("salt" if "sodium chloride" in ing else "sodium bicarbonate")
                else:
                    expected_canonical.append(ing)
            
            # Standardized Ground Truth Schema
            record = {
                "image": image_name,
                "product_name": prod_name,
                "ground_truth_text": ingredients_text,
                "expected_ingredients": expected_ingredients,
                "expected_canonical": expected_canonical
            }
            
            ground_truth_records.append(record)
            
        # Write OCR Ground Truth Annotation files
        for record in ground_truth_records:
            record_name = record["image"].replace(".jpg", ".json")
            
            # Save into ocr_ground_truth
            with open(os.path.join(PROCESSED_OCR_DIR, record_name), "w", encoding="utf-8") as out:
                json.dump(record, out, indent=2)
                
        print(f"[+] Exported {len(ground_truth_records)} ground truth files to: {PROCESSED_OCR_DIR}")
        print("[+] Preprocessing pipeline completed successfully.")
        
    except Exception as e:
        print(f"[-] Preprocessing failed: {e}")

if __name__ == "__main__":
    preprocess()
