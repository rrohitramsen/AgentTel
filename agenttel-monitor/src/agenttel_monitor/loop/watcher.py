from __future__ import annotations

import logging
from dataclasses import dataclass, field

from ..config.types import WatchConfig
from ..mcp.client import McpClient
from ..mcp.models import OperationHealth, ServiceHealth

logger = logging.getLogger(__name__)


@dataclass
class WatchResult:
    """Outcome of a single watch poll."""
    service_health: ServiceHealth
    overall_status: str = "unknown"
    is_degraded: bool = False
    status_changed: bool = False
    anomalous_operations: list[OperationHealth] = field(default_factory=list)


class Watcher:
    """Polls service health and detects degradation."""

    def __init__(self, mcp_client: McpClient, config: WatchConfig) -> None:
        self._mcp = mcp_client
        self._config = config
        self._previous_status: str | None = None
        self._consecutive_failures: int = 0

    async def poll(self) -> WatchResult:
        """Perform a single health poll and return the result."""
        try:
            health = await self._mcp.get_service_health()
        except Exception as exc:
            logger.error("Health poll failed: %s", exc)
            self._consecutive_failures += 1
            return WatchResult(
                service_health=ServiceHealth(),
                overall_status="error",
                is_degraded=True,
                status_changed=self._previous_status != "error",
                anomalous_operations=[],
            )

        self._consecutive_failures = 0

        # Identify anomalous operations
        anomalous: list[OperationHealth] = []
        for op in health.operations:
            if self._is_anomalous(op):
                anomalous.append(op)

        overall = health.overall_status
        is_degraded = overall in ("degraded", "critical", "error") or len(anomalous) > 0
        status_changed = self._previous_status is not None and self._previous_status != overall

        self._previous_status = overall

        result = WatchResult(
            service_health=health,
            overall_status=overall,
            is_degraded=is_degraded,
            status_changed=status_changed,
            anomalous_operations=anomalous,
        )

        if is_degraded:
            logger.warning(
                "Degradation detected: status=%s, anomalous_ops=%d",
                overall,
                len(anomalous),
            )
        else:
            logger.debug("Health OK: status=%s", overall)

        return result

    def _is_anomalous(self, op: OperationHealth) -> bool:
        """Determine whether an operation is exhibiting anomalous behaviour."""
        if op.status in ("degraded", "critical", "error"):
            return True
        if op.error_rate > (1.0 - self._config.degradation_threshold):
            return True
        return False
