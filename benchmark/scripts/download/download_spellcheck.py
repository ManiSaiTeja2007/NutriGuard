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
RAW_DIR = os.path.join(BASE_DIR, "raw", "spellcheck")
MANIFESTS_DIR = os.path.join(BASE_DIR, "manifests")

# Ensure directories exist
os.makedirs(RAW_DIR, exist_ok=True)
os.makedirs(MANIFESTS_DIR, exist_ok=True)

# Dataset configuration
SPELLCHECK_URL = "https://norvig.com/spell-testset1.txt"
OUTPUT_FILE = os.path.join(RAW_DIR, "norvig_spell_testset1.txt")
MANIFEST_FILE = os.path.join(MANIFESTS_DIR, "spellcheck_manifest.json")

def download_dataset():
    print(f"[*] Downloading spelling correction evaluation dataset...")
    print(f"    URL: {SPELLCHECK_URL}")
    
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
    }
    
    req = urllib.request.Request(SPELLCHECK_URL, headers=headers)
    
    try:
        with urllib.request.urlopen(req) as response:
            data = response.read()
            
            # Save file
            with open(OUTPUT_FILE, "wb") as f:
                f.write(data)
            print(f"[+] Saved raw data to: {OUTPUT_FILE}")
            
            # Calculate SHA-256
            sha256_hash = hashlib.sha256(data).hexdigest()
            print(f"[+] File SHA-256: {sha256_hash}")
            
            # Count lines
            line_count = len(data.split(b"\n"))
            print(f"[+] Counted {line_count} spelling correction pairs.")
            
            # Generate manifest
            manifest = {
                "dataset_name": "Peter Norvig Spell Test Set 1",
                "source": SPELLCHECK_URL,
                "downloaded_at": datetime.utcnow().isoformat() + "Z",
                "subset_size": line_count,
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
    except Exception as e:
        print(f"[-] Download failed: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    download_dataset()
