"""Agent context provider for LLM consumption."""

from __future__ import annotations

from typing import Any

from agenttel.agent.health import ServiceHealthAggregator
from agenttel.anomaly.detector import AnomalyDetector
from agenttel.baseline.composite import CompositeBaselineProvider
from agenttel.causality.tracker import CausalityTracker
from agenttel.slo.tracker import SloTracker
from agenttel.topology.registry import TopologyRegistry


class AgentContextProvider:
    """Provides structured context for AI agent consumption."""

    def __init__(
        self,
        topology: TopologyRegistry,
        health_aggregator: ServiceHealthAggregator,
        baseline_provider: CompositeBaselineProvider,
        anomaly_detector: AnomalyDetector,
        slo_tracker: SloTracker,
        causality_tracker: CausalityTracker,
    ) -> None:
        self._topology = topology
        self._health = health_aggregator
        self._baselines = baseline_provider
        self._anomaly = anomaly_detector
        self._slo = slo_tracker
        self._causality = causality_tracker

    def get_full_context(self) -> dict[str, Any]:
        """Get full service context for incident diagnosis."""
        summary = self._health.get_summary()
        return {
            "service": {
                "name": summary.service_name,
                "status": summary.status,
                "team": self._topology.team,
                "tier": self._topology.tier,
                "domain": self._topology.domain,
                "on_call_channel": self._topology.on_call_channel,
            },
            "operations": [
                {
                    "name": op.operation_name,
                    "request_count": op.request_count,
                    "error_count": op.error_count,
                    "error_rate": op.error_count / op.request_count
                    if op.request_count > 0
                    else 0,
                    "latency_p50_ms": op.latency_p50_ms,
                    "latency_p99_ms": op.latency_p99_ms,
                }
                for op in summary.operations
            ],
            "dependencies": [
                {
                    "name": d.name,
                    "type": d.type.value,
                    "criticality": d.criticality.value,
                    "state": d.state.value,
                    "latency_ms": d.latency_ms,
                    "error_rate": d.error_rate,
                }
                for d in summary.dependencies
            ],
            "slo_statuses": [
                {
                    "name": s.name,
                    "target": s.target,
                    "budget_remaining": s.budget_remaining,
                    "burn_rate": s.burn_rate,
                    "alert_level": s.alert_level.value,
                }
                for s in self._slo.get_all_statuses()
            ],
            "causality": None,
        }

        # Add causal chain if available
        chain = self._causality.build_causal_chain()
        if chain:
            pass
