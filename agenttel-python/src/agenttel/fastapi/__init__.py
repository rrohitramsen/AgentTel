"""FastAPI integration for AgentTel."""

from agenttel.fastapi.config import auto_configure
from agenttel.fastapi.decorators import agent_operation

__all__ = [
    "AgentTelMiddleware",
    "instrument_fastapi",
    "auto_configure",
    "agent_operation",
]


def __getattr__(name: str):
    if name in ("AgentTelMiddleware", "instrument_fastapi"):
        from agenttel.fastapi.middleware import AgentTelMiddleware, instrument_fastapi

        _exports = {
            "AgentTelMiddleware": AgentTelMiddleware,
            "instrument_fastapi": instrument_fastapi,
        }
        globals().update(_exports)
        return _exports[name]
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
