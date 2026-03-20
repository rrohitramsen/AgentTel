"""API key authentication and role-based permissions for the MCP server.

Opt-in: when no AuthConfig is provided, all checks are skipped
and the server behaves as before (backward compatible).
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Callable


class ToolPermission(Enum):
    """Permission levels for MCP tool access."""

    READ = "read"
    DIAGNOSE = "diagnose"
    REMEDIATE = "remediate"
    ADMIN = "admin"


@dataclass
class AgentIdentity:
    """Identifies an authenticated agent making MCP requests."""

    agent_id: str
    role: str
    session_id: str | None = None


@dataclass
class AuthConfig:
    """Configuration for API key authentication.

    Provide either a static api_keys map, a custom validate_key function,
    or both. When both are provided, the static map is checked first.
    """

    api_keys: dict[str, AgentIdentity] | None = None
    validate_key: Callable[[str], AgentIdentity | None] | None = None

    def resolve_key(self, key: str) -> AgentIdentity | None:
        """Resolve a bearer token to an agent identity.

        Returns None if the key is invalid or not found.
        """
        trimmed = key.strip()
        if not trimmed:
            return None

        if self.api_keys:
            identity = self.api_keys.get(trimmed)
            if identity is not None:
                return identity

        if self.validate_key:
            return self.validate_key(trimmed)

        return None


# Default role-to-permission mappings
_DEFAULT_ROLE_PERMISSIONS: dict[str, set[ToolPermission]] = {
    "observer": {ToolPermission.READ},
    "diagnostician": {ToolPermission.READ, ToolPermission.DIAGNOSE},
    "remediator": {ToolPermission.READ, ToolPermission.DIAGNOSE, ToolPermission.REMEDIATE},
    "admin": {ToolPermission.READ, ToolPermission.DIAGNOSE, ToolPermission.REMEDIATE, ToolPermission.ADMIN},
}

# Default tool-to-required-permission mappings
_DEFAULT_TOOL_PERMISSIONS: dict[str, ToolPermission] = {
    # READ tools
    "get_service_health": ToolPermission.READ,
    "get_operation_baselines": ToolPermission.READ,
    "get_anomalies": ToolPermission.READ,
    "get_slo_status": ToolPermission.READ,
    "get_dependency_health": ToolPermission.READ,
    "get_topology": ToolPermission.READ,
    "get_incident_context": ToolPermission.READ,
    "get_error_classification": ToolPermission.READ,
    "get_deployment_info": ToolPermission.READ,
    "get_slo_report": ToolPermission.READ,
    "get_executive_summary": ToolPermission.READ,
    "get_trend_analysis": ToolPermission.READ,
    "get_playbook": ToolPermission.READ,
    "get_session": ToolPermission.READ,
    # DIAGNOSE tools
    "verify_remediation_effect": ToolPermission.DIAGNOSE,
    "create_session": ToolPermission.DIAGNOSE,
    "add_session_entry": ToolPermission.DIAGNOSE,
    "suggest_remediation": ToolPermission.DIAGNOSE,
    "get_change_correlation": ToolPermission.DIAGNOSE,
    # REMEDIATE tools
    "execute_remediation": ToolPermission.REMEDIATE,
    "list_remediation_actions": ToolPermission.REMEDIATE,
}


class ToolPermissionRegistry:
    """Maps roles to granted permissions and tools to required permission levels.

    Pre-populated with sensible defaults:
        observer     -> [READ]
        diagnostician -> [READ, DIAGNOSE]
        remediator   -> [READ, DIAGNOSE, REMEDIATE]
        admin        -> [READ, DIAGNOSE, REMEDIATE, ADMIN]
    """

    def __init__(self) -> None:
        self._role_permissions: dict[str, set[ToolPermission]] = {
            k: set(v) for k, v in _DEFAULT_ROLE_PERMISSIONS.items()
        }
        self._tool_permissions: dict[str, ToolPermission] = dict(_DEFAULT_TOOL_PERMISSIONS)

    def set_role_permissions(self, role: str, perms: list[ToolPermission]) -> None:
        """Set the permissions granted to a role, replacing any existing mapping."""
        self._role_permissions[role.lower()] = set(perms)

    def set_tool_permission(self, tool_name: str, perm: ToolPermission) -> None:
        """Set the required permission level for a specific tool."""
        self._tool_permissions[tool_name] = perm

    def is_allowed(self, identity: AgentIdentity, tool_name: str) -> bool:
        """Check whether the given identity has permission to invoke the named tool.

        Unknown tools default to READ. Unknown roles get observer permissions.
        """
        role = identity.role.lower()
        granted = self._role_permissions.get(
            role, self._role_permissions.get("observer", {ToolPermission.READ})
        )
        required = self._tool_permissions.get(tool_name, ToolPermission.READ)
        return required in granted
