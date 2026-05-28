#!/usr/bin/env python3
import os
import sys
import json
import csv
import urllib.request
import urllib.error
import hashlib
import struct
import shutil
from datetime import datetime

# Root paths
BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SEMANTIC_DIR = os.path.join(BASE_DIR, "semantic")
REAL_WORLD_DIR = os.path.join(SEMANTIC_DIR, "real_world")
MANIFESTS_DIR = os.path.join(SEMANTIC_DIR, "manifests")
FAILURE_CASES_DIR = os.path.join(SEMANTIC_DIR, "failure_cases")
QUARANTINE_DIR = os.path.join(BASE_DIR, "quarantine")
REPORTS_DIR = os.path.join(BASE_DIR, "reports")
AUDIT_DIR = os.path.join(REPORTS_DIR, "dataset_audit")
HEALTH_DIR = os.path.join(REPORTS_DIR, "dataset_health")

# Ensure target directories exist
os.makedirs(REAL_WORLD_DIR, exist_ok=True)
os.makedirs(MANIFESTS_DIR, exist_ok=True)
os.makedirs(QUARANTINE_DIR, exist_ok=True)
os.makedirs(AUDIT_DIR, exist_ok=True)
os.makedirs(HEALTH_DIR, exist_ok=True)
os.makedirs(FAILURE_CASES_DIR, exist_ok=True)

# Target Files
INGREDIENTS_JSON = os.path.join(REAL_WORLD_DIR, "ingredients.json")
ADDITIVES_JSON = os.path.join(REAL_WORLD_DIR, "additives.json")
PRODUCTS_CSV = os.path.join(REAL_WORLD_DIR, "products.csv")
VERSIONS_JSON = os.path.join(MANIFESTS_DIR, "dataset_versions.json")
AUDIT_LOG_JSON = os.path.join(AUDIT_DIR, "audit_log.json")
COVERAGE_METRICS_JSON = os.path.join(AUDIT_DIR, "coverage_metrics.json")
HEALTH_REPORT_JSON = os.path.join(HEALTH_DIR, "health_report.json")

# URLs
INGREDIENTS_TAXONOMY_URL = "https://static.openfoodfacts.org/data/taxonomies/ingredients.json"
ADDITIVES_TAXONOMY_URL = "https://static.openfoodfacts.org/data/taxonomies/additives.json"
PRODUCT_SEARCH_URL = "https://world.openfoodfacts.org/api/v2/search?search_terms=ingredients&fields=code,product_name,ingredients_text,categories,additives_tags,ingredients_tags&page_size=50"

STANDARD_CHECKSUMS = {
    "ingredients.json": "b42a1628d9d50c9a5623fb46460ec62782ad939f55d4820cf27a99f1de6fb696",
    "additives.json": "7ad77ce8ca840304262e5a28f48c9fa70df730c54a5b6237ca438d5af3461361"
}

audit_events = []
downloaded_files_count = 0
verified_files_count = 0
failed_downloads_count = 0
quarantine_events_count = 0

def log_audit(event_type, filepath, message):
    timestamp = datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
    event = {
        "timestamp": timestamp,
        "event_type": event_type,
        "filepath": os.path.relpath(filepath, BASE_DIR) if os.path.exists(filepath) else filepath,
        "message": message
    }
    audit_events.append(event)
    print(f"[{event_type}] {message}")

