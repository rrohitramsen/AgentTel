"""Auto-configuration for FastAPI integration."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from opentelemetry import trace
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider

from agenttel.config import AgentTelConfig
from agenttel.engine import AgentTelEngine


def auto_configure(
    app: Any,
    config_path: str | Path = "agenttel.yml",
    service_name: str | None = None,
) -> AgentTelEngine:
    """Auto-configure AgentTel for a FastAPI application.

    Loads agenttel.yml, configures all components, registers the
    AgentTelSpanProcessor, and sets up topology as OTel resource attributes.

    Args:
        app: FastAPI application instance.
        config_path: Path to agenttel.yml.
        service_name: Optional service name override.

    Returns:
        Configured AgentTelEngine.
    """
    config = AgentTelConfig.from_yaml(config_path)
    engine = AgentTelEngine(config)

    # Build resource attributes
    resource_attrs: dict[str, str] = {}
    if service_name:
        resource_attrs["service.name"] = service_name
    resource_attrs.update(engine.topology.get_topology_attributes())

    resource = Resource.create(resource_attrs)
    provider = TracerProvider(resource=resource)

    # Register AgentTel processor
    provider.add_span_processor(engine.processor)
    trace.set_tracer_provider(provider)

    # Add middleware
    from agenttel.fastapi.middleware import AgentTelMiddleware

    app.add_middleware(AgentTelMiddleware, engine=engine)

    # Emit deployment event
    if config.deployment.emit_on_startup:
        engine.event_emitter.emit_deployment(
            version=config.deployment.version,
            environment=config.deployment.environment,
            deployment_id=config.deployment.deployment_id,
        )

    return engine
