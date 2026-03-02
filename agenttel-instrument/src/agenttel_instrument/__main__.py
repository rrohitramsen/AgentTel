"""Entry point for the AgentTel Instrumentation MCP server."""

import asyncio
import argparse
import logging
import os
import signal

from .config.config import load_config
from .mcp.server import McpServer
from .tools import (
    analyze_codebase,
    apply_improvements,
    apply_single,
    instrument_backend,
    instrument_frontend,
    validate,
    suggest,
)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
logger = logging.getLogger(__name__)


def _register_tools(server: McpServer) -> None:
    """Register all instrumentation tools with the MCP server."""
    server.register_tool(analyze_codebase.TOOL_DEFINITION, analyze_codebase.handle)
    server.register_tool(instrument_backend.TOOL_DEFINITION, instrument_backend.handle)
    server.register_tool(instrument_frontend.TOOL_DEFINITION, instrument_frontend.handle)
    server.register_tool(validate.TOOL_DEFINITION, validate.handle)
    server.register_tool(suggest.TOOL_DEFINITION, suggest.handle)
    server.register_tool(apply_improvements.TOOL_DEFINITION, apply_improvements.handle)
    server.register_tool(apply_single.TOOL_DEFINITION, apply_single.handle)


async def _run(config_path: str) -> None:
    config = load_config(config_path)

    server = McpServer(
        host=config.server.host,
        port=config.server.port,
        agenttel_config_path=os.environ.get("AGENTTEL_CONFIG_PATH", "/app/agenttel.yml"),
    )
    _register_tools(server)
    await server.start()

    logger.info(
        "AgentTel Instrumentation server ready at http://%s:%d",
        config.server.host, config.server.port,
    )

    # Wait until interrupted
    stop_event = asyncio.Event()

    def _signal_handler():
        logger.info("Shutting down...")
        stop_event.set()

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, _signal_handler)

    await stop_event.wait()


def main():
    parser = argparse.ArgumentParser(description="AgentTel Instrumentation MCP Server")
    parser.add_argument("--config", default="instrument.yml", help="Config file path")
    args = parser.parse_args()
    asyncio.run(_run(args.config))


if __name__ == "__main__":
    main()
