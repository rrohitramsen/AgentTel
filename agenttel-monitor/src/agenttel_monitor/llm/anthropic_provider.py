from __future__ import annotations

import logging
import os

import anthropic

from ..config.types import LlmConfig
from .provider import LlmProvider

logger = logging.getLogger(__name__)


class AnthropicProvider(LlmProvider):
    """LLM provider backed by the Anthropic SDK (Claude models)."""

    def __init__(self, config: LlmConfig) -> None:
        self._config = config
        api_key = config.api_key or os.environ.get("ANTHROPIC_API_KEY")
        if not api_key:
            raise ValueError(
                "Anthropic API key not provided. Set llm.api_key in config "
                "or the ANTHROPIC_API_KEY environment variable."
            )
        self._client = anthropic.AsyncAnthropic(api_key=api_key)

    async def complete(self, system: str, user: str) -> str:
        """Send prompts to Claude and return the text response."""
        logger.debug(
            "Calling Anthropic: model=%s, max_tokens=%d",
            self._config.model,
            self._config.max_tokens,
        )

        message = await self._client.messages.create(
            model=self._config.model,
            max_tokens=self._config.max_tokens,
            temperature=self._config.temperature,
            system=system,
            messages=[
                {"role": "user", "content": user},
            ],
        )

        # Extract text from the response content blocks
        text_parts: list[str] = []
        for block in message.content:
            if hasattr(block, "text"):
                text_parts.append(block.text)

        result = "\n".join(text_parts)
        logger.debug("Anthropic response length: %d chars", len(result))
        return result
