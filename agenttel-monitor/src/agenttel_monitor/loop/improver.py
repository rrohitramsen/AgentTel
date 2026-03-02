"""Improve phase — calls the instrument agent to apply low-risk improvements."""

from __future__ import annotations

import json
import logging

import httpx

from ..config.types import ImproveConfig

logger = logging.getLogger(__name__)


class Improver:
    """Calls the instrument agent's apply_improvements tool after each incident cycle."""

    def __init__(self, config: ImproveConfig) -> None:
        self._config = config

    async def improve(self) -> dict:
        """Call the instrument agent to detect and apply improvements."""
        if not self._config.enabled:
            return {}

        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                payload = {
                    "jsonrpc": "2.0",
                    "method": "tools/call",
                    "params": {
                        "name": "apply_improvements",
                        "arguments": {
                            "config_path": self._config.config_path,
                        },
                    },
                    "id": 1,
                }

                resp = await client.post(
                    f"{self._config.instrument_url}/mcp",
                    json=payload,
                )
                resp.raise_for_status()
                data = resp.json()

                # Parse the result
                result = data.get("result", {})
                content = result.get("content", [])
                if content:
                    text = content[0].get("text", "")
                    try:
                        improvements = json.loads(text)
                    except json.JSONDecodeError:
                        improvements = {"raw": text}

                    applied = improvements.get("applied", [])
                    pending_count = improvements.get("pending_count", 0)

                    if applied:
                        for change in applied:
                            logger.info("[IMPROVE] Applied: %s", change)

                    if pending_count:
                        logger.info(
                            "[IMPROVE] %d improvements need human review",
                            pending_count,
                        )

                    if not applied and not pending_count:
                        logger.debug("[IMPROVE] No improvements needed")

                    return improvements

        except Exception as exc:
            logger.warning("[IMPROVE] Failed to call instrument agent: %s", exc)

        return {}
