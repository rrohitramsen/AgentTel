from __future__ import annotations

import os
from pathlib import Path

import yaml

from .types import InstrumentConfig


def load_config(path: str) -> InstrumentConfig:
    """Load instrument configuration from a YAML file.

    If the file does not exist, returns an InstrumentConfig with defaults.
    Environment variables can override specific fields:
      - MCP_HOST  -> backend_mcp.host
      - MCP_PORT  -> backend_mcp.port
      - SERVER_PORT -> server.port
    """
    config_path = Path(path)

    if config_path.exists():
        with open(config_path, "r") as f:
            raw = yaml.safe_load(f) or {}
    else:
        raw = {}

    config = InstrumentConfig.model_validate(raw)

    if mcp_host := os.environ.get("MCP_HOST"):
        config.backend_mcp.host = mcp_host

    if mcp_port := os.environ.get("MCP_PORT"):
        config.backend_mcp.port = int(mcp_port)

    if server_port := os.environ.get("SERVER_PORT"):
        config.server.port = int(server_port)

    return config
