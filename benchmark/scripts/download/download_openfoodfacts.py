#!/usr/bin/env python3
import os
import sys
import json
import urllib.request
import urllib.error
import hashlib
from datetime import datetime

# Target paths
BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RAW_DIR = os.path.join(BASE_DIR, "raw", "openfoodfacts")
MANIFESTS_DIR = os.path.join(BASE_DIR, "manifests")

# Ensure directories exist
os.makedirs(RAW_DIR, exist_ok=True)
os.makedirs(MANIFESTS_DIR, exist_ok=True)

# Dataset configuration
OFF_SEARCH_URL = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=ingredients&json=true&page_size=50"
OUTPUT_FILE = os.path.join(RAW_DIR, "off_products_subset.json")
MANIFEST_FILE = os.path.join(MANIFESTS_DIR, "openfoodfacts_manifest.json")

def download_dataset():
    print(f"[*] Querying OpenFoodFacts Search API for subset data...")
    print(f"    URL: {OFF_SEARCH_URL}")
    
    headers = {
        'User-Agent': 'NutriGuardBenchmarkTool/1.0 (manis@example.com)'
    }
    
    req = urllib.request.Request(OFF_SEARCH_URL, headers=headers)
    
    try:
        with urllib.request.urlopen(req) as response:
            data = response.read()
            
            # Verify valid JSON
            json_data = json.loads(data.decode('utf-8'))
            product_count = len(json_data.get("products", []))
            print(f"[+] Download complete. Fetched {product_count} products.")
            
            # Save file
            with open(OUTPUT_FILE, "wb") as f:
                f.write(data)
            print(f"[+] Saved raw data to: {OUTPUT_FILE}")
            
            # Calculate SHA-256
            sha256_hash = hashlib.sha256(data).hexdigest()
            print(f"[+] File SHA-256: {sha256_hash}")
            
            # Generate manifest
            manifest = {
                "dataset_name": "OpenFoodFacts Product Search Subset",
                "source": OFF_SEARCH_URL,
                "downloaded_at": datetime.utcnow().isoformat() + "Z",
                "subset_size": product_count,
                "file_count": 1,
                "files": [
                    {
                        "path": os.path.relpath(OUTPUT_FILE, BASE_DIR),
                        "size_bytes": len(data),
                        "checksum_sha256": sha256_hash
                    }
                ],
                "preprocessing_lineage": {
                    "raw_status": "downloaded",
                    "annotation_version": "1.0.0",
                    "normalized_version": "1.0.0"
                }
            }
            
            with open(MANIFEST_FILE, "w", encoding="utf-8") as mf:
                json.dump(manifest, mf, indent=2)
            print(f"[+] Generated manifest at: {MANIFEST_FILE}")
            
    except urllib.error.URLError as e:
        print(f"[-] Network connection error: {e}", file=sys.stderr)
        sys.exit(1)
    except json.JSONDecodeError as e:
        print(f"[-] Invalid JSON response parsed: {e}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"[-] Download failed: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    download_dataset()
