"""OpenAI SDK instrumentation wrapper."""

from __future__ import annotations

import functools
from typing import Any

from opentelemetry import context, trace

from agenttel.genai import attributes as genai_attr
from agenttel.genai.cost import ModelCostCalculator
from agenttel.genai.span_builder import GenAiSpanBuilder


def instrument_openai(client: Any = None) -> Any:
    """Instrument an OpenAI client for automatic tracing.

    Wraps chat.completions.create() (sync and async) to create
    gen_ai chat spans with full attribute instrumentation.

    Args:
        client: An OpenAI client instance. If None, patches the default client.

    Returns:
        The instrumented client.
    """
    if client is None:
        return client

    span_builder = GenAiSpanBuilder()
    _wrap_completions(client, span_builder)
    return client


def _wrap_completions(client: Any, span_builder: GenAiSpanBuilder) -> None:
    """Wrap the chat.completions.create method."""
    if not hasattr(client, "chat") or not hasattr(client.chat, "completions"):
        return

    original_create = client.chat.completions.create

    @functools.wraps(original_create)
    def traced_create(*args: Any, **kwargs: Any) -> Any:
        model = kwargs.get("model", "unknown")
        span = span_builder.start_chat_span(
            model=model,
            system="openai",
            temperature=kwargs.get("temperature"),
            max_tokens=kwargs.get("max_tokens"),
            top_p=kwargs.get("top_p"),
        )

        try:
            with trace.use_span(span, end_on_exit=False):
                response = original_create(*args, **kwargs)

                # Handle streaming
                if kwargs.get("stream"):
                    return _wrap_stream(response, span, span_builder, model)

                # Extract response data
                _finalize_span(span, span_builder, response, model)
                return response
        except Exception as exc:
            span.set_status(trace.Status(trace.StatusCode.ERROR, str(exc)))
            span.record_exception(exc)
            span.end()
            raise

    client.chat.completions.create = traced_create


def _finalize_span(
    span: trace.Span,
    span_builder: GenAiSpanBuilder,
    response: Any,
    model: str,
) -> None:
    """Extract response data and end span."""
    input_tokens = 0
    output_tokens = 0
    reasoning_tokens = 0
    cached_tokens = 0
    finish_reason = None
    response_id = None
    response_model = model

    if hasattr(response, "usage") and response.usage:
        input_tokens = getattr(response.usage, "prompt_tokens", 0) or 0
        output_tokens = getattr(response.usage, "completion_tokens", 0) or 0
        # Extended usage fields
        if hasattr(response.usage, "completion_tokens_details"):
            details = response.usage.completion_tokens_details
            if details:
                reasoning_tokens = getattr(details, "reasoning_tokens", 0) or 0
        if hasattr(response.usage, "prompt_tokens_details"):
            details = response.usage.prompt_tokens_details
            if details:
                cached_tokens = getattr(details, "cached_tokens", 0) or 0

    if hasattr(response, "id"):
        response_id = response.id
    if hasattr(response, "model"):
        response_model = response.model
    if hasattr(response, "choices") and response.choices:
        finish_reason = getattr(response.choices[0], "finish_reason", None)

    span_builder.end_span_with_response(
        span=span,
        model=response_model,
        input_tokens=input_tokens,
        output_tokens=output_tokens,
        reasoning_tokens=reasoning_tokens,
        cached_tokens=cached_tokens,
        finish_reason=finish_reason,
        response_id=response_id,
    )


def _wrap_stream(
    stream: Any,
    span: trace.Span,
    span_builder: GenAiSpanBuilder,
    model: str,
) -> Any:
    """Wrap a streaming response to capture final usage."""
    input_tokens = 0
    output_tokens = 0
    finish_reason = None
    response_model = model

    def _gen():
        nonlocal input_tokens, output_tokens, finish_reason, response_model
        try:
            for chunk in stream:
                if hasattr(chunk, "model") and chunk.model:
                    response_model = chunk.model
                if hasattr(chunk, "usage") and chunk.usage:
                    input_tokens = getattr(chunk.usage, "prompt_tokens", 0) or 0
                    output_tokens = getattr(chunk.usage, "completion_tokens", 0) or 0
                if hasattr(chunk, "choices") and chunk.choices:
                    fr = getattr(chunk.choices[0], "finish_reason", None)
                    if fr:
                        finish_reason = fr
                yield chunk
        finally:
            span_builder.end_span_with_response(
                span=span,
                model=response_model,
                input_tokens=input_tokens,
                output_tokens=output_tokens,
                finish_reason=finish_reason,
            )

    return _gen()
