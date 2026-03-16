"""Anthropic SDK instrumentation wrapper."""

from __future__ import annotations

import functools
from typing import Any

from opentelemetry import trace

from agenttel.genai.span_builder import GenAiSpanBuilder


def instrument_anthropic(client: Any = None) -> Any:
    """Instrument an Anthropic client for automatic tracing.

    Wraps messages.create() to create gen_ai chat spans.

    Args:
        client: An anthropic.Anthropic client instance.

    Returns:
        The instrumented client.
    """
    if client is None:
        return client

    span_builder = GenAiSpanBuilder()
    _wrap_messages(client, span_builder)
    return client


def _wrap_messages(client: Any, span_builder: GenAiSpanBuilder) -> None:
    """Wrap the messages.create method."""
    if not hasattr(client, "messages"):
        return

    original_create = client.messages.create

    @functools.wraps(original_create)
    def traced_create(*args: Any, **kwargs: Any) -> Any:
        model = kwargs.get("model", "unknown")
        span = span_builder.start_chat_span(
            model=model,
            system="anthropic",
            temperature=kwargs.get("temperature"),
            max_tokens=kwargs.get("max_tokens"),
            top_p=kwargs.get("top_p"),
        )

        try:
            with trace.use_span(span, end_on_exit=False):
                response = original_create(*args, **kwargs)

                # Handle streaming
                if kwargs.get("stream"):
                    return _wrap_anthropic_stream(response, span, span_builder, model)

                _finalize_anthropic_span(span, span_builder, response, model)
                return response
        except Exception as exc:
            span.set_status(trace.Status(trace.StatusCode.ERROR, str(exc)))
            span.record_exception(exc)
            span.end()
            raise

    client.messages.create = traced_create


def _finalize_anthropic_span(
    span: trace.Span,
    span_builder: GenAiSpanBuilder,
    response: Any,
    model: str,
) -> None:
    """Extract Anthropic response data and end span."""
    input_tokens = 0
    output_tokens = 0
    cached_tokens = 0
    finish_reason = None
    response_id = None
    response_model = model

    if hasattr(response, "usage"):
        usage = response.usage
        input_tokens = getattr(usage, "input_tokens", 0) or 0
        output_tokens = getattr(usage, "output_tokens", 0) or 0
        cached_tokens = getattr(usage, "cache_read_input_tokens", 0) or 0

    if hasattr(response, "id"):
        response_id = response.id
    if hasattr(response, "model"):
        response_model = response.model
    if hasattr(response, "stop_reason"):
        finish_reason = response.stop_reason

    span_builder.end_span_with_response(
        span=span,
        model=response_model,
        input_tokens=input_tokens,
        output_tokens=output_tokens,
        cached_tokens=cached_tokens,
        finish_reason=finish_reason,
        response_id=response_id,
    )


def _wrap_anthropic_stream(
    stream: Any,
    span: trace.Span,
    span_builder: GenAiSpanBuilder,
    model: str,
) -> Any:
    """Wrap an Anthropic streaming response."""
    input_tokens = 0
    output_tokens = 0
    finish_reason = None
    response_model = model

    def _gen():
        nonlocal input_tokens, output_tokens, finish_reason, response_model
        try:
            for event in stream:
                event_type = getattr(event, "type", "")
                if event_type == "message_start" and hasattr(event, "message"):
                    msg = event.message
                    if hasattr(msg, "model"):
                        response_model = msg.model
                    if hasattr(msg, "usage"):
                        input_tokens = getattr(msg.usage, "input_tokens", 0) or 0
                elif event_type == "message_delta":
                    if hasattr(event, "usage"):
                        output_tokens = getattr(event.usage, "output_tokens", 0) or 0
                    if hasattr(event, "delta"):
                        finish_reason = getattr(event.delta, "stop_reason", None)
                yield event
        finally:
            span_builder.end_span_with_response(
                span=span,
                model=response_model,
                input_tokens=input_tokens,
                output_tokens=output_tokens,
                finish_reason=finish_reason,
            )

    return _gen()
