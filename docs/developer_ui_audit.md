# Developer UI Audit — Stage 13.0E

This document records the visibility classifications of all debug, timing, latency, and diagnostics elements in the NutriGuard application to ensure a clean production experience.

## 1. UI Elements Classification

We audited all diagnostics features and classified them into three visibility tiers:
- **`Production Visible`**: Visible to all users.
- **`Developer Visible`**: Rendered only when both `BuildCapabilities.isDeveloperBuild` is true AND the Developer Options preference (`AppSettings.showOverlays`) is enabled.
- **`Hidden`**: Temporary developers logs or obsolete overlays removed entirely.

| UI Component | Screen | Classification | Gate Condition / Implementation |
| :--- | :--- | :---: | :--- |
| **Allergen Warnings & Category Badges** | `ResultsScreen` | **Production Visible** | Standard semantic analysis result data shown to all users. |
| **Developer Tools Menu Icon** | `ResultsScreen` (Top Bar) | **Developer Visible** | Shown only if `isDeveloperModeActive` is true. Toggles the expander panel. |
| **ExpandableDeveloperSection** | `ResultsScreen` | **Developer Visible** | `com.example.ui.features.developer.components.ExpandableDeveloperSection` gated by `isDeveloperModeActive && devToolsExpanded`. |
| **Diagnostics Panel** | `ResultsScreen` | **Developer Visible** | Gated by `isDeveloperModeActive` (contains Raw OCR text, Normalized text, and latency metrics table). |
| **Ingestion Trace View** | `ResultsScreen` | **Developer Visible** | Collapsible trace per ingredient item, gated by `showTraceOption = BuildCapabilities.isDeveloperBuild && AppSettings.showOverlays`. |
| **Camera Preview Overlays** | `ScanScreen` | **Developer Visible** | Bounding box preview overlays and stats panels gated by `FeatureFlags.showOverlays && AppSettings.showOverlays`. |
| **Dev Console / Replay Viewer** | Sidebar Menu | **Developer Visible** | Menu items and screen routes gated by `BuildCapabilities.isDeveloperBuild`. Redirects to `HomeScreen` in production. |
| **Diagnostics Badges / Failures** | `ResultsScreen` | **Developer Visible** | Bypassed when `showTraceOption` is false. Normal users do not see internal parser failure types. |

---

## 2. Developer Mode Enforcements

1. **State Centralization**: `isDeveloperModeActive` evaluates `BuildCapabilities.isDeveloperBuild && AppSettings.showOverlays`.
2. **True Isolation**: When `isDeveloperModeActive` is false (either because it is a production release flavor or because "Show Live OCR Overlays" / developer options are toggled off in settings):
   - No diagnostic panels are compiled or rendered in `ResultsScreen`.
   - No timing tables or stage latency cards are visible.
   - Bounding boxes are disabled.
   - Ingredients are displayed purely as clean canonical lists with allergy warning headers.
3. **Menu Redirection**: The Sidebar modal navigation drawer checks `BuildCapabilities.isDeveloperBuild` at launch. If a user attempts to deep-link or access the `DevConsole`, `ReplayViewer`, or `BenchmarkRunner` routes in a production build, they are automatically redirected back to `HomeScreen`.
4. **No Overhead**: Decoupling diagnostics from production rendering minimizes Compose recomposition overhead, preventing visual stutter during transitions.
