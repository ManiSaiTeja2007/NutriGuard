# Packaging Intelligence Taxonomy

This document establishes the authoritative domain definitions for food product packaging classification. By structuring packaging text into functional domains, the system prevents token contamination and routes data to specialized, context-aware interpreters.

---

## 1. Domain Taxonomy Catalog

### INGREDIENTS
* **Purpose**: Core composition mapping for food items.
* **Observed Real Example**: 
  > `"INGREDIENTS: Soya base (Water, Hulled soya beans (8.7%)), Apple extract, Sea salt."`
* **Routing Owner**: `SemanticRouter`
* **Future Processor**: `SpecializedInterpretationStage` (executes `SemanticPipeline` and spelling corrections)
* **UI Consumer**: Ingredients flat list cards, category color-codings, and additive warnings.
* **Separation Rule**: **MUST NEVER** consume allergen advice headers or unrelated marketing copy.

### ALLERGENS
* **Purpose**: Dedicated extraction of allergens and cross-contact risk notices.
* **Observed Real Example**: 
  > `"Contains: Wheat, Milk, Soy."` OR `"May contain peanuts. Made in a facility that also processes tree nuts."`
* **Routing Owner**: `SemanticRouter`
* **Future Processor**: `AllergenInterpreter`
* **UI Consumer**: High-visibility allergen warning badges (e.g. Milk, Soy, Peanut allergen alert overlay).
* **Separation Rule**: Bypasses the comma-split ingredients engine entirely to prevent mapping cross-contact warnings as actual ingredients.

### NUTRITION
* **Purpose**: Parsing of nutritional composition lists and daily value percentages.
* **Observed Real Example**: 
  > `"Calories 250. Total Fat 12g (18% DV). Sodium 470mg (20% DV). Sugars 5g."`
* **Routing Owner**: `SemanticRouter`
* **Future Processor**: `NutritionInterpreter`
* **UI Consumer**: Structured nutrition summary grid cards.
* **Separation Rule**: Bypasses spelling engines for non-vocabulary words to prevent translating metric markers (e.g. "g", "mg") to ingredients.

### STORAGE
* **Purpose**: Identification of temperature, preservation, and shelf-life instructions.
* **Observed Real Example**: 
  > `"Keep refrigerated at 2°C to 6°C. Once opened, consume within 3 days."`
* **Routing Owner**: `SemanticRouter`
* **Future Processor**: `StorageInstructionInterpreter`
* **UI Consumer**: Safety/preservation alerts and storage guidelines card.
* **Separation Rule**: Prevents storage verbs (e.g. "keep", "store") from polluting ingredients.

### MANUFACTURER
* **Purpose**: Extraction of corporate registration, distributor addresses, and country of origin.
* **Observed Real Example**: 
  > `"Produced by Alpro UK Ltd, Latimer, UK."` OR `"Imported by Food Trade Inc, New York, NY."`
* **Routing Owner**: `SemanticRouter`
* **Future Processor**: `PackagingMetadataInterpreter`
* **UI Consumer**: Distributor details popup dialog.
* **Separation Rule**: Stops geographic city/country names from being matched against the ingredient spelling engine.

### MARKETING
* **Purpose**: Brand promises, organic certifications, and non-mandatory claims.
* **Observed Real Example**: 
  > `"Grown in volcanic soils. Non-GMO, hand-harvested almonds representing our commitment to sustainability."`
* **Routing Owner**: `SemanticRouter`
* **Future Processor**: `MarketingInterpreter` (or ignored)
* **UI Consumer**: Secondary product details tab.
* **Separation Rule**: Prevents promotional adjectives (e.g. "love", "hand-harvested") from appearing as flat ingredients.

### WARNINGS
* **Purpose**: Mandatory health risks, age restrictions, and chemical safety statements.
* **Observed Real Example**: 
  > `"WARNING: Accidental overdose of iron-containing products is a leading cause of fatal poisoning in children."`
* **Routing Owner**: `SemanticRouter`
* **Future Processor**: `WarningInterpreter` (or statutory rules evaluator)
* **UI Consumer**: Critical pop-up modal dialog blocks.
* **Separation Rule**: Isolated immediately to trigger safety overrides.

### REGULATORY
* **Purpose**: Statutory license identifiers, organic logos, and regional certification numbers.
* **Observed Real Example**: 
  > `"FSSAI Licence No. 10012022000234."` OR `"US Organic Certification USDA."`
* **Routing Owner**: `SemanticRouter`
* **Future Processor**: `RegulatoryInterpreter`
* **UI Consumer**: Compliance validation badges.
* **Separation Rule**: Prevents numeric ID strings from triggering false additive E-number repairs.
