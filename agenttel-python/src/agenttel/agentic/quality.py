"""Quality tracking and loop detection for agentic workflows."""

from __future__ import annotations

import threading
from collections import Counter
from dataclasses import dataclass


@dataclass
class LoopDetectionResult:
    """Result of loop detection analysis."""

    detected: bool = False
    pattern: str = ""
    repetitions: int = 0


class LoopDetector:
    """Detects repeated tool calls or step patterns."""

    def __init__(self, max_repetitions: int = 3) -> None:
        self._max_repetitions = max_repetitions
        self._recent_actions: list[str] = []
        self._lock = threading.Lock()

    def record_action(self, action: str) -> LoopDetectionResult:
        """Record an action and check for loops."""
        with self._lock:
            self._recent_actions.append(action)

            # Keep only last N*2 actions for analysis
            window = self._recent_actions[-(self._max_repetitions * 3) :]

            # Check for exact repetitions
            if len(window) >= self._max_repetitions:
                last = window[-1]
                repeat_count = 0
                for a in reversed(window):
                    if a == last:
                        repeat_count += 1
                    else:
                        break
                if repeat_count >= self._max_repetitions:
                    return LoopDetectionResult(
                        detected=True,
                        pattern=last,
                        repetitions=repeat_count,
                    )

            # Check for sequence repetitions (A-B-A-B pattern)
            if len(window) >= 4:
                seq_len = 2
                seq = tuple(window[-seq_len:])
                count = 0
                for i in range(len(window) - seq_len + 1):
                    if tuple(window[i : i + seq_len]) == seq:
                        count += 1
                if count >= self._max_repetitions:
                    return LoopDetectionResult(
                        detected=True,
                        pattern=f"sequence({' -> '.join(seq)})",
                        repetitions=count,
                    )

        return LoopDetectionResult()

    def reset(self) -> None:
        with self._lock:
            self._recent_actions.clear()


class QualityTracker:
    """Tracks quality metrics for agent invocations."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._goals_attempted = 0
        self._goals_achieved = 0
        self._human_interventions = 0
        self._loops_detected = 0
        self._tool_call_counts: Counter[str] = Counter()

    def record_goal(self, achieved: bool) -> None:
        """Record a goal attempt and outcome."""
        with self._lock:
            self._goals_attempted += 1
            if achieved:
                self._goals_achieved += 1

    def record_human_intervention(self) -> None:
        """Record a human intervention."""
        with self._lock:
            self._human_interventions += 1

    def record_loop_detected(self) -> None:
        """Record a loop detection event."""
        with self._lock:
            self._loops_detected += 1

    def record_tool_call(self, tool_name: str) -> None:
        """Record a tool call."""
        with self._lock:
            self._tool_call_counts[tool_name] += 1

    def summary(self) -> dict:
        """Get quality metrics summary."""
        with self._lock:
            success_rate = (
                self._goals_achieved / self._goals_attempted
                if self._goals_attempted > 0
                else 0.0
            )
            return {
                "goals_attempted": self._goals_attempted,
                "goals_achieved": self._goals_achieved,
                "success_rate": success_rate,
                "human_interventions": self._human_interventions,
                "loops_detected": self._loops_detected,
                "tool_call_distribution": dict(self._tool_call_counts),
            }

    def reset(self) -> None:
        with self._lock:
            self._goals_attempted = 0
            self._goals_achieved = 0
            self._human_interventions = 0
            self._loops_detected = 0
            self._tool_call_counts.clear()
