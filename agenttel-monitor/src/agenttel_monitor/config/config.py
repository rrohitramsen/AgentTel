from __future__ import annotations

import os
from pathlib import Path

import yaml

from .types import MonitorConfig


def load_config(path: str) -> MonitorConfig:
    """Load monitor configuration from a YAML file.

    If the file does not exist, returns a MonitorConfig with defaults.
    Environment variables can override specific fields:
      - ANTHROPIC_API_KEY  -> llm.api_key
      - MCP_HOST           -> mcp.host
      - MCP_PORT           -> mcp.port
      - WEBHOOK_URL        -> notifications.webhook_url
    """
    config_path = Path(path)

    if config_path.exists():
        with open(config_path, "r") as f:
            raw = yaml.safe_load(f) or {}
    else:
        raw = {}

    config = MonitorConfig.model_validate(raw)

    # Environment variable overrides
    if api_key := os.environ.get("ANTHROPIC_API_KEY"):
        if config.llm.api_key is None:
            config.llm.api_key = api_key

    if mcp_host := os.environ.get("MCP_HOST"):
        config.mcp.host = mcp_host

    if mcp_port := os.environ.get("MCP_PORT"):
        config.mcp.port = int(mcp_port)

    if webhook_url := os.environ.get("WEBHOOK_URL"):
        config.notifications.webhook_url = webhook_url

    # OTEL tracing endpoint override (inside Docker: http://otel-collector:4318)
    if otlp_endpoint := os.environ.get("OTEL_EXPORTER_OTLP_ENDPOINT"):
        config.tracing.otlp_endpoint = otlp_endpoint

    return config
