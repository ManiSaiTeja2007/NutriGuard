#!/usr/bin/env python3
import os
import sys
import subprocess
import shutil

# Target paths
BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RAW_DIR = os.path.join(BASE_DIR, "raw", "kaggle_labels")

# Dataset config
KAGGLE_DATASET_NAME = "openfoodfacts/world-food-facts"

def check_kaggle_credentials():
    # Detect home/profile directory
    home_dir = os.path.expanduser("~")
    user_profile = os.environ.get("USERPROFILE")
    
    candidate_paths = []
    if home_dir:
        candidate_paths.append(os.path.join(home_dir, ".kaggle", "kaggle.json"))
    if user_profile:
        candidate_paths.append(os.path.join(user_profile, ".kaggle", "kaggle.json"))
        
    found_path = None
    for path in candidate_paths:
        if os.path.exists(path):
            found_path = path
            break
            
    return found_path

def download_dataset():
    # 1. Verify credentials
    kaggle_json_path = check_kaggle_credentials()
    if not kaggle_json_path:
        print("=" * 60)
        print("[-] ERROR: Kaggle API credentials not found.")
        print("=" * 60)
        print("Please follow these steps to configure your Kaggle credentials:")
        print("1. Sign up or log into your account at https://www.kaggle.com")
        print("2. Navigate to your Profile -> Settings.")
        print("3. Click 'Create New Token' under the API section to download 'kaggle.json'.")
        print("4. Move the downloaded 'kaggle.json' file to:")
        if sys.platform.startswith("win"):
            print("   %USERPROFILE%\\.kaggle\\kaggle.json   (typically C:\\Users\\<username>\\.kaggle\\kaggle.json)")
        else:
            print("   ~/.kaggle/kaggle.json")
        print("5. Ensure file permissions are restricted (e.g. chmod 600 ~/.kaggle/kaggle.json on Unix).")
        print("=" * 60)
        sys.exit(1)

    print(f"[+] Found Kaggle API credentials at: {kaggle_json_path}")
    
    # 2. Check if Kaggle CLI is installed
    kaggle_cmd = shutil.which("kaggle")
    if not kaggle_cmd:
        print("[-] ERROR: 'kaggle' command line utility is not installed on your system PATH.")
        print("    Please install it using: pip install kaggle")
        sys.exit(1)
        
    print(f"[+] Found Kaggle CLI executable: {kaggle_cmd}")
    
    # 3. Create target directory
    os.makedirs(RAW_DIR, exist_ok=True)
    
    # 4. Invoke CLI download
    cmd = [
        "kaggle", "datasets", "download",
        "-d", KAGGLE_DATASET_NAME,
        "-p", RAW_DIR,
        "--unzip"
    ]
    
    print(f"[*] Running command: {' '.join(cmd)}")
    try:
        result = subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        print("[+] Kaggle dataset download succeeded!")
        print(result.stdout)
        
        # Manifest placeholder update
        manifest_file = os.path.join(BASE_DIR, "manifests", "kaggle_labels_manifest.json")
        import json
        from datetime import datetime
        
        manifest = {
            "dataset_name": KAGGLE_DATASET_NAME,
            "source": f"https://www.kaggle.com/datasets/{KAGGLE_DATASET_NAME}",
            "downloaded_at": datetime.utcnow().isoformat() + "Z",
            "subset_size": "full",
            "file_count": len(os.listdir(RAW_DIR)),
            "preprocessing_lineage": {
                "raw_status": "downloaded",
                "annotation_version": "1.0.0",
                "normalized_version": "1.0.0"
            }
        }
        with open(manifest_file, "w", encoding="utf-8") as mf:
            json.dump(manifest, mf, indent=2)
        print(f"[+] Generated manifest at: {manifest_file}")
        
    except subprocess.CalledProcessError as e:
        print(f"[-] Kaggle CLI execution failed with exit code {e.returncode}:", file=sys.stderr)
        print(e.stderr, file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    download_dataset()
