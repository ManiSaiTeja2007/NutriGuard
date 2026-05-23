#!/usr/bin/env python3
import sys
import random
from pathlib import Path

# Fix random seed for reproducibility
random.seed(42)

# Target directories
SCRIPT_DIR = Path(__file__).resolve().parent
BENCHMARK_DIR = SCRIPT_DIR.parent
RAW_DIR = BENCHMARK_DIR / "datasets" / "raw"
CLEAN_DIR = RAW_DIR / "clean_labels"

SYNTH_DIR = BENCHMARK_DIR / "datasets" / "synthetic"
BLUR_DIR = SYNTH_DIR / "generated_blur"
ROT_DIR = SYNTH_DIR / "generated_rotation"
NOISE_DIR = SYNTH_DIR / "generated_noise"
LIGHT_DIR = SYNTH_DIR / "generated_lowlight"

def check_pillow():
    try:
        from PIL import Image, ImageFilter, ImageEnhance
        return True, Image, ImageFilter, ImageEnhance
    except ImportError:
        return False, None, None, None

def copy_annotation_with_tag(src_txt_path: Path, dest_txt_path: Path, tag: str):
    if not src_txt_path.exists():
        return
    with open(src_txt_path, "r", encoding="utf-8") as f:
        content = f.read()
    
    # Replace clean tag with target failure tag
    if "[FAILURE_TAGS]" in content:
        lines = content.split("\n")
        idx = lines.index("[FAILURE_TAGS]")
        # Keep everything up to [FAILURE_TAGS] and rewrite the tags
        new_lines = lines[:idx+1]
        new_lines.append(tag)
        content = "\n".join(new_lines)
    else:
        content += f"\n\n[FAILURE_TAGS]\n{tag}\n"
        
    with open(dest_txt_path, "w", encoding="utf-8") as f:
        f.write(content)

def add_salt_and_pepper_noise(img, Image):
    # Convert to RGB mode if not already
    if img.mode != "RGB":
        img = img.convert("RGB")
    
    pixels = img.load()
    width, height = img.size
    
    # 5% noise density
    num_noise_pixels = int(width * height * 0.05)
    
    for _ in range(num_noise_pixels):
        x = random.randint(0, width - 1)
        y = random.randint(0, height - 1)
        # 50% salt, 50% pepper
        if random.random() < 0.5:
            pixels[x, y] = (255, 255, 255)
        else:
            pixels[x, y] = (0, 0, 0)
            
    return img

def generate_variations():
    print("[*] Starting synthetic dataset generation pipeline...")
    
    # 1. Ensure target folders exist
    BLUR_DIR.mkdir(parents=True, exist_ok=True)
    ROT_DIR.mkdir(parents=True, exist_ok=True)
    NOISE_DIR.mkdir(parents=True, exist_ok=True)
    LIGHT_DIR.mkdir(parents=True, exist_ok=True)
    
    # 2. Check clean base files
    clean_images = list(CLEAN_DIR.glob("*.jpg"))
    if not clean_images:
        print(f"[-] No clean base labels found in: {CLEAN_DIR}")
        print("    Please run prepare_dataset_structure.py first.")
        sys.exit(1)
        
    print(f"[+] Found {len(clean_images)} base images to augment.")
    
    # 3. Check PIL library availability
    has_pil, Image, ImageFilter, ImageEnhance = check_pillow()
    
    if not has_pil:
        print("=" * 60)
        print("[-] WARNING: Python 'Pillow' library is not installed.")
        print("    Pillow is required to render actual image variations.")
        print("    Please install it using:")
        print("       pip install Pillow")
        print("=" * 60)
        print("[!] Running in fallback mock mode: Generating metadata and copy files...")
        
        # Fallback: copy images as-is and generate correct annotations
        for img_path in clean_images:
            base_name = img_path.name
            txt_path = img_path.with_suffix(".txt")
            
            # Blur
            shutil_copy_fallback(img_path, BLUR_DIR / base_name)
            copy_annotation_with_tag(txt_path, BLUR_DIR / f"{img_path.stem}.txt", "blur")
            
            # Rotation
            shutil_copy_fallback(img_path, ROT_DIR / base_name)
            copy_annotation_with_tag(txt_path, ROT_DIR / f"{img_path.stem}.txt", "rotation")
            
            # Noise
            shutil_copy_fallback(img_path, NOISE_DIR / base_name)
            copy_annotation_with_tag(txt_path, NOISE_DIR / f"{img_path.stem}.txt", "noise")
            
            # Lowlight
            shutil_copy_fallback(img_path, LIGHT_DIR / base_name)
            copy_annotation_with_tag(txt_path, LIGHT_DIR / f"{img_path.stem}.txt", "low_light")
            
        print("[+] Mock variations generated successfully.")
        return

    print("[+] Pillow library found. Generating deterministic variations...")
    
    for img_path in clean_images:
        base_name = img_path.name
        txt_path = img_path.with_suffix(".txt")
        
        # Load base image
        try:
            img = Image.open(img_path)
            
            # A. Gaussian Blur (radius = 2)
            blur_img = img.filter(ImageFilter.GaussianBlur(radius=2))
            blur_img.save(BLUR_DIR / base_name)
            copy_annotation_with_tag(txt_path, BLUR_DIR / f"{img_path.stem}.txt", "blur")
            
            # B. 90/180/270 Rotation (Choose 90 degrees deterministically)
            rot_img = img.rotate(90, expand=True)
            rot_img.save(ROT_DIR / base_name)
            copy_annotation_with_tag(txt_path, ROT_DIR / f"{img_path.stem}.txt", "rotation")
            
            # C. Salt-and-Pepper Noise
            noise_img = add_salt_and_pepper_noise(img.copy(), Image)
            noise_img.save(NOISE_DIR / base_name)
            copy_annotation_with_tag(txt_path, NOISE_DIR / f"{img_path.stem}.txt", "noise")
            
            # D. Brightness Reduction (low light: 30% of original brightness)
            enhancer = ImageEnhance.Brightness(img)
            light_img = enhancer.enhance(0.3)
            light_img.save(LIGHT_DIR / base_name)
            copy_annotation_with_tag(txt_path, LIGHT_DIR / f"{img_path.stem}.txt", "low_light")
            
            print(f"  [+] Augmentations completed for: {base_name}")
            
        except Exception as e:
            print(f"  [-] Failed to process {base_name}: {e}", file=sys.stderr)
            
    print("[+] Synthetic dataset variations generated successfully.")

def shutil_copy_fallback(src: Path, dest: Path):
    if dest.exists():
        dest.unlink()
    with open(src, "rb") as f_src:
        data = f_src.read()
    with open(dest, "wb") as f_dest:
        f_dest.write(data)

if __name__ == "__main__":
    generate_variations()
