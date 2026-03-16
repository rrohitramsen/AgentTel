"""Baseline provider interfaces and static implementation."""

from __future__ import annotations

from abc import ABC, abstractmethod

from agenttel.models import OperationBaseline


class BaselineProvider(ABC):
    """Abstract base class for baseline providers."""

    @abstractmethod
    def get_baseline(self, operation_name: str) -> OperationBaseline | None:
        """Get baseline for an operation."""
        ...

    @abstractmethod
    def has_baseline(self, operation_name: str) -> bool:
        """Check if a baseline exists for an operation."""
        ...


class StaticBaselineProvider(BaselineProvider):
    """Provides baselines from static configuration."""

    def __init__(self) -> None:
        self._baselines: dict[str, OperationBaseline] = {}

    def register(self, baseline: OperationBaseline) -> None:
        """Register a static baseline for an operation."""
        self._baselines[baseline.operation_name] = baseline

    def get_baseline(self, operation_name: str) -> OperationBaseline | None:
        return self._baselines.get(operation_name)

    def has_baseline(self, operation_name: str) -> bool:
        return operation_name in self._baselines

    def all_baselines(self) -> dict[str, OperationBaseline]:
        """Return all registered baselines."""
        return dict(self._baselines)
