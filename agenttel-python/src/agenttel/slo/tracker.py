"""SLO tracking with error budget calculation."""

from __future__ import annotations

import threading

from agenttel.enums import AlertLevel
from agenttel.models import SloDefinition, SloStatus


class SloTracker:
    """Thread-safe SLO tracker with error budget calculation."""

    def __init__(self) -> None:
        self._definitions: dict[str, SloDefinition] = {}
        self._total: dict[str, int] = {}
        self._failed: dict[str, int] = {}
        self._lock = threading.Lock()

    def register(self, definition: SloDefinition) -> None:
        """Register an SLO definition."""
        with self._lock:
            self._definitions[definition.name] = definition
            self._total.setdefault(definition.name, 0)
            self._failed.setdefault(definition.name, 0)

    def record_request(self, slo_name: str, is_failure: bool = False) -> None:
        """Record a request against an SLO."""
        with self._lock:
            if slo_name not in self._definitions:
                return
            self._total[slo_name] = self._total.get(slo_name, 0) + 1
            if is_failure:
                self._failed[slo_name] = self._failed.get(slo_name, 0) + 1

    def get_status(self, slo_name: str) -> SloStatus | None:
        """Get current SLO status."""
        with self._lock:
            defn = self._definitions.get(slo_name)
            if defn is None:
                return None

            total = self._total.get(slo_name, 0)
            failed = self._failed.get(slo_name, 0)

        if total == 0:
            return SloStatus(
                name=defn.name,
                target=defn.target,
                type=defn.type,
                total_requests=0,
                failed_requests=0,
                budget_remaining=1.0,
                burn_rate=0.0,
                alert_level=AlertLevel.OK,
            )

        error_budget = 1.0 - defn.target
        if error_budget <= 0:
            budget_remaining = 0.0 if failed > 0 else 1.0
            burn_rate = float("inf") if failed > 0 else 0.0
        else:
            budget_consumption = (failed / total) / error_budget if total > 0 else 0.0
            budget_remaining = max(0.0, 1.0 - budget_consumption)
            burn_rate = budget_consumption

        alert_level = self._compute_alert_level(budget_remaining)

        return SloStatus(
            name=defn.name,
            target=defn.target,
            type=defn.type,
            total_requests=total,
            failed_requests=failed,
            budget_remaining=budget_remaining,
            burn_rate=burn_rate,
            alert_level=alert_level,
        )

    def get_all_statuses(self) -> list[SloStatus]:
        """Get status for all registered SLOs."""
        statuses = []
        for name in self._definitions:
            status = self.get_status(name)
            if status is not None:
                statuses.append(status)
        return statuses

    def reset(self, slo_name: str | None = None) -> None:
        """Reset counters for one or all SLOs."""
        with self._lock:
            if slo_name:
                self._total[slo_name] = 0
                self._failed[slo_name] = 0
            else:
                for name in self._definitions:
                    self._total[name] = 0
                    self._failed[name] = 0

    @staticmethod
    def _compute_alert_level(budget_remaining: float) -> AlertLevel:
        if budget_remaining <= 0.10:
            return AlertLevel.CRITICAL
        if budget_remaining <= 0.25:
            return AlertLevel.WARNING
        if budget_remaining <= 0.50:
            return AlertLevel.INFO
        return AlertLevel.OK
