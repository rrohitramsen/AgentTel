"""AWS Bedrock Runtime instrumentation wrapper."""

from __future__ import annotations

import functools
import json
from typing import Any

from opentelemetry import trace

from agenttel.genai.span_builder import GenAiSpanBuilder


def instrument_bedrock(client: Any = None) -> Any:
    """Instrument a boto3 Bedrock Runtime client for tracing.

    Wraps invoke_model() and converse() to create gen_ai chat spans.

    Args:
        client: A boto3 Bedrock Runtime client.

    Returns:
        The instrumented client.
    """
    if client is None:
        return client

    span_builder = GenAiSpanBuilder()

    # Wrap invoke_model
    if hasattr(client, "invoke_model"):
        _wrap_invoke_model(client, span_builder)

    # Wrap converse
    if hasattr(client, "converse"):
        _wrap_converse(client, span_builder)

    return client


def _wrap_invoke_model(client: Any, span_builder: GenAiSpanBuilder) -> None:
    """Wrap invoke_model method."""
    original = client.invoke_model

    @functools.wraps(original)
    def traced_invoke_model(**kwargs: Any) -> Any:
        model_id = kwargs.get("modelId", "unknown")
        span = span_builder.start_chat_span(
            model=model_id,
            system="bedrock",
        )

        try:
            with trace.use_span(span, end_on_exit=False):
                response = original(**kwargs)

                # Try to extract usage from response body
                input_tokens = 0
                output_tokens = 0
                if "body" in response:
                    try:
                        body = json.loads(response["body"].read())
                        response["body"] = body  # Replace with parsed body
                        usage = body.get("usage", {})
                        input_tokens = usage.get("input_tokens", 0)
                        output_tokens = usage.get("output_tokens", 0)
                    except (json.JSONDecodeError, AttributeError):
                        pass

                span_builder.end_span_with_response(
                    span=span,
                    model=model_id,
                    input_tokens=input_tokens,
                    output_tokens=output_tokens,
                )
                return response
        except Exception as exc:
            span.set_status(trace.Status(trace.StatusCode.ERROR, str(exc)))
            span.record_exception(exc)
            span.end()
            raise

    client.invoke_model = traced_invoke_model


def _wrap_converse(client: Any, span_builder: GenAiSpanBuilder) -> None:
    """Wrap converse method."""
    original = client.converse

    @functools.wraps(original)
    def traced_converse(**kwargs: Any) -> Any:
        model_id = kwargs.get("modelId", "unknown")
        span = span_builder.start_chat_span(
            model=model_id,
            system="bedrock",
        )

        try:
            with trace.use_span(span, end_on_exit=False):
                response = original(**kwargs)

                input_tokens = 0
                output_tokens = 0
                finish_reason = None

                usage = response.get("usage", {})
                input_tokens = usage.get("inputTokens", 0)
                output_tokens = usage.get("outputTokens", 0)
                finish_reason = response.get("stopReason")

                span_builder.end_span_with_response(
                    span=span,
                    model=model_id,
                    input_tokens=input_tokens,
                    output_tokens=output_tokens,
                    finish_reason=finish_reason,
                )
                return response
        except Exception as exc:
            span.set_status(trace.Status(trace.StatusCode.ERROR, str(exc)))
            span.record_exception(exc)
            span.end()
            raise

    client.converse = traced_converse
