#!/usr/bin/env python3

class Failure:
    def __init__(self, failure_type: str, stage: str, confidence: float, subset: str, replay_path: str, pipeline_version: str, details: str = ""):
        self.failure_type = failure_type
        self.stage = stage
        self.confidence = float(confidence)
        self.subset = subset
        self.replay_path = str(replay_path) if replay_path else ""
        self.pipeline_version = pipeline_version
        self.details = details

    def to_dict(self) -> dict:
        return {
            "failure_type": self.failure_type,
            "stage": self.stage,
            "confidence": self.confidence,
            "subset": self.subset,
            "replay_path": self.replay_path,
            "pipeline_version": self.pipeline_version,
            "details": self.details
        }

    def __repr__(self) -> str:
        return f"<Failure {self.failure_type} in {self.stage} stage (Confidence: {self.confidence:.2f})>"
