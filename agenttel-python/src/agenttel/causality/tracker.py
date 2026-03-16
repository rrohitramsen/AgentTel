"""Causality tracking for dependency state changes."""

from __future__ import annotations

import threading
import time
from dataclasses import dataclass, field

from agenttel.enums import (
    BusinessImpact,
    CauseCategory,
    DependencyCriticality,
    DependencyState,
    ImpactScope,
)


@dataclass
class StateChange:
    """Records a dependency state change."""

    dependency_name: str
    previous_state: DependencyState
    new_state: DependencyState
    timestamp: float = field(default_factory=time.time)
    cause_category: CauseCategory = CauseCategory.UNKNOWN


@dataclass
class CausalChain:
    """A chain of causally related state changes."""

    root_cause: str
    cause_category: CauseCategory
    affected_dependencies: list[str]
    business_impact: BusinessImpact = BusinessImpact.OPERATIONAL
    impact_scope: ImpactScope = ImpactScope.SINGLE_SERVICE
    state_changes: list[StateChange] = field(default_factory=list)
    started_at: float = field(default_factory=time.time)


class CausalityTracker:
    """Tracks dependency state changes and identifies causal relationships."""

    def __init__(self, correlation_window_s: float = 30.0) -> None:
        self._states: dict[str, DependencyState] = {}
        self._criticalities: dict[str, DependencyCriticality] = {}
        self._recent_changes: list[StateChange] = []
        self._correlation_window = correlation_window_s
        self._lock = threading.Lock()

    def register_dependency(
        self, name: str, criticality: DependencyCriticality
    ) -> None:
        """Register a dependency for tracking."""
        with self._lock:
            self._states[name] = DependencyState.UNKNOWN
            self._criticalities[name] = criticality

    def update_state(
        self,
        dependency_name: str,
        new_state: DependencyState,
        cause_category: CauseCategory = CauseCategory.UNKNOWN,
    ) -> StateChange | None:
        """Update dependency state and return change if state actually changed."""
        with self._lock:
            previous = self._states.get(dependency_name, DependencyState.UNKNOWN)
            if previous == new_state:
                return None

            self._states[dependency_name] = new_state
            change = StateChange(
                dependency_name=dependency_name,
                previous_state=previous,
                new_state=new_state,
                cause_category=cause_category,
            )
            self._recent_changes.append(change)
            self._prune_old_changes()
            return change

    def get_state(self, dependency_name: str) -> DependencyState:
        """Get current state of a dependency."""
        return self._states.get(dependency_name, DependencyState.UNKNOWN)

    def get_all_states(self) -> dict[str, DependencyState]:
        """Get all dependency states."""
        with self._lock:
            return dict(self._states)

    def get_failing_dependencies(self) -> list[str]:
        """Get names of all failing dependencies."""
        with self._lock:
            return [
                name
                for name, state in self._states.items()
                if state == DependencyState.FAILING
            ]

    def build_causal_chain(self) -> CausalChain | None:
        """Analyze recent state changes and build a causal chain."""
        with self._lock:
            if not self._recent_changes:
                return None

            # Find the earliest failure in the correlation window
            failures = [
                c for c in self._recent_changes
                if c.new_state == DependencyState.FAILING
            ]
            if not failures:
                return None

            root = failures[0]
            affected = list({c.dependency_name for c in failures})

            # Determine impact scope
            if len(affected) >= 3:
                scope = ImpactScope.MULTI_SERVICE
            elif len(affected) > 1:
                scope = ImpactScope.SINGLE_SERVICE
            else:
                scope = ImpactScope.SINGLE_OPERATION

            # Determine business impact based on criticality
            has_required = any(
                self._criticalities.get(name) == DependencyCriticality.REQUIRED
                for name in affected
            )
            impact = BusinessImpact.REVENUE if has_required else BusinessImpact.OPERATIONAL

            return CausalChain(
                root_cause=root.dependency_name,
                cause_category=root.cause_category,
                affected_dependencies=affected,
                business_impact=impact,
                impact_scope=scope,
                state_changes=list(self._recent_changes),
                started_at=root.timestamp,
            )

    def _prune_old_changes(self) -> None:
        """Remove state changes older than the correlation window."""
        cutoff = time.time() - self._correlation_window
        self._recent_changes = [
            c for c in self._recent_changes if c.timestamp > cutoff
        ]
