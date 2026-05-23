#!/usr/bin/env python3
import json
import sys
from pathlib import Path

# Setup paths for import parity
PROJECT_ROOT = Path(__file__).resolve().parents[4]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

class ConfigLoader:
    DEFAULT_CONFIG_PATH = PROJECT_ROOT / "benchmark" / "config" / "benchmark_config.json"

    @staticmethod
    def load_config(config_path: Path = None) -> dict:
        if config_path is None:
            config_path = ConfigLoader.DEFAULT_CONFIG_PATH

        if not config_path.exists():
            # Return default dictionary if config file does not exist
            return {
                "pipeline_version": "1.0.0",
                "benchmark_schema_version": "1.0.0",
                "dataset_version": "1.0.0",
                "deterministic_seed": 42,
                "simulate_ocr_corruption": True,
                "stage_filtering": None,
                "subset_selection": "all",
                "replay": {
                    "enabled": True,
                    "save_all_replays": True,
                    "save_failed_replays": True
                },
                "report": {
                    "json": True,
                    "csv": True,
                    "markdown": True,
                    "output_dir": "benchmark/reports",
                    "replays_dir": "benchmark/replays"
                }
            }

        try:
            with open(config_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            print(f"[-] WARNING: Failed to load config file: {e}. Using defaults.", file=sys.stderr)
            return ConfigLoader.load_config(Path("/does_not_exist"))
