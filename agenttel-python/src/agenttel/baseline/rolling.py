"""Rolling baseline provider with ring buffer statistics."""

from __future__ import annotations

import math
import threading
import time
from collections import deque
from dataclasses import dataclass, field

from agenttel.enums import BaselineConfidence, BaselineSource
from agenttel.baseline.provider import BaselineProvider
from agenttel.models import OperationBaseline


@dataclass
class Snapshot:
    """Statistical snapshot of a rolling window."""

    mean: float = 0.0
    stddev: float = 0.0
    p50: float = 0.0
    p95: float = 0.0
    p99: float = 0.0
    error_rate: float = 0.0
    sample_count: int = 0
    confidence: BaselineConfidence = BaselineConfidence.LOW


class RollingWindow:
    """Thread-safe ring buffer for latency and error tracking."""

    def __init__(self, capacity: int = 1000) -> None:
        self._capacity = capacity
        self._latencies: deque[float] = deque(maxlen=capacity)
        self._errors: deque[bool] = deque(maxlen=capacity)
        self._lock = threading.Lock()

    def record(self, latency_ms: float, is_error: bool = False) -> None:
        """Record a data point."""
        with self._lock:
            self._latencies.append(latency_ms)
            self._errors.append(is_error)

    @property
    def size(self) -> int:
        with self._lock:
            return len(self._latencies)

    def snapshot(self) -> Snapshot:
        """Compute statistical snapshot of current data."""
        with self._lock:
            if not self._latencies:
                return Snapshot()

            latencies = sorted(self._latencies)
            n = len(latencies)
            error_count = sum(1 for e in self._errors if e)

        mean = sum(latencies) / n
        variance = sum((x - mean) ** 2 for x in latencies) / n if n > 1 else 0.0
        stddev = math.sqrt(variance)

        # Determine confidence
        if n < 30:
            confidence = BaselineConfidence.LOW
        elif n < 200:
            confidence = BaselineConfidence.MEDIUM
        else:
            confidence = BaselineConfidence.HIGH

        return Snapshot(
            mean=mean,
            stddev=stddev,
            p50=_percentile(latencies, 0.50),
            p95=_percentile(latencies, 0.95),
            p99=_percentile(latencies, 0.99),
            error_rate=error_count / n if n > 0 else 0.0,
            sample_count=n,
            confidence=confidence,
        )


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


class RollingBaselineProvider(BaselineProvider):
    """Provides baselines from rolling window statistics."""

    def __init__(self, window_size: int = 1000, min_samples: int = 10) -> None:
        self._windows: dict[str, RollingWindow] = {}
        self._window_size = window_size
        self._min_samples = min_samples
        self._lock = threading.Lock()

    def record(self, operation_name: str, latency_ms: float, is_error: bool = False) -> None:
        """Record a data point for an operation."""
        window = self._get_or_create_window(operation_name)
        window.record(latency_ms, is_error)

    def get_baseline(self, operation_name: str) -> OperationBaseline | None:
        window = self._windows.get(operation_name)
        if window is None or window.size < self._min_samples:
            return None

        snap = window.snapshot()
        return OperationBaseline(
            operation_name=operation_name,
            latency_p50_ms=snap.p50,
            latency_p95_ms=snap.p95,
            latency_p99_ms=snap.p99,
            error_rate=snap.error_rate,
            source=BaselineSource.ROLLING_7D,
            confidence=snap.confidence,
            sample_count=snap.sample_count,
            updated_at=time.time(),
        )

    def has_baseline(self, operation_name: str) -> bool:
        window = self._windows.get(operation_name)
        return window is not None and window.size >= self._min_samples

    def get_window(self, operation_name: str) -> RollingWindow | None:
        """Get the rolling window for an operation."""
        return self._windows.get(operation_name)

    def all_operations(self) -> list[str]:
        """Return all tracked operation names."""
        return list(self._windows.keys())

    def _get_or_create_window(self, operation_name: str) -> RollingWindow:
        if operation_name not in self._windows:
            with self._lock:
                if operation_name not in self._windows:
                    self._windows[operation_name] = RollingWindow(self._window_size)
        return self._windows[operation_name]
