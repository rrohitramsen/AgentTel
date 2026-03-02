"""Feedback engine — detects coverage gaps and generates improvement events."""

from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml

from .models import FeedbackEvent, FeedbackTrigger, RiskLevel


class FeedbackEngine:
    """Analyzes config and health data to produce feedback events.

    Detection sources:
    - Config analysis (missing baselines, runbooks)
    - Health data comparison (stale baselines, SLO burn rate)
    - Gap detection (uncovered endpoints, routes, services)
    """

    def detect_from_config(self, config: dict[str, Any]) -> list[FeedbackEvent]:
        """Detect improvement opportunities from config alone."""
        events: list[FeedbackEvent] = []
        events.extend(self._check_baselines(config))
        events.extend(self._check_runbooks(config))
        events.extend(self._check_dependencies(config))
        return events

    def detect_from_health(
        self,
        config: dict[str, Any],
        health_data: dict[str, Any],
    ) -> list[FeedbackEvent]:
        """Detect improvements by comparing config against live health data."""
        events: list[FeedbackEvent] = []
        events.extend(self._check_stale_baselines(config, health_data))
        events.extend(self._check_slo_burn_rate(health_data))
        return events

    def _check_baselines(self, config: dict[str, Any]) -> list[FeedbackEvent]:
        events: list[FeedbackEvent] = []
        for op in config.get("operations", []):
            name = op.get("name", "unknown")
            baseline = op.get("baseline", {})
            if baseline.get("p50") in (None, "TODO") or baseline.get("p99") in (None, "TODO"):
                events.append(FeedbackEvent(
                    trigger=FeedbackTrigger.MISSING_BASELINE,
                    risk_level=RiskLevel.LOW,
                    target=name,
                    current_value="TODO",
                    reasoning=f"Operation '{name}' has incomplete baselines.",
                    auto_applicable=True,
                ))
        return events

    def _check_runbooks(self, config: dict[str, Any]) -> list[FeedbackEvent]:
        events: list[FeedbackEvent] = []
        for op in config.get("operations", []):
            name = op.get("name", "unknown")
            if not op.get("runbook_url"):
                events.append(FeedbackEvent(
                    trigger=FeedbackTrigger.MISSING_RUNBOOK,
                    risk_level=RiskLevel.MEDIUM,
                    target=name,
                    reasoning=f"Operation '{name}' has no runbook URL.",
                    auto_applicable=False,
                ))
        return events

    def _check_dependencies(self, config: dict[str, Any]) -> list[FeedbackEvent]:
        events: list[FeedbackEvent] = []
        for dep in config.get("dependencies", []):
            name = dep.get("name", "unknown")
            if dep.get("health_check_url") in (None, "TODO"):
                events.append(FeedbackEvent(
                    trigger=FeedbackTrigger.MISSING_RUNBOOK,
                    risk_level=RiskLevel.MEDIUM,
                    target=f"dependency:{name}",
                    reasoning=f"Dependency '{name}' has no health check URL.",
                    auto_applicable=False,
                ))
        return events

    def _check_stale_baselines(
        self,
        config: dict[str, Any],
        health_data: dict[str, Any],
    ) -> list[FeedbackEvent]:
        events: list[FeedbackEvent] = []
        ops_by_name = {
            op.get("name"): op for op in config.get("operations", [])
        }
        for health_op in health_data.get("operations", []):
            name = health_op.get("operation", "")
            config_op = ops_by_name.get(name)
            if not config_op:
                continue

            baseline = config_op.get("baseline", {})
            config_p50 = baseline.get("p50")
            observed_p50 = health_op.get("latency_p50_ms")

            if (
                config_p50 not in (None, "TODO")
                and observed_p50 is not None
                and observed_p50 > 0
            ):
                ratio = observed_p50 / float(config_p50)
                if ratio > 2.0 or ratio < 0.5:
                    events.append(FeedbackEvent(
                        trigger=FeedbackTrigger.STALE_BASELINE,
                        risk_level=RiskLevel.LOW,
                        target=name,
                        current_value=f"p50={config_p50}ms",
                        suggested_value=f"p50={observed_p50:.0f}ms",
                        reasoning=(
                            f"Observed P50 ({observed_p50:.0f}ms) differs {ratio:.1f}x "
                            f"from configured ({config_p50}ms)."
                        ),
                        auto_applicable=True,
                    ))
        return events

    def _check_slo_burn_rate(
        self,
        health_data: dict[str, Any],
    ) -> list[FeedbackEvent]:
        events: list[FeedbackEvent] = []
        for slo in health_data.get("slo_statuses", []):
            burn_rate = slo.get("burn_rate", 0)
            if burn_rate > 3.0:
                events.append(FeedbackEvent(
                    trigger=FeedbackTrigger.SLO_BURN_RATE_HIGH,
                    risk_level=RiskLevel.MEDIUM,
                    target=slo.get("name", "unknown"),
                    current_value=f"burn_rate={burn_rate:.1f}x",
                    reasoning=(
                        f"SLO burn rate is {burn_rate:.1f}x — baselines may "
                        f"need recalibration."
                    ),
                    auto_applicable=False,
                ))
        return events
