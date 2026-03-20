"""Tests for Anthropic GenAI instrumentation."""

from unittest.mock import MagicMock

from agenttel.genai.anthropic import instrument_anthropic
from agenttel.genai import attributes as genai_attr


def create_mock_anthropic_client():
    """Create a mock Anthropic client with the expected structure."""
    client = MagicMock()
    client.messages = MagicMock()
    return client


def create_mock_response(
    model="claude-opus-4",
    response_id="msg_abc123",
    input_tokens=500,
    output_tokens=200,
    stop_reason="end_turn",
    cache_read_input_tokens=0,
):
    """Create a mock Anthropic response object."""
    response = MagicMock()
    response.id = response_id
    response.model = model
    response.stop_reason = stop_reason
    response.usage = MagicMock()
    response.usage.input_tokens = input_tokens
    response.usage.output_tokens = output_tokens
    response.usage.cache_read_input_tokens = cache_read_input_tokens
    return response


class TestInstrumentAnthropic:
    def test_patches_messages_create(self):
        """Verify that instrument_anthropic wraps messages.create."""
        client = create_mock_anthropic_client()
        original_create = client.messages.create

        result = instrument_anthropic(client)

        assert result is client
        assert client.messages.create is not original_create

    def test_extracts_input_and_output_tokens(self, span_exporter):
        """Verify input_tokens and output_tokens are captured."""
        client = create_mock_anthropic_client()
        mock_response = create_mock_response(input_tokens=800, output_tokens=300)
        client.messages.create = MagicMock(return_value=mock_response)

        instrument_anthropic(client)
        client.messages.create(model="claude-opus-4", messages=[], max_tokens=1024)

        spans = span_exporter.get_finished_spans()
        assert len(spans) >= 1

        span = spans[-1]
        attrs = dict(span.attributes or {})
        assert attrs.get(genai_attr.USAGE_INPUT_TOKENS) == 800
        assert attrs.get(genai_attr.USAGE_OUTPUT_TOKENS) == 300

    def test_handles_stop_reason(self, span_exporter):
        """Verify stop_reason is captured as finish_reason."""
        client = create_mock_anthropic_client()
        mock_response = create_mock_response(stop_reason="end_turn")
        client.messages.create = MagicMock(return_value=mock_response)

        instrument_anthropic(client)
        client.messages.create(model="claude-opus-4", messages=[], max_tokens=1024)

        spans = span_exporter.get_finished_spans()
        assert len(spans) >= 1

        span = spans[-1]
        attrs = dict(span.attributes or {})
        finish_reasons = attrs.get(genai_attr.RESPONSE_FINISH_REASONS)
        assert finish_reasons is not None
        assert "end_turn" in (finish_reasons if isinstance(finish_reasons, (list, tuple)) else [finish_reasons])

    def test_handles_cache_read_input_tokens(self, span_exporter):
        """Verify cache_read_input_tokens is captured."""
        client = create_mock_anthropic_client()
        mock_response = create_mock_response(cache_read_input_tokens=300)
        client.messages.create = MagicMock(return_value=mock_response)

        instrument_anthropic(client)
        client.messages.create(model="claude-opus-4", messages=[], max_tokens=1024)

        spans = span_exporter.get_finished_spans()
        assert len(spans) >= 1

        span = spans[-1]
        attrs = dict(span.attributes or {})
        assert attrs.get(genai_attr.USAGE_CACHED_TOKENS) == 300

    def test_handles_streaming(self, span_exporter):
        """Verify streaming response wraps events correctly."""
        client = create_mock_anthropic_client()

        # Create mock streaming events
        message_start = MagicMock()
        message_start.type = "message_start"
        message_start.message = MagicMock()
        message_start.message.model = "claude-opus-4"
        message_start.message.usage = MagicMock()
        message_start.message.usage.input_tokens = 500

        message_delta = MagicMock()
        message_delta.type = "message_delta"
        message_delta.usage = MagicMock()
        message_delta.usage.output_tokens = 200
        message_delta.delta = MagicMock()
        message_delta.delta.stop_reason = "end_turn"

        client.messages.create = MagicMock(
            return_value=iter([message_start, message_delta])
        )

        instrument_anthropic(client)
        result = client.messages.create(
            model="claude-opus-4", messages=[], max_tokens=1024, stream=True
        )

        # Consume the generator
        events = list(result)
        assert len(events) == 2

    def test_handles_errors(self, span_exporter):
        """Verify error is recorded on span and re-raised."""
        client = create_mock_anthropic_client()
        client.messages.create = MagicMock(
            side_effect=RuntimeError("overloaded_error")
        )

        instrument_anthropic(client)

        try:
            client.messages.create(model="claude-opus-4", messages=[], max_tokens=1024)
            assert False, "Should have raised"
        except RuntimeError as e:
            assert "overloaded_error" in str(e)

        spans = span_exporter.get_finished_spans()
        assert len(spans) >= 1
        span = spans[-1]
        events = span.events or []
        exception_events = [e for e in events if e.name == "exception"]
        assert len(exception_events) >= 1

    def test_handles_null_client(self):
        """Verify None client returns None gracefully."""
        result = instrument_anthropic(None)
        assert result is None
