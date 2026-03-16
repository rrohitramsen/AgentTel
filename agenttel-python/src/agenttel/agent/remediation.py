"""Remediation registry and executor."""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field
from typing import Any, Callable

logger = logging.getLogger("agenttel.remediation")


@dataclass
class RemediationAction:
    """A registered remediation action."""

    name: str
    description: str
    handler: Callable[..., Any]
    requires_approval: bool = True
    risk_level: str = "medium"
    tags: list[str] = field(default_factory=list)


@dataclass
class RemediationResult:
    """Result of executing a remediation action."""

    action_name: str
    success: bool
    message: str = ""
    executed_at: float = field(default_factory=time.time)
    duration_ms: float = 0.0
    details: dict[str, Any] = field(default_factory=dict)


class RemediationRegistry:
    """Registry for remediation actions."""

    def __init__(self) -> None:
        self._actions: dict[str, RemediationAction] = {}

    def register(
        self,
        name: str,
        description: str,
        handler: Callable[..., Any],
        requires_approval: bool = True,
        risk_level: str = "medium",
        tags: list[str] | None = None,
    ) -> None:
        """Register a remediation action."""
        self._actions[name] = RemediationAction(
            name=name,
            description=description,
            handler=handler,
            requires_approval=requires_approval,
            risk_level=risk_level,
            tags=tags or [],
        )

    def get_action(self, name: str) -> RemediationAction | None:
        """Get a remediation action by name."""
        return self._actions.get(name)

    def list_actions(self) -> list[RemediationAction]:
        """List all registered actions."""
        return list(self._actions.values())

    def suggest(self, failing_dependencies: list[str], anomaly_patterns: list[str]) -> list[str]:
        """Suggest remediation actions based on current state."""
        suggestions = []
        for action in self._actions.values():
            # Match tags against failing deps and patterns
            for tag in action.tags:
                if tag in failing_dependencies or tag in anomaly_patterns:
                    suggestions.append(action.name)
                    break
        return suggestions


class RemediationExecutor:
    """Executes remediation actions with audit logging."""

    def __init__(self, registry: RemediationRegistry) -> None:
        self._registry = registry
        self._audit_log: list[RemediationResult] = []

    def execute(
        self,
        action_name: str,
        params: dict[str, Any] | None = None,
        approved: bool = False,
    ) -> RemediationResult:
        """Execute a remediation action."""
        action = self._registry.get_action(action_name)
        if action is None:
            return RemediationResult(
                action_name=action_name,
                success=False,
                message=f"Unknown action: {action_name}",
            )

        if action.requires_approval and not approved:
            return RemediationResult(
                action_name=action_name,
                success=False,
                message="Action requires approval",
            )

        start = time.time()
        try:
            result = action.handler(**(params or {}))
            duration_ms = (time.time() - start) * 1000
            exec_result = RemediationResult(
                action_name=action_name,
                success=True,
                message=str(result) if result else "Executed successfully",
                duration_ms=duration_ms,
            )
        except Exception as exc:
            duration_ms = (time.time() - start) * 1000
            exec_result = RemediationResult(
                action_name=action_name,
                success=False,
                message=str(exc),
                duration_ms=duration_ms,
            )
            logger.error("Remediation %s failed: %s", action_name, exc)

        self._audit_log.append(exec_result)
        logger.info(
            "Remediation %s: success=%s, duration=%.1fms",
            action_name,
            exec_result.success,
            exec_result.duration_ms,
        )
        return exec_result

    def get_audit_log(self) -> list[RemediationResult]:
        """Get the audit log of executed actions."""
        return list(self._audit_log)


class ActionFeedbackLoop:
    """Verifies remediation effectiveness by comparing before/after state."""

    def __init__(self, check_interval_s: float = 30.0, max_checks: int = 5) -> None:
        self._check_interval = check_interval_s
        self._max_checks = max_checks

    def verify(
        self,
        action_name: str,
        health_checker: Callable[[], str],
    ) -> dict[str, Any]:
        """Verify that a remediation improved service health.

        Returns verification result with before/after status.
        """
        before_status = health_checker()
        return {
            "action": action_name,
            "before_status": before_status,
            "verified": False,
            "message": "Verification requires async polling (call check_status periodically)",
        }
