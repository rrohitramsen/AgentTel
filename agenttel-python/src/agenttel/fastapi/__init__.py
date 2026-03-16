"""FastAPI integration for AgentTel."""

from agenttel.fastapi.config import auto_configure
from agenttel.fastapi.decorators import agent_operation
from agenttel.fastapi.middleware import AgentTelMiddleware, instrument_fastapi

__all__ = [
    "AgentTelMiddleware",
    "instrument_fastapi",
    "auto_configure",
    "agent_operation",
]
