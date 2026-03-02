from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass

from ..mcp.client import McpClient
from ..mcp.models import ServiceHealth
from .actor import ActResult

logger = logging.getLogger(__name__)

DEFAULT_VERIFY_DELAY_SECONDS = 30.0


@dataclass
class VerificationResult:
    """Result of post-action health verification."""
    pre_status: str = "unknown"
    post_status: str = "unknown"
    recovered: bool = False
    still_degraded: bool = False
    post_health: ServiceHealth | None = None
    delay_seconds: float = DEFAULT_VERIFY_DELAY_SECONDS


class Verifier:
    """Re-checks service health after remediation to verify recovery."""

    def __init__(
        self,
        mcp_client: McpClient,
        delay_seconds: float = DEFAULT_VERIFY_DELAY_SECONDS,
    ) -> None:
        self._mcp = mcp_client
        self._delay = delay_seconds

    async def verify(self, act_result: ActResult) -> VerificationResult:
        """Wait, then re-check health to see if the situation improved."""
        result = VerificationResult(delay_seconds=self._delay)

        if not act_result.any_executed:
            logger.info("No actions were executed; skipping verification")
            result.pre_status = "n/a"
            result.post_status = "n/a"
            return result

        logger.info("Waiting %.1f seconds before verifying recovery...", self._delay)
        await asyncio.sleep(self._delay)

        try:
            health = await self._mcp.get_service_health()
            result.post_health = health
            result.post_status = health.overall_status

            # Determine recovery
            healthy_states = {"healthy", "ok", "normal"}
            degraded_states = {"degraded", "critical", "error"}

            if health.overall_status.lower() in healthy_states:
                result.recovered = True
                result.still_degraded = False
                logger.info("Service recovered: status=%s", health.overall_status)
            elif health.overall_status.lower() in degraded_states:
                result.recovered = False
                result.still_degraded = True
                logger.warning(
                    "Service still degraded after remediation: status=%s",
                    health.overall_status,
                )
            else:
                result.recovered = False
                result.still_degraded = False
                logger.info("Service status after verification: %s", health.overall_status)

        except Exception as exc:
            logger.error("Verification health check failed: %s", exc)
            result.post_status = "error"
            result.still_degraded = True

        return result
