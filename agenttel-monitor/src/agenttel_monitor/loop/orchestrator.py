from __future__ import annotations

import asyncio
import logging
import signal
from typing import Optional

from ..config.types import MonitorConfig
from ..llm.anthropic_provider import AnthropicProvider
from ..mcp.client import McpClient
from ..notifications.webhook import WebhookNotifier
from ..tracing.monitor_tracer import setup_tracing
from .actor import Actor
from .improver import Improver
from .investigator import Investigator
from .learner import Learner
from .reasoner import Reasoner
from .verifier import Verifier
from .watcher import Watcher

logger = logging.getLogger(__name__)


class MonitorOrchestrator:
    """Main orchestrator running the Watch -> Investigate -> Reason -> Act -> Verify -> Learn loop."""

    def __init__(self, config: MonitorConfig) -> None:
        self._config = config
        self._running = False

        # Components (initialized in _setup)
        self._mcp_client: Optional[McpClient] = None
        self._watcher: Optional[Watcher] = None
        self._investigator: Optional[Investigator] = None
        self._reasoner: Optional[Reasoner] = None
        self._actor: Optional[Actor] = None
        self._verifier: Optional[Verifier] = None
        self._learner: Optional[Learner] = None
        self._improver: Optional[Improver] = None
        self._tracer = None

    async def _setup(self) -> None:
        """Initialize all components."""
        # Tracing
        if self._config.tracing.enabled:
            self._tracer = setup_tracing(
                service_name=self._config.tracing.service_name,
                otlp_endpoint=self._config.tracing.otlp_endpoint,
            )

        # MCP client
        self._mcp_client = McpClient(self._config.mcp)
        await self._mcp_client.start()

        # LLM provider
        llm = AnthropicProvider(self._config.llm)

        # Notifications
        notifier: WebhookNotifier | None = None
        if self._config.notifications.webhook_url:
            notifier = WebhookNotifier(
                webhook_url=self._config.notifications.webhook_url,
                timeout_seconds=self._config.notifications.timeout_seconds,
            )

        # Loop components
        self._watcher = Watcher(self._mcp_client, self._config.watch)
        self._investigator = Investigator(self._mcp_client)
        self._reasoner = Reasoner(llm)
        self._actor = Actor(self._mcp_client, self._config.actions, notifier)
        self._verifier = Verifier(self._mcp_client)
        self._learner = Learner(self._config.learning)
        self._improver = Improver(self._config.improve)

    async def _teardown(self) -> None:
        """Clean up resources."""
        if self._mcp_client is not None:
            await self._mcp_client.close()

    async def run(self) -> None:
        """Run the main monitoring loop until interrupted."""
        logging.basicConfig(
            level=logging.INFO,
            format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        )

        logger.info("Starting AgentTel Monitor")
        logger.info(
            "Config: mcp=%s, interval=%.1fs, model=%s",
            self._config.mcp.base_url,
            self._config.watch.interval_seconds,
            self._config.llm.model,
        )

        await self._setup()
        self._running = True

        # Handle graceful shutdown
        loop = asyncio.get_running_loop()
        for sig in (signal.SIGINT, signal.SIGTERM):
            loop.add_signal_handler(sig, self._handle_shutdown)

        try:
            while self._running:
                await self._tick()
                await asyncio.sleep(self._config.watch.interval_seconds)
        except asyncio.CancelledError:
            logger.info("Monitor loop cancelled")
        finally:
            await self._teardown()
            logger.info("AgentTel Monitor stopped")

    def _handle_shutdown(self) -> None:
        """Signal handler for graceful shutdown."""
        logger.info("Shutdown signal received")
        self._running = False

    async def _tick(self) -> None:
        """Execute one full Watch -> Investigate -> Reason -> Act -> Verify -> Learn cycle."""
        span = None
        if self._tracer is not None:
            span = self._tracer.start_span("monitor.tick")

        try:
            # 1. Watch
            watch_result = await self._watcher.poll()

            if not watch_result.is_degraded:
                logger.debug("System healthy, nothing to do")
                if span is not None:
                    span.set_attribute("monitor.status", "healthy")
                    span.end()
                return

            logger.info(
                "Degradation detected (status=%s), entering investigation",
                watch_result.overall_status,
            )

            if span is not None:
                span.set_attribute("monitor.status", "degraded")
                span.set_attribute(
                    "monitor.anomalous_ops",
                    len(watch_result.anomalous_operations),
                )

            # 2. Investigate
            context = await self._investigator.investigate(watch_result)

            # 3. Reason
            diagnosis = await self._reasoner.diagnose(context)
            logger.info(
                "Diagnosis: root_cause='%s', severity=%s, confidence=%.2f",
                diagnosis.root_cause[:100],
                diagnosis.severity,
                diagnosis.confidence,
            )

            if span is not None:
                span.set_attribute("monitor.root_cause", diagnosis.root_cause[:200])
                span.set_attribute("monitor.severity", diagnosis.severity)
                span.set_attribute("monitor.confidence", diagnosis.confidence)

            # 4. Act
            act_result = await self._actor.act(diagnosis)

            # 5. Verify
            verification = await self._verifier.verify(act_result)
            logger.info(
                "Verification: recovered=%s, post_status=%s",
                verification.recovered,
                verification.post_status,
            )

            if span is not None:
                span.set_attribute("monitor.recovered", verification.recovered)

            # 6. Learn
            await self._learner.record(context, diagnosis, act_result, verification)

            # 7. Improve — refine instrumentation config based on observed data
            if self._improver is not None:
                improvements = await self._improver.improve()
                applied_count = improvements.get("applied_count", 0)
                if applied_count > 0:
                    logger.info("Improve phase: %d changes applied to config", applied_count)
                    if span is not None:
                        span.set_attribute("monitor.improvements_applied", applied_count)

        except Exception as exc:
            logger.error("Error in monitor tick: %s", exc, exc_info=True)
            if span is not None:
                span.set_attribute("monitor.error", str(exc))
        finally:
            if span is not None:
                span.end()
