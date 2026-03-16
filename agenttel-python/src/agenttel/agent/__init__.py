"""Agent interface for AgentTel (MCP server, health, identity, incident)."""

from agenttel.agent.health import ServiceHealthAggregator
from agenttel.agent.identity import AgentIdentity, ToolPermissionRegistry

__all__ = [
    "ServiceHealthAggregator",
    "AgentIdentity",
    "ToolPermissionRegistry",
]
