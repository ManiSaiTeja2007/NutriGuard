#!/usr/bin/env python3
import sys
import json
import urllib.request
import urllib.error
import hashlib
from pathlib import Path
from datetime import datetime

# Target directories using pathlib
SCRIPT_DIR = Path(__file__).resolve().parent
BENCHMARK_DIR = SCRIPT_DIR.parent.parent
CACHE_DIR = BENCHMARK_DIR / "download_cache"
TARGET_DIR = BENCHMARK_DIR / "datasets" / "raw" / "clean_labels"

# Ensure directories exist
CACHE_DIR.mkdir(parents=True, exist_ok=True)
TARGET_DIR.mkdir(parents=True, exist_ok=True)

# Query parameters for product images on OpenFoodFacts
SEARCH_URL = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=ingredients&json=true&page_size=10"

# Mock annotation template
ANNOTATION_TEMPLATE = """[RAW INGREDIENTS]
Ingredients: {ingredients}

[EXPECTED CANONICAL]
{canonical}

[NUTRITION VALUES]
Calories: 100
Sodium: 100mg

[FAILURE_TAGS]
clean
"""

def download_products():
    print(f"[*] Fetching products list from OpenFoodFacts...")
    print(f"    URL: {SEARCH_URL}")
    
    headers = {
        'User-Agent': 'NutriGuardOCRBenchmark/1.0 (manis@example.com)'
    }
    req = urllib.request.Request(SEARCH_URL, headers=headers)
    
    try:
        with urllib.request.urlopen(req) as response:
            res_data = response.read()
            json_data = json.loads(res_data.decode('utf-8'))
            
        products = json_data.get("products", [])
        print(f"[+] Found {len(products)} products in search results.")
        
        # Get existing index to avoid conflicts
        existing_images = list(TARGET_DIR.glob("label_*.jpg"))
        current_index = len(existing_images) + 1
        
        download_count = 0
        for product in products:
            image_url = product.get("image_ingredients_url") or product.get("image_front_url")
            ingredients_text = product.get("ingredients_text_en", product.get("ingredients_text", "unknown"))
            
            if not image_url:
                continue
                
            print(f"[*] Downloading image from OpenFoodFacts...")
            print(f"    Source URL: {image_url}")
            
            # Temporary cache file path
            temp_ext = Path(image_url).suffix or ".jpg"
            if temp_ext.lower() not in [".jpg", ".jpeg", ".png"]:
                temp_ext = ".jpg"
                
            temp_cache_file = CACHE_DIR / f"temp_download{temp_ext}"
            
            # Download file into cache
            img_req = urllib.request.Request(image_url, headers=headers)
            with urllib.request.urlopen(img_req) as img_resp:
                img_data = img_resp.read()
                
            # Verify file integrity (must be non-empty)
            if len(img_data) < 100:
                print("[-] WARNING: Downloaded file is too small to be a valid image, skipping.")
                continue
                
            with open(temp_cache_file, "wb") as f:
                f.write(img_data)
                
            # Move and rename deterministically
            deterministic_name = f"label_{current_index:06d}.jpg"
            deterministic_txt = f"label_{current_index:06d}.txt"
            
            dest_img_path = TARGET_DIR / deterministic_name
            dest_txt_path = TARGET_DIR / deterministic_txt
            
            # Move file out of cache
            if temp_cache_file.exists():
                shutil_move(temp_cache_file, dest_img_path)
                
            # Parse expected canonicals
            clean_ingredients = []
            for tok in ingredients_text.split(","):
                clean_tok = tok.strip().lower().rstrip(".").rstrip(",")
                if clean_tok and not clean_tok.startswith("ingredients"):
                    clean_ingredients.append(clean_tok)
            
            canonical_text = "\n".join(clean_ingredients)
            
            # Generate matching annotations
            with open(dest_txt_path, "w", encoding="utf-8") as tf:
                tf.write(ANNOTATION_TEMPLATE.format(
                    ingredients=ingredients_text,
                    canonical=canonical_text
                ))
                
            print(f"  [+] Saved {deterministic_name} & {deterministic_txt}")
            current_index += 1
            download_count += 1
            
            # Limit download to 3 for subset safety check
            if download_count >= 3:
                break
                
        print(f"[+] Successfully staged and migrated {download_count} images.")
        
    except urllib.error.URLError as e:
        print(f"[-] Network connection error: {e}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"[-] Download pipeline failed: {e}", file=sys.stderr)
        sys.exit(1)

def shutil_move(src_path: Path, dest_path: Path):
    # Safe move implementation
    if dest_path.exists():
        dest_path.unlink()
    src_path.rename(dest_path)

if __name__ == "__main__":
    download_products()
