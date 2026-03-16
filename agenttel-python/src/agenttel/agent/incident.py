"""Incident context builder for AI agent diagnosis."""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Any

from agenttel.agent.health import ServiceHealthAggregator
from agenttel.causality.tracker import CausalityTracker
from agenttel.models import AnomalyResult, SloStatus


@dataclass
class IncidentContext:
    """Full incident context for AI agent consumption."""

    service_name: str
    status: str
    timestamp: float = field(default_factory=time.time)
    anomalies: list[dict[str, Any]] = field(default_factory=list)
    slo_violations: list[dict[str, Any]] = field(default_factory=list)
    failing_dependencies: list[str] = field(default_factory=list)
    causal_chain: dict[str, Any] | None = None
    recent_changes: list[dict[str, Any]] = field(default_factory=list)
    suggested_actions: list[str] = field(default_factory=list)


class IncidentContextBuilder:
    """Builds comprehensive incident context from system state."""

    def __init__(
        self,
        health_aggregator: ServiceHealthAggregator,
        causality_tracker: CausalityTracker,
    ) -> None:
        self._health = health_aggregator
        self._causality = causality_tracker

    def build(
        self,
        anomalies: list[AnomalyResult] | None = None,
        slo_statuses: list[SloStatus] | None = None,
    ) -> IncidentContext:
        """Build incident context from current state."""
        summary = self._health.get_summary()

        # Build anomaly list
        anomaly_list = []
        if anomalies:
            for a in anomalies:
                if a.is_anomaly:
                    anomaly_list.append({
                        "score": a.score,
                        "z_score": a.z_score,
                        "pattern": a.pattern.value if a.pattern else "unknown",
                    })

        # Build SLO violations
        slo_violations = []
        if slo_statuses:
            for s in slo_statuses:
                if s.alert_level.value != "ok":
                    slo_violations.append({
                        "name": s.name,
                        "target": s.target,
                        "budget_remaining": s.budget_remaining,
                        "burn_rate": s.burn_rate,
                        "alert_level": s.alert_level.value,
                    })

        # Get failing dependencies
        failing = self._causality.get_failing_dependencies()

        # Get causal chain
        chain = self._causality.build_causal_chain()
        causal_data = None
        if chain:
            causal_data = {
                "root_cause": chain.root_cause,
                "cause_category": chain.cause_category.value,
                "affected_dependencies": chain.affected_dependencies,
                "business_impact": chain.business_impact.value,
                "impact_scope": chain.impact_scope.value,
            }

        return IncidentContext(
            service_name=summary.service_name,
            status=summary.status,
            anomalies=anomaly_list,
            slo_violations=slo_violations,
            failing_dependencies=failing,
            causal_chain=causal_data,
        )


class ContextFormatter:
    """Formats incident context as structured text for LLM consumption."""

    @staticmethod
    def format_for_llm(context: IncidentContext) -> str:
        """Format incident context as readable text for LLM."""
        lines = [
            f"# Incident Context: {context.service_name}",
            f"Status: {context.status}",
            f"Timestamp: {time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(context.timestamp))}",
            "",
        ]

        if context.anomalies:
            lines.append("## Anomalies Detected")
            for a in context.anomalies:
                lines.append(f"- Pattern: {a['pattern']}, Score: {a['score']:.2f}, Z-Score: {a['z_score']:.2f}")
            lines.append("")

        if context.slo_violations:
            lines.append("## SLO Violations")
            for s in context.slo_violations:
                lines.append(
                    f"- {s['name']}: target={s['target']}, "
                    f"budget_remaining={s['budget_remaining']:.1%}, "
                    f"alert={s['alert_level']}"
                )
            lines.append("")

        if context.failing_dependencies:
            lines.append("## Failing Dependencies")
            for dep in context.failing_dependencies:
                lines.append(f"- {dep}")
            lines.append("")

        if context.causal_chain:
            c = context.causal_chain
            lines.append("## Causal Analysis")
            lines.append(f"Root Cause: {c['root_cause']}")
            lines.append(f"Category: {c['cause_category']}")
            lines.append(f"Impact: {c['business_impact']} ({c['impact_scope']})")
            lines.append(f"Affected: {', '.join(c['affected_dependencies'])}")
            lines.append("")

        if context.suggested_actions:
            lines.append("## Suggested Actions")
            for i, action in enumerate(context.suggested_actions, 1):
                lines.append(f"{i}. {action}")

        return "\n".join(lines)
