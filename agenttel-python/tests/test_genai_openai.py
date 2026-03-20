"""Tests for OpenAI GenAI instrumentation."""

from unittest.mock import MagicMock

from agenttel.genai.openai import instrument_openai
from agenttel.genai import attributes as genai_attr


def create_mock_openai_client():
    """Create a mock OpenAI client with the expected structure."""
    client = MagicMock()
    client.chat = MagicMock()
    client.chat.completions = MagicMock()
    return client


def create_mock_response(
    model="gpt-4o",
    response_id="chatcmpl-abc123",
    prompt_tokens=100,
    completion_tokens=50,
    finish_reason="stop",
    reasoning_tokens=0,
    cached_tokens=0,
):
    """Create a mock OpenAI response object."""
    response = MagicMock()
    response.id = response_id
    response.model = model
    response.usage = MagicMock()
    response.usage.prompt_tokens = prompt_tokens
    response.usage.completion_tokens = completion_tokens

    # Extended usage details
    if reasoning_tokens > 0:
        response.usage.completion_tokens_details = MagicMock()
        response.usage.completion_tokens_details.reasoning_tokens = reasoning_tokens
    else:
        response.usage.completion_tokens_details = None

    if cached_tokens > 0:
        response.usage.prompt_tokens_details = MagicMock()
        response.usage.prompt_tokens_details.cached_tokens = cached_tokens
    else:
        response.usage.prompt_tokens_details = None

    choice = MagicMock()
    choice.finish_reason = finish_reason
    response.choices = [choice]

    return response


class TestInstrumentOpenAI:
    def test_patches_chat_completions_create(self):
        """Verify that instrument_openai wraps chat.completions.create."""
        client = create_mock_openai_client()
        original_create = client.chat.completions.create

        result = instrument_openai(client)

        assert result is client
        assert client.chat.completions.create is not original_create

    def test_creates_span_with_correct_attrs(self, span_exporter):
        """Verify span has gen_ai.system=openai and gen_ai.request.model."""
        client = create_mock_openai_client()
        mock_response = create_mock_response()
        client.chat.completions.create = MagicMock(return_value=mock_response)

        instrument_openai(client)
        client.chat.completions.create(model="gpt-4o", messages=[])

        spans = span_exporter.get_finished_spans()
        assert len(spans) >= 1

        span = spans[-1]
        attrs = dict(span.attributes or {})

        assert attrs.get(genai_attr.SYSTEM) == "openai"
        assert attrs.get(genai_attr.REQUEST_MODEL) == "gpt-4o"

    def test_extracts_prompt_and_completion_tokens(self, span_exporter):
        """Verify that prompt_tokens and completion_tokens are extracted."""
        client = create_mock_openai_client()
        mock_response = create_mock_response(prompt_tokens=150, completion_tokens=75)
        client.chat.completions.create = MagicMock(return_value=mock_response)

        instrument_openai(client)
        client.chat.completions.create(model="gpt-4o", messages=[])

        spans = span_exporter.get_finished_spans()
        assert len(spans) >= 1

        span = spans[-1]
        attrs = dict(span.attributes or {})
        assert attrs.get(genai_attr.USAGE_INPUT_TOKENS) == 150
        assert attrs.get(genai_attr.USAGE_OUTPUT_TOKENS) == 75

    def test_handles_streaming(self, span_exporter):
        """Verify streaming response wraps in a generator."""
        client = create_mock_openai_client()
        # Create mock streaming chunks
        chunk1 = MagicMock()
        chunk1.model = "gpt-4o"
        chunk1.usage = None
        chunk1.choices = []

        chunk2 = MagicMock()
        chunk2.model = "gpt-4o"
        chunk2.usage = MagicMock()
        chunk2.usage.prompt_tokens = 100
        chunk2.usage.completion_tokens = 50
        chunk2.choices = [MagicMock()]
        chunk2.choices[0].finish_reason = "stop"

        client.chat.completions.create = MagicMock(return_value=iter([chunk1, chunk2]))

        instrument_openai(client)
        result = client.chat.completions.create(model="gpt-4o", messages=[], stream=True)

        # Consume the generator
        chunks = list(result)
        assert len(chunks) == 2

    def test_handles_errors(self, span_exporter):
        """Verify error is recorded on span and re-raised."""
        client = create_mock_openai_client()
        client.chat.completions.create = MagicMock(
            side_effect=RuntimeError("API rate limit exceeded")
        )

        instrument_openai(client)

        try:
            client.chat.completions.create(model="gpt-4o", messages=[])
            assert False, "Should have raised"
        except RuntimeError as e:
            assert "rate limit" in str(e)

        spans = span_exporter.get_finished_spans()
        assert len(spans) >= 1
        span = spans[-1]
        # Verify the span recorded the error
        events = span.events or []
        exception_events = [e for e in events if e.name == "exception"]
        assert len(exception_events) >= 1

    def test_handles_null_client(self):
        """Verify None client returns None gracefully."""
        result = instrument_openai(None)
        assert result is None

    def test_handles_reasoning_tokens(self, span_exporter):
        """Verify completion_tokens_details.reasoning_tokens is captured."""
        client = create_mock_openai_client()
        mock_response = create_mock_response(reasoning_tokens=500)
        client.chat.completions.create = MagicMock(return_value=mock_response)

        instrument_openai(client)
        client.chat.completions.create(model="o1", messages=[])

        spans = span_exporter.get_finished_spans()
        assert len(spans) >= 1
        span = spans[-1]
        attrs = dict(span.attributes or {})
        assert attrs.get(genai_attr.USAGE_REASONING_TOKENS) == 500

    def test_handles_cached_tokens(self, span_exporter):
        """Verify prompt_tokens_details.cached_tokens is captured."""
        client = create_mock_openai_client()
        mock_response = create_mock_response(cached_tokens=250)
        client.chat.completions.create = MagicMock(return_value=mock_response)

        instrument_openai(client)
        client.chat.completions.create(model="gpt-4o", messages=[])

        spans = span_exporter.get_finished_spans()
        assert len(spans) >= 1
        span = spans[-1]
        attrs = dict(span.attributes or {})
        assert attrs.get(genai_attr.USAGE_CACHED_TOKENS) == 250
