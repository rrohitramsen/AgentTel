"""Feedback applier — applies low-risk feedback events to config files."""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Any

import yaml

from .models import FeedbackEvent, FeedbackTrigger, RiskLevel

logger = logging.getLogger(__name__)


class FeedbackApplier:
    """Applies low-risk feedback events to agenttel.yml config files."""

    def apply(self, config_path: Path, events: list[FeedbackEvent]) -> list[str]:
        """Apply eligible events to the config file.

        Only LOW risk events with auto_applicable=True are applied.
        Returns a list of descriptions of applied changes.
        """
        eligible = [
            e for e in events
            if e.risk_level == RiskLevel.LOW and e.auto_applicable
        ]

        if not eligible:
            return []

        config = self._load_config(config_path)
        if config is None:
            return [f"Failed to load config: {config_path}"]

        applied: list[str] = []

        for event in eligible:
            result = self._apply_event(config, event)
            if result:
                applied.append(result)

        if applied:
            self._save_config(config_path, config)
            logger.info("Applied %d changes to %s", len(applied), config_path)

        return applied

    def apply_single(self, config_path: Path, event: FeedbackEvent) -> str | None:
        """Apply a single event regardless of risk level (user-approved)."""
        config = self._load_config(config_path)
        if config is None:
            return None

        result = self._apply_event(config, event)
        if result:
            self._save_config(config_path, config)
        return result

    def _apply_event(self, config: dict[str, Any], event: FeedbackEvent) -> str | None:
        if event.trigger in (FeedbackTrigger.STALE_BASELINE, FeedbackTrigger.MISSING_BASELINE):
            return self._apply_baseline_update(config, event)
        if event.trigger == FeedbackTrigger.MISSING_RUNBOOK:
            return self._apply_runbook_placeholder(config, event)
        return None

    def _apply_runbook_placeholder(
        self, config: dict[str, Any], event: FeedbackEvent,
    ) -> str | None:
        for op in config.get("operations", []):
            if op.get("name") == event.target:
                op["runbook_url"] = "TODO: add runbook URL"
                return f"Added runbook placeholder for {event.target}"
        return None

    def _apply_baseline_update(
        self, config: dict[str, Any], event: FeedbackEvent,
    ) -> str | None:
        for op in config.get("operations", []):
            if op.get("name") == event.target and event.suggested_value:
                # Parse suggested_value like "p50=62ms" or "p99=149ms"
                import re
                m = re.match(r"(p\d+)=([0-9.]+)ms", event.suggested_value)
                if m:
                    metric = m.group(1)
                    value = m.group(2)
                    try:
                        op.setdefault("baseline", {})[metric] = float(value)
                        return f"Updated {event.target} baseline {metric} to {value}ms"
                    except ValueError:
                        pass
        return None

    @staticmethod
    def _load_config(config_path: Path) -> dict[str, Any] | None:
        try:
            raw = config_path.read_text(encoding="utf-8")
            config = yaml.safe_load(raw)
            return config if isinstance(config, dict) else None
        except (yaml.YAMLError, OSError):
            return None

    @staticmethod
    def _save_config(config_path: Path, config: dict[str, Any]) -> None:
        content = yaml.dump(config, default_flow_style=False, sort_keys=False, allow_unicode=True)
        with open(config_path, "w", encoding="utf-8") as f:
            f.write(content)
            f.flush()
            os.fsync(f.fileno())
