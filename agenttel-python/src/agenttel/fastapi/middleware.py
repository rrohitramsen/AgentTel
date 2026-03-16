"""AgentTel middleware for FastAPI/Starlette."""

from __future__ import annotations

from typing import Any

from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import Response
from starlette.routing import Match

from opentelemetry import trace

from agenttel import attributes as attr
from agenttel.engine import AgentTelEngine


def instrument_fastapi(
    app: Any,
    config_path: str = "agenttel.yml",
    engine: AgentTelEngine | None = None,
) -> AgentTelEngine:
    """One-line integration for FastAPI apps.

    Args:
        app: FastAPI application instance.
        config_path: Path to agenttel.yml configuration file.
        engine: Optional pre-configured engine. If None, one is created from config.

    Returns:
        The AgentTelEngine instance.
    """
    if engine is None:
        engine = AgentTelEngine.from_config(config_path)

    engine.install()
    app.add_middleware(AgentTelMiddleware, engine=engine)
    return engine


class AgentTelMiddleware(BaseHTTPMiddleware):
    """Starlette middleware that enriches spans with AgentTel attributes."""

    def __init__(self, app: Any, engine: AgentTelEngine) -> None:
        super().__init__(app)
        self._engine = engine

    async def dispatch(
        self, request: Request, call_next: RequestResponseEndpoint
    ) -> Response:
        """Process request and enrich the active span."""
        span = trace.get_current_span()

        if span and span.is_recording():
            # Resolve route template from FastAPI router
            route_template = self._resolve_route(request)
            if route_template:
                span.set_attribute("http.route", route_template)

            # Set topology attributes
            topo_attrs = self._engine.topology.get_topology_attributes()
            for key, value in topo_attrs.items():
                span.set_attribute(key, value)

            # Resolve and set operation context
            method = request.method
            if route_template:
                operation_name = f"[{method} {route_template}]"
                op_config = self._engine.config.operations.get(operation_name)
                if op_config:
                    self._set_operation_attributes(span, operation_name, op_config)

        response = await call_next(request)
        return response

    def _resolve_route(self, request: Request) -> str | None:
        """Resolve the route template from the FastAPI/Starlette router."""
        if not hasattr(request, "app"):
            return None

        app = request.app
        # Walk through Starlette app to find routes
        routes = getattr(app, "routes", [])
        for route in routes:
            match, _ = route.matches(request.scope)
            if match == Match.FULL:
                return getattr(route, "path", None)
        return request.url.path

    def _set_operation_attributes(
        self, span: Any, operation_name: str, op_config: Any
    ) -> None:
        """Set operation-level attributes from config."""
        config = self._engine.config
        profile = None
        if op_config.profile and op_config.profile in config.profiles:
            profile = config.profiles[op_config.profile]

        if profile:
            span.set_attribute(attr.OPERATION_RETRYABLE, profile.retryable)
            span.set_attribute(attr.OPERATION_IDEMPOTENT, profile.idempotent)
            span.set_attribute(attr.OPERATION_SAFE_TO_RESTART, profile.safe_to_restart)
            span.set_attribute(
                attr.OPERATION_ESCALATION_LEVEL, profile.escalation_level.value
            )
            if profile.fallback_description:
                span.set_attribute(
                    attr.OPERATION_FALLBACK_DESCRIPTION, profile.fallback_description
                )

        if op_config.runbook_url:
            span.set_attribute(attr.OPERATION_RUNBOOK_URL, op_config.runbook_url)
        if op_config.profile:
            span.set_attribute(attr.OPERATION_PROFILE, op_config.profile)
