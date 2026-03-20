"""Shared test fixtures for AgentTel Python SDK."""

import pytest
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

# Set up a single global TracerProvider for all tests.
# OTel only allows set_tracer_provider() once per process.
_exporter = InMemorySpanExporter()
_provider = TracerProvider()
_provider.add_span_processor(SimpleSpanProcessor(_exporter))
trace.set_tracer_provider(_provider)


@pytest.fixture()
def span_exporter():
    """Provide a cleared in-memory span exporter for tests."""
    _exporter.clear()
    yield _exporter
    _exporter.clear()
