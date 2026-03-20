"""Agent interface for AgentTel (MCP server, health, identity, incident)."""

from agenttel.agent.auth import (
    AuthConfig,
    ToolPermission,
    ToolPermissionRegistry as AuthPermissionRegistry,
)
from agenttel.agent.health import ServiceHealthAggregator
from agenttel.agent.identity import AgentIdentity, ToolPermissionRegistry

__all__ = [
    "AuthConfig",
    "AuthPermissionRegistry",
    "ServiceHealthAggregator",
    "AgentIdentity",
    "ToolPermission",
    "ToolPermissionRegistry",
]
