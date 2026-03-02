from __future__ import annotations

import logging
from typing import Any

import httpx

logger = logging.getLogger(__name__)


class WebhookNotifier:
    """Sends JSON notifications to a webhook URL via HTTP POST."""

    def __init__(
        self,
        webhook_url: str,
        timeout_seconds: float = 10.0,
    ) -> None:
        self._url = webhook_url
        self._timeout = timeout_seconds

    async def send(self, payload: dict[str, Any]) -> bool:
        """POST a JSON payload to the configured webhook URL.

        Args:
            payload: The JSON-serializable dictionary to send.

        Returns:
            True if the webhook accepted the payload (2xx), False otherwise.
        """
        if not self._url:
            logger.warning("Webhook URL is not configured; notification skipped")
            return False

        logger.info("Sending webhook notification to %s", self._url)
        logger.debug("Webhook payload: %s", payload)

        try:
            async with httpx.AsyncClient(
                timeout=httpx.Timeout(self._timeout)
            ) as client:
                response = await client.post(
                    self._url,
                    json=payload,
                    headers={"Content-Type": "application/json"},
                )

            if response.is_success:
                logger.info("Webhook notification sent successfully (status=%d)", response.status_code)
                return True
            else:
                logger.warning(
                    "Webhook returned non-success status: %d %s",
                    response.status_code,
                    response.text[:200],
                )
                return False

        except httpx.TimeoutException:
            logger.error("Webhook request timed out after %.1fs", self._timeout)
            return False
        except httpx.HTTPError as exc:
            logger.error("Webhook HTTP error: %s", exc)
            return False
        except Exception as exc:
            logger.error("Unexpected webhook error: %s", exc)
            return False
