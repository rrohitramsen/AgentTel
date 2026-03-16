"""Composite baseline provider that chains multiple providers."""

from __future__ import annotations

from agenttel.baseline.provider import BaselineProvider
from agenttel.models import OperationBaseline


class CompositeBaselineProvider(BaselineProvider):
    """Chains multiple baseline providers with priority ordering.

    Providers are checked in order; the first to return a baseline wins.
    Typically: rolling -> static -> slo-derived.
    """

    def __init__(self, providers: list[BaselineProvider] | None = None) -> None:
        self._providers: list[BaselineProvider] = providers or []

    def add_provider(self, provider: BaselineProvider) -> None:
        """Add a provider to the chain."""
        self._providers.append(provider)

    def get_baseline(self, operation_name: str) -> OperationBaseline | None:
        for provider in self._providers:
            baseline = provider.get_baseline(operation_name)
            if baseline is not None:
                return baseline
        return None

    def has_baseline(self, operation_name: str) -> bool:
        return any(p.has_baseline(operation_name) for p in self._providers)
