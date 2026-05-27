import os
from PIL import Image, ImageDraw, ImageFont

def generate_image(filename, text, size=(300, 150)):
    # Create a RGB image with a gradient background
    img = Image.new("RGB", size, color=(240, 240, 240))
    draw = ImageDraw.Draw(img)
    
    # Draw a gradient/pattern for high entropy
    for i in range(size[0]):
        for j in range(size[1]):
            r = (i + j) % 256
            g = (i * 2) % 256
            b = (j * 3) % 256
            draw.point((i, j), fill=(r, g, b))
            
    # Draw simple text using default font/lines (no external font required)
    draw.text((20, 40), "NUTRIGUARD BENCHMARK", fill=(255, 255, 255))
    draw.text((20, 70), text, fill=(255, 255, 0))
    draw.text((20, 100), "STATUS: FAILURE CASE", fill=(255, 0, 0))
    
    # Ensure directory exists
    os.makedirs(os.path.dirname(filename), exist_ok=True)
    img.save(filename, "PNG")
    print(f"Generated {filename} (Size: {os.path.getsize(filename)} bytes)")

def main():
    base_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    failure_cases_dir = os.path.join(base_dir, "semantic", "failure_cases")
    
    generate_image(os.path.join(failure_cases_dir, "fail_001.png"), "OCR TARGET: INS 50O(ii)")
    generate_image(os.path.join(failure_cases_dir, "fail_002.png"), "OCR TARGET: tuimeric")

if __name__ == "__main__":
    main()
