from __future__ import annotations

from abc import ABC, abstractmethod


class LlmProvider(ABC):
    """Abstract base class for LLM providers."""

    @abstractmethod
    async def complete(self, system: str, user: str) -> str:
        """Send a system + user prompt and return the completion text.

        Args:
            system: The system prompt establishing context and role.
            user: The user prompt with the specific request.

        Returns:
            The LLM's text response.
        """
        ...