def calculate_sha256(filepath):
    sha256_hash = hashlib.sha256()
    with open(filepath, "rb") as f:
        for byte_block in iter(lambda: f.read(4096), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()

def get_png_size(filepath):
    try:
        with open(filepath, 'rb') as f:
            data = f.read(24)
            if data[:8] == b'\x89PNG\r\n\x1a\n':
                w, h = struct.unpack('>II', data[16:24])
                return w, h
    except Exception:
        pass
    return None

def validate_image_file(filepath):
    if not os.path.exists(filepath):
        return False, "File does not exist"
    
    size = os.path.getsize(filepath)
    if size < 100:
        return False, f"Placeholder detected: size is too small ({size} bytes)"
        
    try:
        with open(filepath, 'rb') as f:
            header = f.read(1024)
            if b'<html' in header.lower() or b'<!doctype html' in header.lower():
                return False, "HTML masquerading as image"
    except Exception as e:
        return False, f"Read error: {e}"
        
    size_dims = get_png_size(filepath)
    if not size_dims:
        return False, "Not a valid PNG or corrupt header"
        
    w, h = size_dims
    if w <= 1 or h <= 1:
        return False, f"Invalid dimensions: {w}x{h} pixels (placeholder)"
        
    # Calculate simple byte entropy (variation check)
    try:
        with open(filepath, 'rb') as f:
            content = f.read()
            unique_bytes = len(set(content))
            if unique_bytes < 5:
                return False, f"Low-entropy solid-color placeholder detected ({unique_bytes} unique bytes)"
    except Exception:
        pass
        
    return True, "Valid PNG image"

def quarantine_file(filepath, reason):
    global quarantine_events_count
    os.makedirs(QUARANTINE_DIR, exist_ok=True)
    filename = os.path.basename(filepath)
    dest = os.path.join(QUARANTINE_DIR, f"{datetime.now().strftime('%Y%m%d%H%M%S')}_{filename}")
    if os.path.exists(filepath):
        shutil.move(filepath, dest)
        quarantine_events_count += 1
        log_audit("QUARANTINE", dest, f"Moved corrupt/invalid file {filename} to quarantine due to: {reason}")
    else:
        log_audit("QUARANTINE", dest, f"Quarantined missing file placeholder due to: {reason}")

def download_file_resumable(url, dest_path, description):
    global downloaded_files_count, failed_downloads_count
    
    # 1. Skip-existing if checksum matches expected standard checksum
    expected_checksum = STANDARD_CHECKSUMS.get(os.path.basename(dest_path))
    if os.path.exists(dest_path):
        current_checksum = calculate_sha256(dest_path)
        if expected_checksum and current_checksum == expected_checksum:
            log_audit("SKIP", dest_path, f"{description} already exists with matching standard checksum.")
            return current_checksum, False

    log_audit("DOWNLOAD_START", dest_path, f"Downloading {description} from {url}")
    headers = {
        'User-Agent': 'NutriGuardBenchmarkTool/1.0 (manis@example.com)'
    }
    
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req) as response:
            data = response.read()
            with open(dest_path, "wb") as f:
                f.write(data)
            checksum = calculate_sha256(dest_path)
            downloaded_files_count += 1
            log_audit("DOWNLOAD_SUCCESS", dest_path, f"Successfully downloaded {description} (SHA-256: {checksum})")
            return checksum, False
    except Exception as e:
        failed_downloads_count += 1
        log_audit("WARNING", dest_path, f"Download failed for {description}: {e}")
        
        # Safe fallback: write fallback manifest tagging but DO NOT silently copy fake data if not present
        if os.path.exists(dest_path) and os.path.getsize(dest_path) > 0:
            log_audit("FALLBACK_USED", dest_path, f"Preserving existing file on disk as fallback.")
            return calculate_sha256(dest_path), True
        
        log_audit("CRITICAL", dest_path, f"No fallback file available for {description}. Acquisition FAILED.")
        return None, True

