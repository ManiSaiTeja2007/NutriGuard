import os

target = "debugConfig"
for root, dirs, files in os.walk("."):
    if ".gradle" in root or ".idea" in root or "build" in root:
        continue
    for file in files:
        if file.endswith((".kts", ".properties", ".xml", ".pro")):
            path = os.path.join(root, file)
            try:
                with open(path, "r", encoding="utf-8") as f:
                    content = f.read()
                    if target in content:
                        print(f"Found '{target}' in: {path}")
            except Exception as e:
                pass
