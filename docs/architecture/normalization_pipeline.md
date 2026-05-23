# NutriGuard - Normalization & Extraction Pipeline

This document details the text cleaning, section extraction, and tokenization phases in the NutriGuard ingredient intelligence engine.

## Pipeline Flow

```
Raw OCR Text
     │
     ▼ (TextNormalizer)
Cleaned & Normalized lowercase text
     │
     ▼ (IngredientExtractor.extractRawSection)
Ingredient list substring (stripped of headers)
     │
     ▼ (IngredientExtractor.tokenize)
Parenthesis-aware tokenized list (with Spacing Recovery if no delimiters exist)
```

---

## 1. Text Normalization (`TextNormalizer`)

The [TextNormalizer](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/normalization/TextNormalizer.kt) object formats noisy raw OCR output into a standard lowercase form:
- **Hyphen Recovery**: Matches trailing hyphens followed by whitespace and newlines (`-\s*[\r\n]+\s*` and `-\s+`) and replaces them with spaces to rebuild words split across lines.
- **Linebreak Cleanup**: Converts newlines (`\n`), carriage returns (`\r`), and tabs (`\t`) to space characters.
- **Junk Stripping**: Removes common noisy characters like `|`, `*`, `_`, `•`, `~`, `^`, `\`, `/`, `#`, `@`, `<`, and `>`.
- **Delimiter Spacing**: Standardizes spaces around list delimiters (`,`, `;`, `:`) to simplify extraction checks.

---

## 2. Section Extraction (`IngredientExtractor.extractRawSection`)

The [IngredientExtractor](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/ingredient/IngredientExtractor.kt) isolates ingredients from surrounding text using a predefined list of headers (e.g. `"ingredients:"`, `"contains:"`, `"other ingredients:"`). 
If a header is found, it extracts all characters following it. Otherwise, it defaults to the full input string.

---

## 3. Delimiter Tokenization & Parenthesis-Aware Split

Ingredients are separated by splitting the raw text by commas (`,`) and semicolons (`;`) under the following safety rules:
- **Depth Safety**: Keeps delimiters enclosed in parentheses `( )` or brackets `[ ]` at the same token depth level (e.g., `"wheat flour (water, yeast, enriched flour)"` is parsed as a single item, avoiding splits inside the parentheses).
- **Punctuation Trimming**: Cleans tokens by stripping leading and trailing dots, colons, commas, and duplicate spaces.

---

## 4. Spacing Recovery (Fallback)

If the extracted section has no top-level list delimiters (commas or semicolons), the pipeline falls back to space-based tokenization. It then attempts to merge adjacent words if they match a known vocabulary entry or canonical alias (e.g., reconstructing `"citric acid"` from `"citric"` and `"acid"`).
- It attempts multi-word matches from 4 words down to 2 words.
- If a match is found in the static/learned vocabulary or standard alias list, the words are rejoined.
- Otherwise, they remain separate single-word tokens.
