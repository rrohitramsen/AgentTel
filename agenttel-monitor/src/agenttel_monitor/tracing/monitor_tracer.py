from __future__ import annotations

import logging

from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

logger = logging.getLogger(__name__)


def setup_tracing(
    service_name: str = "agenttel.monitor",
    otlp_endpoint: str = "http://localhost:4318",
) -> trace.Tracer:
    """Configure OpenTelemetry tracing with OTLP HTTP export.

    Args:
        service_name: The service name to identify this monitor in traces.
        otlp_endpoint: The OTLP HTTP collector endpoint.

    Returns:
        A Tracer instance for creating spans.
    """
    resource = Resource.create(
        {
            "service.name": service_name,
            "service.version": "0.1.0",
        }
    )

    provider = TracerProvider(resource=resource)

    # OTLP HTTP exporter (sends to /v1/traces by default)
    otlp_exporter = OTLPSpanExporter(
        endpoint=f"{otlp_endpoint}/v1/traces",
    )

    processor = BatchSpanProcessor(otlp_exporter)
    provider.add_span_processor(processor)

    # Set as global tracer provider
    trace.set_tracer_provider(provider)

    logger.info(
        "OpenTelemetry tracing configured: service=%s, endpoint=%s",
        service_name,
        otlp_endpoint,
    )

    return trace.get_tracer(service_name)