def fetch_real_products_csv():
    global downloaded_files_count, failed_downloads_count
    log_audit("DOWNLOAD_START", PRODUCTS_CSV, f"Querying search API for products from {PRODUCT_SEARCH_URL}")
    
    headers = {
        'User-Agent': 'NutriGuardBenchmarkTool/1.0 (manis@example.com)'
    }
    req = urllib.request.Request(PRODUCT_SEARCH_URL, headers=headers)
    try:
        with urllib.request.urlopen(req) as response:
            data = response.read()
            json_data = json.loads(data.decode('utf-8'))
            products = json_data.get("products", [])
            
            with open(PRODUCTS_CSV, mode="w", newline="", encoding="utf-8") as csv_file:
                fieldnames = ["code", "product_name", "ingredients_text", "categories", "additives_tags", "ingredients_tags"]
                writer = csv.DictWriter(csv_file, fieldnames=fieldnames)
                writer.writeheader()
                for prod in products:
                    writer.writerow({
                        "code": prod.get("code", ""),
                        "product_name": prod.get("product_name", ""),
                        "ingredients_text": prod.get("ingredients_text", ""),
                        "categories": prod.get("categories", ""),
                        "additives_tags": ",".join(prod.get("additives_tags", [])),
                        "ingredients_tags": ",".join(prod.get("ingredients_tags", []))
                    })
            checksum = calculate_sha256(PRODUCTS_CSV)
            downloaded_files_count += 1
            log_audit("DOWNLOAD_SUCCESS", PRODUCTS_CSV, f"Generated products.csv from search API (SHA-256: {checksum})")
            return checksum, False
    except Exception as e:
        failed_downloads_count += 1
        log_audit("WARNING", PRODUCTS_CSV, f"Failed to retrieve online products: {e}")
        
        if os.path.exists(PRODUCTS_CSV) and os.path.getsize(PRODUCTS_CSV) > 0:
            log_audit("FALLBACK_USED", PRODUCTS_CSV, "Preserving existing products.csv as fallback.")
            return calculate_sha256(PRODUCTS_CSV), True
            
        log_audit("CRITICAL", PRODUCTS_CSV, "No fallback products.csv available. Acquisition FAILED.")
        return None, True

