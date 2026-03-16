"""AgentTel span processor for OpenTelemetry."""

from __future__ import annotations

import time
from typing import Any

from opentelemetry import context, trace
from opentelemetry.sdk.trace import ReadableSpan, Span, SpanProcessor

from agenttel import attributes as attr
from agenttel.anomaly.detector import AnomalyDetector
from agenttel.baseline.composite import CompositeBaselineProvider
from agenttel.baseline.rolling import RollingBaselineProvider
from agenttel.causality.tracker import CausalityTracker
from agenttel.config import AgentTelConfig
from agenttel.enums import AlertLevel, DependencyState
from agenttel.error.classifier import ErrorClassifier
from agenttel.events import AgentTelEventEmitter
from agenttel.models import OperationBaseline, OperationContext
from agenttel.slo.tracker import SloTracker
from agenttel.topology.registry import TopologyRegistry


class AgentTelSpanProcessor(SpanProcessor):
    """Enriches spans with AgentTel attributes.

    On start: attaches baseline + decision attributes.
    On end: feeds rolling baselines, runs anomaly detection, checks SLO.
    """

    def __init__(
        self,
        config: AgentTelConfig,
        topology: TopologyRegistry,
        baseline_provider: CompositeBaselineProvider,
        rolling_provider: RollingBaselineProvider,
        anomaly_detector: AnomalyDetector,
        slo_tracker: SloTracker,
        error_classifier: ErrorClassifier,
        causality_tracker: CausalityTracker,
        event_emitter: AgentTelEventEmitter,
    ) -> None:
        self._config = config
        self._topology = topology
        self._baseline_provider = baseline_provider
        self._rolling_provider = rolling_provider
        self._anomaly_detector = anomaly_detector
        self._slo_tracker = slo_tracker
        self._error_classifier = error_classifier
        self._causality_tracker = causality_tracker
        self._event_emitter = event_emitter

    def on_start(self, span: Span, parent_context: context.Context | None = None) -> None:
        """Enrich span on start with baseline and context attributes."""
        if not span.is_recording():
            return

        operation_name = self._resolve_operation_name(span)
        if not operation_name:
            return

        # Attach topology attributes
        topo_attrs = self._topology.get_topology_attributes()
        for key, value in topo_attrs.items():
            span.set_attribute(key, value)

        # Attach operation context
        op_context = self._resolve_operation_context(operation_name)
        if op_context:
            self._set_operation_context(span, op_context)

        # Attach baseline attributes
        baseline = self._baseline_provider.get_baseline(operation_name)
        if baseline:
            self._set_baseline_attributes(span, baseline)

    def on_end(self, span: ReadableSpan) -> None:
        """Process span on end: update rolling baselines, detect anomalies, check SLO."""
        if not self._config.enabled:
            return

        operation_name = self._resolve_operation_name_from_readable(span)
        if not operation_name:
            return

        # Calculate latency
        latency_ms = self._calculate_latency_ms(span)
        is_error = span.status.is_ok is False if span.status else False

        # Feed rolling baseline
        self._rolling_provider.record(operation_name, latency_ms, is_error)

        # Anomaly detection
        if self._config.anomaly_detection.enabled:
            baseline = self._baseline_provider.get_baseline(operation_name)
            if baseline and baseline.latency_p50_ms > 0:
                snap = self._rolling_provider.get_window(operation_name)
                if snap and snap.size >= self._config.baselines.rolling_min_samples:
                    snapshot = snap.snapshot()
                    result = self._anomaly_detector.detect(
                        latency_ms, snapshot.mean, snapshot.stddev
                    )
                    if result.is_anomaly:
                        self._event_emitter.emit_anomaly_detected(
                            operation=operation_name,
                            score=result.score,
                            z_score=result.z_score,
                            pattern=result.pattern.value if result.pattern else None,
                        )

        # SLO tracking
        for slo_status in self._slo_tracker.get_all_statuses():
            self._slo_tracker.record_request(slo_status.name, is_error)
            status = self._slo_tracker.get_status(slo_status.name)
            if status and status.alert_level != AlertLevel.OK:
                self._event_emitter.emit_slo_budget_alert(
                    slo_name=status.name,
                    budget_remaining=status.budget_remaining,
                    alert_level=status.alert_level.value,
                )

    def shutdown(self) -> None:
        """Shutdown the processor."""
        pass

    def force_flush(self, timeout_millis: int = 30000) -> bool:
        """Force flush any buffered data."""
        return True

    def _resolve_operation_name(self, span: Span) -> str | None:
        """Resolve operation name from span."""
        return span.name if hasattr(span, "name") else None

    def _resolve_operation_name_from_readable(self, span: ReadableSpan) -> str | None:
        """Resolve operation name from a readable span."""
        # Check for http.route first
        route = span.attributes.get("http.route") if span.attributes else None
        method = span.attributes.get("http.request.method") or span.attributes.get("http.method") if span.attributes else None
        if route and method:
            return f"[{method} {route}]"
        return span.name

    def _resolve_operation_context(self, operation_name: str) -> OperationContext | None:
        """Resolve operation context from config."""
        op_config = self._config.operations.get(operation_name)
        if op_config is None:
            return None

        # Check if profile is specified
        profile = None
        if op_config.profile and op_config.profile in self._config.profiles:
            profile = self._config.profiles[op_config.profile]

        return OperationContext(
            retryable=op_config.retryable if op_config.retryable is not None
            else (profile.retryable if profile else False),
            idempotent=op_config.idempotent if op_config.idempotent is not None
            else (profile.idempotent if profile else False),
            runbook_url=op_config.runbook_url,
            escalation_level=profile.escalation_level if profile else None,
            safe_to_restart=profile.safe_to_restart if profile else True,
            fallback_description=profile.fallback_description if profile else None,
        )

    def _set_operation_context(self, span: Span, ctx: OperationContext) -> None:
        """Set operation context attributes on span."""
        span.set_attribute(attr.OPERATION_RETRYABLE, ctx.retryable)
        span.set_attribute(attr.OPERATION_IDEMPOTENT, ctx.idempotent)
        span.set_attribute(attr.OPERATION_SAFE_TO_RESTART, ctx.safe_to_restart)
        if ctx.runbook_url:
            span.set_attribute(attr.OPERATION_RUNBOOK_URL, ctx.runbook_url)
        if ctx.escalation_level:
            span.set_attribute(attr.OPERATION_ESCALATION_LEVEL, ctx.escalation_level.value)
        if ctx.fallback_description:
            span.set_attribute(attr.OPERATION_FALLBACK_DESCRIPTION, ctx.fallback_description)

    def _set_baseline_attributes(self, span: Span, baseline: OperationBaseline) -> None:
        """Set baseline attributes on span."""
        span.set_attribute(attr.BASELINE_LATENCY_P50_MS, baseline.latency_p50_ms)
        span.set_attribute(attr.BASELINE_LATENCY_P99_MS, baseline.latency_p99_ms)
        span.set_attribute(attr.BASELINE_ERROR_RATE, baseline.error_rate)
        span.set_attribute(attr.BASELINE_SOURCE, baseline.source.value)
        span.set_attribute(attr.BASELINE_CONFIDENCE, baseline.confidence.value)
        span.set_attribute(attr.BASELINE_SAMPLE_COUNT, baseline.sample_count)
        if baseline.latency_p95_ms > 0:
            span.set_attribute(attr.BASELINE_LATENCY_P95_MS, baseline.latency_p95_ms)

    def _calculate_latency_ms(self, span: ReadableSpan) -> float:
        """Calculate span latency in milliseconds."""
        if span.start_time and span.end_time:
            return (span.end_time - span.start_time) / 1_000_000
        return 0.0
