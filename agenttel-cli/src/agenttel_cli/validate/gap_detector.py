"""Gap detector — finds mismatches between config and source code."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml

from agenttel_cli.analyze.endpoint_detector import DetectedEndpoint


@dataclass(frozen=True)
class ConfigGap:
    """A gap between the configuration and the actual source code."""

    type: str  # "unconfigured_endpoint" or "stale_config"
    details: str


class GapDetector:
    """Compares an agenttel.yml configuration against detected source-code endpoints."""

    def detect(
        self,
        config_path: Path,
        detected_endpoints: list[DetectedEndpoint],
    ) -> list[ConfigGap]:
        """Find gaps between the config file and the source code.

        Parameters
        ----------
        config_path:
            Path to the agenttel.yml configuration.
        detected_endpoints:
            Endpoints detected from the live source tree.

        Returns
        -------
        list[ConfigGap]
            All gaps found.
        """
        config = self._load_config(config_path)
        if config is None:
            return []

        configured_ops = self._extract_configured_operations(config)
        source_ops = self._endpoints_to_operation_keys(detected_endpoints)

        gaps: list[ConfigGap] = []

        # Endpoints in source but not in config.
        for op_key in sorted(source_ops - configured_ops):
            gaps.append(
                ConfigGap(
                    type="unconfigured_endpoint",
                    details=f"Endpoint '{op_key}' exists in source but is not configured.",
                )
            )

        # Operations in config that no longer exist in source.
        for op_key in sorted(configured_ops - source_ops):
            gaps.append(
                ConfigGap(
                    type="stale_config",
                    details=f"Operation '{op_key}' is configured but not found in source.",
                )
            )

        return gaps

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    @staticmethod
    def _load_config(config_path: Path) -> dict[str, Any] | None:
        try:
            raw = config_path.read_text(encoding="utf-8")
            config = yaml.safe_load(raw)
            return config if isinstance(config, dict) else None
        except (yaml.YAMLError, OSError):
            return None

    @staticmethod
    def _extract_configured_operations(config: dict[str, Any]) -> set[str]:
        """Return the set of operation name keys from the config."""
        ops: set[str] = set()
        for op in config.get("operations", []):
            name = op.get("name")
            if name:
                ops.add(name)
        return ops

    @staticmethod
    def _endpoints_to_operation_keys(endpoints: list[DetectedEndpoint]) -> set[str]:
        """Convert detected endpoints to operation name keys matching config format."""
        return {f"{ep.method} {ep.path}" for ep in endpoints}
