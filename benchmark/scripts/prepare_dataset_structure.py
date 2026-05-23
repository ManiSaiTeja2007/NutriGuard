#!/usr/bin/env python3
import sys
from pathlib import Path

# Tiny valid 1x1 JPEG image bytes to seed the dataset deterministically
TINY_JPG_BYTES = (
    b'\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00\xff\xdb\x00C\x00\x08\x06\x06\x07\x06\x05\x08\x07\x07\x07\t\t\x08\n\x0c\x14\r\x0c\x0b\x0b\x0c\x19\x12\x13\x0f\x14\x1d\x1a\x1f\x1e\x1d\x1a\x1c\x1c $.\' ",#\x1c\x1c(7),01444\x1f\'9=82<.342\xff\xdb\x00C\x01\t\t\t\x0c\x0b\x0c\x18\r\r\x182!\x1c!22222222222222222222222222222222222222222222222222\xff\xc0\x00\x11\x08\x00\x01\x00\x01\x03\x01"\x00\x02\x11\x01\x03\x11\x01\xff\xc4\x00\x1f\x00\x00\x01\x05\x01\x01\x01\x01\x01\x01\x00\x00\x00\x00\x00\x00\x00\x00\x01\x02\x03\x04\x05\x06\x07\x08\t\n\x0b\xff\xc4\x00\xb5\x10\x00\x02\x01\x03\x03\x02\x04\x03\x05\x05\x04\x04\x00\x00\x01}\x01\x02\x03\x00\x04\x11\x05\x12!1A\x06\x13Qa\x07"q\x142\x81\x91\xa1\x08#B\xb1\xc1\x15R\xd1\xf0$3br\x82\t\n\x16\x17\x18\x19\x1a%&\'()*456789:CDEFGHIJSTUVWXYZcdefghijstuvwxyz\x83\x84\x85\x86\x87\x88\x89\x8a\x92\x93\x94\x95\x96\x97\x98\x99\x9a\xa2\xa3\xa4\xa5\xa6\xa7\xa8\xa9\xaa\xb2\xb3\xb4\xb5\xb6\xb7\xb8\xb9\xba\xc2\xc3\xc4\xc5\xc6\xc7\xc8\xc9\xca\xd2\xd3\xd4\xd5\xd6\xd7\xd8\xd9\xda\xe1\xe2\xe3\xe4\xe5\xe6\xe7\xe8\xe9\xea\xf1\xf2\xf3\xf4\xf5\xf6\xf7\xf8\xf9\xfa\xff\xc4\x00\x1f\x01\x00\x03\x01\x01\x01\x01\x01\x01\x01\x01\x01\x00\x00\x00\x00\x00\x00\x01\x02\x03\x04\x05\x06\x07\x08\t\n\x0b\xff\xc4\x00\xb5\x11\x00\x02\x01\x02\x04\x04\x03\x04\x07\x05\x04\x04\x00\x01\x02w\x00\x01\x02\x03\x11\x04\x05!1\x06\x12AQ\x07aq\x13"2\x81\x08\x14B\x91\xa1\xb1\xc1\t#3R\xf0\x15br\xd1\n\x16$4\xe1%\xf1\x17\x18\x19\x1a&\'()*56789:CDEFGHIJSTUVWXYZcdefghijstuvwxyz\x82\x83\x84\x85\x86\x87\x88\x89\x8a\x92\x93\x94\x95\x96\x97\x98\x99\x9a\xa2\xa3\xa4\xa5\xa6\xa7\xa8\xa9\xaa\xb2\xb3\xb4\xb5\xb6\xb7\xb8\xb9\xba\xc2\xc3\xc4\xc5\xc6\xc7\xc8\xc9\xca\xd2\xd3\xd4\xd5\xd6\xd7\xd8\xd9\xda\xe2\xe3\xe4\xe5\xe6\xe7\xe8\xe9\xea\xf2\xf3\xf4\xf5\xf6\xf7\xf8\xf9\xfa\xff\xda\x00\x0c\x03\x01\x00\x02\x11\x03\x11\x00?\x00\xf7\xfa(\xa2\x80?\xff\xd9'
)


# Text annotation template content
ANNOTATION_CONTENT_TEMPLATE = """[RAW INGREDIENTS]
Ingredients: sugar, salt, citric acid, msg

[EXPECTED CANONICAL]
sugar
salt
citric acid
monosodium glutamate

[NUTRITION VALUES]
Calories: 120
Sodium: 150mg

[FAILURE_TAGS]
clean
baseline
"""

def create_structure():
    print("[*] Initializing NutriGuard Benchmark Dataset structure...")
    
    # Root benchmark path
    script_dir = Path(__file__).resolve().parent
    benchmark_dir = script_dir.parent
    
    # Required subdirectories
    dirs_to_create = [
        # Datasets raw categories
        benchmark_dir / "datasets" / "raw" / "clean_labels",
        benchmark_dir / "datasets" / "raw" / "blurry_labels",
        benchmark_dir / "datasets" / "raw" / "rotated_labels",
        benchmark_dir / "datasets" / "raw" / "low_light",
        benchmark_dir / "datasets" / "raw" / "multilingual",
        benchmark_dir / "datasets" / "raw" / "curved_packaging",
        benchmark_dir / "datasets" / "raw" / "noisy_backgrounds",
        benchmark_dir / "datasets" / "raw" / "partial_occlusion",
        benchmark_dir / "datasets" / "raw" / "handwritten",
        benchmark_dir / "datasets" / "raw" / "difficult_fonts",
        
        # Datasets processed categories
        benchmark_dir / "datasets" / "processed" / "normalized",
        benchmark_dir / "datasets" / "processed" / "resized",
        benchmark_dir / "datasets" / "processed" / "canonical",
        
        # Datasets synthetic categories
        benchmark_dir / "datasets" / "synthetic" / "generated_blur",
        benchmark_dir / "datasets" / "synthetic" / "generated_rotation",
        benchmark_dir / "datasets" / "synthetic" / "generated_noise",
        benchmark_dir / "datasets" / "synthetic" / "generated_lowlight",
        
        # Other infrastructure
        benchmark_dir / "manifests",
        benchmark_dir / "reports",
        benchmark_dir / "replays" / "raw_ocr_outputs",
        benchmark_dir / "replays" / "canonicalization_outputs",
        benchmark_dir / "replays" / "failed_cases",
        benchmark_dir / "replays" / "diff_reports",
        benchmark_dir / "subsets",
        benchmark_dir / "download_cache"
    ]

    for d in dirs_to_create:
        d.mkdir(parents=True, exist_ok=True)
        print(f"  [+] Created: {d.relative_to(benchmark_dir.parent)}")

    print("[+] Folders successfully generated.")
    
    # Seed a few clean labels with corresponding annotation text files
    clean_dir = benchmark_dir / "datasets" / "raw" / "clean_labels"
    print("[*] Seeding sample dataset assets...")
    
    for i in range(1, 6):
        img_name = f"label_{i:06d}.jpg"  # we write tiny JPEG bytes
        txt_name = f"label_{i:06d}.txt"
        
        img_path = clean_dir / img_name
        txt_path = clean_dir / txt_name
        
        # Write tiny valid image bytes
        with open(img_path, "wb") as img_file:
            img_file.write(TINY_JPG_BYTES)
            
        # Write structured annotation
        with open(txt_path, "w", encoding="utf-8") as txt_file:
            txt_file.write(ANNOTATION_CONTENT_TEMPLATE)
            
        print(f"  [+] Seeded: {img_name} & {txt_name}")
        
    print("[+] Seeding completed successfully.")

if __name__ == "__main__":
    create_structure()
