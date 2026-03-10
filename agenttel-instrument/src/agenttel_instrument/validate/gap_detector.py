"""Detect gaps between agenttel.yml config and actual source code endpoints."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import yaml

from ..analyze.endpoint_detector import Endpoint


@dataclass
class Gap:
    """A detected configuration gap."""

    type: str  # "uncovered_endpoint" | "stale_config"
    details: str


class GapDetector:
    """Compares configured operations against detected source endpoints."""

    def detect(self, config_path: Path, endpoints: list[Endpoint]) -> list[Gap]:
        gaps: list[Gap] = []

        try:
            with open(config_path, encoding="utf-8") as f:
                config = yaml.safe_load(f)
        except (yaml.YAMLError, OSError):
            return gaps

        agenttel = config.get("agenttel", {}) if isinstance(config, dict) else {}
        configured_ops = set(agenttel.get("operations", {}).keys())

        # Build set of detected operation names (e.g. "GET /api/users")
        detected_ops = {f"{ep.method} {ep.path}" for ep in endpoints}

        # Find endpoints in source but not in config
        for op in sorted(detected_ops - configured_ops):
            gaps.append(Gap(
                type="uncovered_endpoint",
                details=f"Endpoint '{op}' found in source but not configured in agenttel.yml",
            ))

        # Find config entries that no longer exist in source
        for op in sorted(configured_ops - detected_ops):
            gaps.append(Gap(
                type="stale_config",
                details=f"Operation '{op}' configured in agenttel.yml but not found in source",
            ))

        return gaps
