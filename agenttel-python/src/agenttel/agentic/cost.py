"""Agent cost aggregation across invocations."""

from __future__ import annotations

import threading
from typing import Any


class AgentCostAggregator:
    """Aggregates token usage and costs across an agent invocation."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._total_input_tokens = 0
        self._total_output_tokens = 0
        self._total_reasoning_tokens = 0
        self._total_cost_usd = 0.0
        self._llm_calls = 0

    def record(
        self,
        input_tokens: int = 0,
        output_tokens: int = 0,
        reasoning_tokens: int = 0,
        cost_usd: float = 0.0,
    ) -> None:
        """Record a single LLM call's usage."""
        with self._lock:
            self._total_input_tokens += input_tokens
            self._total_output_tokens += output_tokens
            self._total_reasoning_tokens += reasoning_tokens
            self._total_cost_usd += cost_usd
            self._llm_calls += 1

    def summary(self) -> dict[str, Any]:
        """Get aggregated cost summary."""
        with self._lock:
            return {
                "total_input_tokens": self._total_input_tokens,
                "total_output_tokens": self._total_output_tokens,
                "total_reasoning_tokens": self._total_reasoning_tokens,
                "total_cost_usd": round(self._total_cost_usd, 6),
                "llm_calls": self._llm_calls,
                "total_tokens": self._total_input_tokens + self._total_output_tokens,
            }

    def reset(self) -> None:
        """Reset all counters."""
        with self._lock:
            self._total_input_tokens = 0
            self._total_output_tokens = 0
            self._total_reasoning_tokens = 0
            self._total_cost_usd = 0.0
            self._llm_calls = 0
