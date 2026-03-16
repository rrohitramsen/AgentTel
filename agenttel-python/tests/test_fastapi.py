"""Tests for FastAPI integration."""

from agenttel.fastapi.decorators import _parse_latency


class TestParseLatency:
    def test_parse_ms(self):
        assert _parse_latency("100ms") == 100.0

    def test_parse_seconds(self):
        assert _parse_latency("2s") == 2000.0

    def test_parse_none(self):
        assert _parse_latency(None) is None

    def test_parse_plain_number(self):
        assert _parse_latency("50") == 50.0

    def test_parse_with_spaces(self):
        assert _parse_latency("  200ms  ") == 200.0