def main():
    global verified_files_count
    
    # Expected files count (ingredients.json, additives.json, products.csv, fail_001.png..fail_004.png)
    expected_files = 7
    
    # 1. Download taxonomies
    ing_sha, ing_fallback = download_file_resumable(INGREDIENTS_TAXONOMY_URL, INGREDIENTS_JSON, "Ingredients Taxonomy")
    add_sha, add_fallback = download_file_resumable(ADDITIVES_TAXONOMY_URL, ADDITIVES_JSON, "Additives Taxonomy")
    prod_sha, prod_fallback = fetch_real_products_csv()
    
    # Validate taxonomies schemas
    ing_verified = False
    if ing_sha and not ing_fallback:
        try:
            with open(INGREDIENTS_JSON, "r", encoding="utf-8") as f:
                json.load(f)
            ing_verified = True
            verified_files_count += 1
            log_audit("VERIFY", INGREDIENTS_JSON, "Ingredients taxonomy JSON verified successfully.")
        except Exception as e:
            quarantine_file(INGREDIENTS_JSON, f"Invalid JSON format: {e}")
            ing_sha = None
            
    add_verified = False
    if add_sha and not add_fallback:
        try:
            with open(ADDITIVES_JSON, "r", encoding="utf-8") as f:
                json.load(f)
            add_verified = True
            verified_files_count += 1
            log_audit("VERIFY", ADDITIVES_JSON, "Additives taxonomy JSON verified successfully.")
        except Exception as e:
            quarantine_file(ADDITIVES_JSON, f"Invalid JSON format: {e}")
            add_sha = None

    prod_verified = False
    if prod_sha and not prod_fallback:
        try:
            with open(PRODUCTS_CSV, "r", encoding="utf-8") as f:
                reader = csv.reader(f)
                headers = next(reader)
                if "ingredients_text" in headers:
                    prod_verified = True
                    verified_files_count += 1
                    log_audit("VERIFY", PRODUCTS_CSV, "Products CSV verified successfully.")
                else:
                    quarantine_file(PRODUCTS_CSV, "Missing ingredients_text column header")
                    prod_sha = None
        except Exception as e:
            quarantine_file(PRODUCTS_CSV, f"Invalid CSV format: {e}")
            prod_sha = None

    # Validate failure images
    fail_images = ["fail_001.png", "fail_002.png", "fail_003.png", "fail_004.png"]
    img_states = {}
    for img in fail_images:
        path = os.path.join(FAILURE_CASES_DIR, img)
        valid, msg = validate_image_file(path)
        if valid:
            verified_files_count += 1
            img_states[img] = {"verified": True, "fallback": False, "checksum": calculate_sha256(path)}
            log_audit("VERIFY", path, f"Image {img} verified successfully.")
        else:
            quarantine_file(path, msg)
            img_states[img] = {"verified": False, "fallback": True, "checksum": None}

    # Generate Manifest dataset_versions.json
    download_date = datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
    versions = {}
    
    versions["openfoodfacts_ingredients"] = {
        "dataset_type": "FALLBACK" if ing_fallback else "REAL_WORLD",
        "source_url": INGREDIENTS_TAXONOMY_URL,
        "download_timestamp": download_date,
        "checksum_sha256": ing_sha or "",
        "verified": ing_verified,
        "calibration_eligible": ing_verified and not ing_fallback,
        "corruption_detected": ing_sha is None,
        "fallback_used": ing_fallback
    }

    versions["openfoodfacts_additives"] = {
        "dataset_type": "FALLBACK" if add_fallback else "REAL_WORLD",
        "source_url": ADDITIVES_TAXONOMY_URL,
        "download_timestamp": download_date,
        "checksum_sha256": add_sha or "",
        "verified": add_verified,
        "calibration_eligible": add_verified and not add_fallback,
        "corruption_detected": add_sha is None,
        "fallback_used": add_fallback
    }

    versions["openfoodfacts_products"] = {
        "dataset_type": "FALLBACK" if prod_fallback else "REAL_WORLD",
        "source_url": PRODUCT_SEARCH_URL,
        "download_timestamp": download_date,
        "checksum_sha256": prod_sha or "",
        "verified": prod_verified,
        "calibration_eligible": prod_verified and not prod_fallback,
        "corruption_detected": prod_sha is None,
        "fallback_used": prod_fallback
    }

    for img, state in img_states.items():
        versions[img] = {
            "dataset_type": "FALLBACK" if state["fallback"] else "REAL_WORLD",
            "source_url": f"local://failure_cases/{img}",
            "download_timestamp": download_date,
            "checksum_sha256": state["checksum"] or "",
            "verified": state["verified"],
            "calibration_eligible": state["verified"],
            "corruption_detected": not state["verified"],
            "fallback_used": state["fallback"]
        }

    with open(VERSIONS_JSON, "w", encoding="utf-8") as f:
        json.dump(versions, f, indent=2)
    log_audit("MANIFEST", VERSIONS_JSON, "Updated dataset versions manifest.")

    # Coverage Metrics
    coverage_percent = (verified_files_count / expected_files) * 100
    metrics = {
        "expected_files": expected_files,
        "downloaded_files": downloaded_files_count,
        "verified_files": verified_files_count,
        "coverage_percent": round(coverage_percent, 2)
    }
    with open(COVERAGE_METRICS_JSON, "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2)
    
    # Health Report
    health = {
        "real_world_images": 4 - sum([1 for state in img_states.values() if state["fallback"]]),
        "synthetic_images": 0,
        "mock_images": sum([1 for state in img_states.values() if state["fallback"]]),
        "verified_checksums": verified_files_count,
        "failed_downloads": failed_downloads_count,
        "corrupt_images": sum([1 for state in img_states.values() if not state["verified"]]),
        "placeholder_images": sum([1 for state in img_states.values() if not state["verified"]]),
        "calibration_ready": ing_verified and add_verified and prod_verified
    }
    with open(HEALTH_REPORT_JSON, "w", encoding="utf-8") as f:
        json.dump(health, f, indent=2)

    # Save Audit Logs
    with open(AUDIT_LOG_JSON, "w", encoding="utf-8") as f:
        json.dump(audit_events, f, indent=2)
        
    print(f"[+] Coverage percent: {metrics['coverage_percent']}%")

if __name__ == "__main__":
    main()
