"""Service health aggregation from span data."""

from __future__ import annotations

import threading
import time
from collections import deque

from agenttel.enums import DependencyState
from agenttel.models import (
    DependencyHealth,
    OperationStats,
    ServiceHealthSummary,
)


class ServiceHealthAggregator:
    """Aggregates per-operation and dependency health from span data.

    Called from the SpanProcessor on_end callback.
    """

    def __init__(self, service_name: str = "", window_size: int = 1000) -> None:
        self._service_name = service_name
        self._window_size = window_size
        self._lock = threading.Lock()

        # Per-operation tracking
        self._op_latencies: dict[str, deque[float]] = {}
        self._op_request_count: dict[str, int] = {}
        self._op_error_count: dict[str, int] = {}

        # Dependency tracking
        self._dep_health: dict[str, DependencyHealth] = {}

    def record_operation(
        self,
        operation_name: str,
        latency_ms: float,
        is_error: bool = False,
    ) -> None:
        """Record an operation execution."""
        with self._lock:
            if operation_name not in self._op_latencies:
                self._op_latencies[operation_name] = deque(maxlen=self._window_size)
                self._op_request_count[operation_name] = 0
                self._op_error_count[operation_name] = 0

            self._op_latencies[operation_name].append(latency_ms)
            self._op_request_count[operation_name] += 1
            if is_error:
                self._op_error_count[operation_name] += 1

    def record_dependency_health(self, health: DependencyHealth) -> None:
        """Update dependency health status."""
        with self._lock:
            self._dep_health[health.name] = health

    def update_dependency_state(
        self,
        name: str,
        state: DependencyState,
        latency_ms: float = 0.0,
        error_rate: float = 0.0,
    ) -> None:
        """Update a dependency's state."""
        with self._lock:
            existing = self._dep_health.get(name)
            if existing:
                existing.state = state
                existing.latency_ms = latency_ms
                existing.error_rate = error_rate
                existing.last_check = time.time()

    def get_summary(self) -> ServiceHealthSummary:
        """Get the current service health summary."""
        with self._lock:
            operations = []
            for op_name in self._op_latencies:
                latencies = sorted(self._op_latencies[op_name])
                n = len(latencies)
                if n == 0:
                    continue

                mean = sum(latencies) / n
                operations.append(
                    OperationStats(
                        operation_name=op_name,
                        request_count=self._op_request_count.get(op_name, 0),
                        error_count=self._op_error_count.get(op_name, 0),
                        latency_p50_ms=_percentile(latencies, 0.50),
                        latency_p95_ms=_percentile(latencies, 0.95),
                        latency_p99_ms=_percentile(latencies, 0.99),
                        latency_mean_ms=mean,
                    )
                )

            dependencies = list(self._dep_health.values())

            # Determine overall status
            status = "HEALTHY"
            has_errors = any(
                op.error_count > op.request_count * 0.1
                for op in operations
                if op.request_count > 0
            )
            has_failing_deps = any(
                d.state == DependencyState.FAILING for d in dependencies
            )
            if has_failing_deps or has_errors:
                status = "CRITICAL"
            elif any(d.state == DependencyState.DEGRADED for d in dependencies):
                status = "DEGRADED"

            return ServiceHealthSummary(
                service_name=self._service_name,
                status=status,
                operations=operations,
                dependencies=dependencies,
            )

    def reset(self) -> None:
        """Reset all aggregated data."""
        with self._lock:
            self._op_latencies.clear()
            self._op_request_count.clear()
            self._op_error_count.clear()
            self._dep_health.clear()


def _percentile(sorted_data: list[float], p: float) -> float:
    """Calculate percentile using linear interpolation."""
    n = len(sorted_data)
    if n == 0:
        return 0.0
    if n == 1:
        return sorted_data[0]
    idx = p * (n - 1)
    lower = int(idx)
    upper = min(lower + 1, n - 1)
    frac = idx - lower
    return sorted_data[lower] + frac * (sorted_data[upper] - sorted_data[lower])
