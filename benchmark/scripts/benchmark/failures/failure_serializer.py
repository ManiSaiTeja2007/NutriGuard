#!/usr/bin/env python3
import json
from benchmark.scripts.benchmark.failures.failure_models import Failure

class FailureSerializer:
    @staticmethod
    def serialize(failures: list) -> list:
        """Converts a list of Failure objects to a list of dictionaries."""
        return [f.to_dict() for f in failures]

    @staticmethod
    def serialize_to_json(failures: list, indent: int = 2) -> str:
        """Converts a list of Failure objects to a JSON-formatted string."""
        return json.dumps(FailureSerializer.serialize(failures), indent=indent)

    @staticmethod
    def deserialize(data: list) -> list:
        """Converts a list of dictionaries to a list of Failure objects."""
        failures = []
        for item in data:
            failures.append(Failure(
                failure_type=item.get("failure_type"),
                stage=item.get("stage"),
                confidence=item.get("confidence", 0.0),
                subset=item.get("subset"),
                replay_path=item.get("replay_path"),
                pipeline_version=item.get("pipeline_version", "1.0.0"),
                details=item.get("details", "")
            ))
        return failures
