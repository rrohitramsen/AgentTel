"""SLO reporting and trend analysis."""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Any

from agenttel.models import SloStatus
from agenttel.slo.tracker import SloTracker


@dataclass
class SloReport:
    """SLO compliance report."""

    generated_at: float = field(default_factory=time.time)
    period: str = ""
    slo_statuses: list[dict[str, Any]] = field(default_factory=list)
    overall_compliance: float = 1.0
    summary: str = ""


@dataclass
class TrendPoint:
    """A single data point in a trend."""

    timestamp: float
    value: float
    label: str = ""


@dataclass
class TrendAnalysis:
    """Trend analysis over a time window."""

    metric_name: str
    direction: str = "stable"  # improving, degrading, stable
    points: list[TrendPoint] = field(default_factory=list)
    summary: str = ""


class SloReportGenerator:
    """Generates SLO compliance reports."""

    def __init__(self, slo_tracker: SloTracker) -> None:
        self._slo_tracker = slo_tracker

    def generate(self, period: str = "current") -> SloReport:
        """Generate an SLO compliance report."""
        statuses = self._slo_tracker.get_all_statuses()

        slo_data = []
        compliant_count = 0
        for status in statuses:
            is_compliant = status.budget_remaining > 0
            if is_compliant:
                compliant_count += 1
            slo_data.append({
                "name": status.name,
                "target": status.target,
                "type": status.type.value,
                "total_requests": status.total_requests,
                "failed_requests": status.failed_requests,
                "budget_remaining": status.budget_remaining,
                "burn_rate": status.burn_rate,
                "alert_level": status.alert_level.value,
                "compliant": is_compliant,
            })

        total = len(statuses) if statuses else 1
        compliance = compliant_count / total

        # Generate summary
        violations = [s for s in slo_data if not s["compliant"]]
        if violations:
            violation_names = ", ".join(s["name"] for s in violations)
            summary = f"{len(violations)} SLO(s) in violation: {violation_names}"
        else:
            summary = f"All {len(statuses)} SLO(s) are compliant"

        return SloReport(
            period=period,
            slo_statuses=slo_data,
            overall_compliance=compliance,
            summary=summary,
        )


class TrendAnalyzer:
    """Analyzes trends in operational metrics."""

    def __init__(self) -> None:
        self._history: dict[str, list[TrendPoint]] = {}

    def record(self, metric_name: str, value: float, label: str = "") -> None:
        """Record a metric data point."""
        if metric_name not in self._history:
            self._history[metric_name] = []
        self._history[metric_name].append(
            TrendPoint(timestamp=time.time(), value=value, label=label)
        )

    def analyze(self, metric_name: str, window_size: int = 10) -> TrendAnalysis:
        """Analyze trend for a metric."""
        points = self._history.get(metric_name, [])
        recent = points[-window_size:] if len(points) > window_size else points

        if len(recent) < 2:
            return TrendAnalysis(
                metric_name=metric_name,
                direction="stable",
                points=recent,
                summary=f"Insufficient data for {metric_name} trend analysis",
            )

        # Simple linear trend
        first_half = recent[: len(recent) // 2]
        second_half = recent[len(recent) // 2 :]
        avg_first = sum(p.value for p in first_half) / len(first_half)
        avg_second = sum(p.value for p in second_half) / len(second_half)

        threshold = 0.1  # 10% change threshold
        if avg_first > 0:
            change = (avg_second - avg_first) / avg_first
        else:
            change = 0.0

        if change > threshold:
            direction = "degrading"
        elif change < -threshold:
            direction = "improving"
        else:
            direction = "stable"

        return TrendAnalysis(
            metric_name=metric_name,
            direction=direction,
            points=recent,
            summary=f"{metric_name}: {direction} ({change:+.1%} change over {len(recent)} samples)",
        )
