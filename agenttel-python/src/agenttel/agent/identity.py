"""Agent identity and permission management."""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from typing import Any

from agenttel.enums import AgentRole


@dataclass
class AgentIdentity:
    """Represents an AI agent's identity for MCP access."""

    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    role: AgentRole = AgentRole.OBSERVER
    session_id: str = field(default_factory=lambda: str(uuid.uuid4()))
    name: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_headers(cls, headers: dict[str, str]) -> AgentIdentity:
        """Extract agent identity from HTTP request headers."""
        return cls(
            id=headers.get("x-agent-id", str(uuid.uuid4())),
            role=AgentRole(headers.get("x-agent-role", "observer")),
            session_id=headers.get("x-agent-session-id", str(uuid.uuid4())),
            name=headers.get("x-agent-name", ""),
        )


# Default tool permissions per role
_DEFAULT_PERMISSIONS: dict[AgentRole, set[str]] = {
    AgentRole.OBSERVER: {
        "get_service_health",
        "get_operation_baselines",
        "get_anomalies",
        "get_slo_status",
        "get_dependency_health",
        "get_topology",
        "get_error_classification",
        "get_deployment_info",
    },
    AgentRole.DIAGNOSTICIAN: {
        "get_service_health",
        "get_operation_baselines",
        "get_anomalies",
        "get_slo_status",
        "get_dependency_health",
        "get_topology",
        "get_incident_context",
        "get_error_classification",
        "get_deployment_info",
        "get_change_correlation",
        "get_slo_report",
        "get_trend_analysis",
        "get_executive_summary",
        "suggest_remediation",
    },
    AgentRole.REMEDIATOR: {
        "get_service_health",
        "get_operation_baselines",
        "get_anomalies",
        "get_slo_status",
        "get_dependency_health",
        "get_topology",
        "get_incident_context",
        "get_error_classification",
        "get_deployment_info",
        "get_change_correlation",
        "get_slo_report",
        "get_trend_analysis",
        "get_executive_summary",
        "suggest_remediation",
        "execute_remediation",
    },
    AgentRole.ADMIN: set(),  # Empty = all tools allowed
}


class ToolPermissionRegistry:
    """Manages role-based tool access for MCP."""

    def __init__(self) -> None:
        self._permissions: dict[AgentRole, set[str]] = dict(_DEFAULT_PERMISSIONS)

    def is_allowed(self, role: AgentRole, tool_name: str) -> bool:
        """Check if a role has permission to use a tool."""
        allowed = self._permissions.get(role)
        if allowed is not None and len(allowed) == 0:
            return True  # Empty set for admin = all allowed
        return tool_name in (allowed or set())

    def grant(self, role: AgentRole, tool_name: str) -> None:
        """Grant a tool permission to a role."""
        if role not in self._permissions:
            self._permissions[role] = set()
        self._permissions[role].add(tool_name)

    def revoke(self, role: AgentRole, tool_name: str) -> None:
        """Revoke a tool permission from a role."""
        if role in self._permissions:
            self._permissions[role].discard(tool_name)
