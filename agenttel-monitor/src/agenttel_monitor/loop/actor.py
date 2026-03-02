from __future__ import annotations

import logging
from dataclasses import dataclass, field

from ..config.types import ActionsConfig
from ..mcp.client import McpClient
from ..mcp.models import RemediationResult
from ..notifications.webhook import WebhookNotifier
from .reasoner import Diagnosis, RecommendedAction

logger = logging.getLogger(__name__)


@dataclass
class ActionOutcome:
    """Result of attempting to execute a recommended action."""
    action: RecommendedAction
    executed: bool = False
    auto_approved: bool = False
    escalated: bool = False
    result: RemediationResult | None = None
    error: str | None = None


@dataclass
class ActResult:
    """Aggregate result of all action attempts."""
    diagnosis: Diagnosis
    outcomes: list[ActionOutcome] = field(default_factory=list)

    @property
    def any_executed(self) -> bool:
        return any(o.executed for o in self.outcomes)

    @property
    def any_escalated(self) -> bool:
        return any(o.escalated for o in self.outcomes)


class Actor:
    """Executes auto-approved remediation actions and escalates the rest."""

    def __init__(
        self,
        mcp_client: McpClient,
        config: ActionsConfig,
        notifier: WebhookNotifier | None = None,
    ) -> None:
        self._mcp = mcp_client
        self._config = config
        self._notifier = notifier

    async def act(self, diagnosis: Diagnosis) -> ActResult:
        """Process all recommended actions from the diagnosis."""
        result = ActResult(diagnosis=diagnosis)

        if not diagnosis.recommended_actions:
            logger.info("No recommended actions to execute")
            return result

        for action in diagnosis.recommended_actions:
            outcome = await self._process_action(action, diagnosis)
            result.outcomes.append(outcome)

        executed_count = sum(1 for o in result.outcomes if o.executed)
        escalated_count = sum(1 for o in result.outcomes if o.escalated)
        logger.info(
            "Action phase complete: %d executed, %d escalated out of %d total",
            executed_count,
            escalated_count,
            len(result.outcomes),
        )

        return result

    async def _process_action(
        self, action: RecommendedAction, diagnosis: Diagnosis
    ) -> ActionOutcome:
        """Decide whether to auto-execute or escalate a single action."""
        outcome = ActionOutcome(action=action)

        action_type = action.action_type or action.action_id

        if self._config.dry_run:
            logger.info("[DRY RUN] Would execute action: %s (%s)", action.action_id, action_type)
            return outcome

        if self._is_auto_approved(action_type):
            outcome.auto_approved = True
            logger.info("Auto-approving action: %s (%s)", action.action_id, action_type)
            await self._execute(action, outcome)
        elif self._requires_approval(action_type):
            logger.info("Escalating action for approval: %s (%s)", action.action_id, action_type)
            await self._escalate(action, diagnosis, outcome)
        else:
            # Unknown action type -- escalate by default for safety
            logger.warning(
                "Unknown action type '%s', escalating for safety", action_type
            )
            await self._escalate(action, diagnosis, outcome)

        return outcome

    def _is_auto_approved(self, action_type: str) -> bool:
        """Check if the action type is in the auto-approve list."""
        return action_type.lower() in [a.lower() for a in self._config.auto_approve]

    def _requires_approval(self, action_type: str) -> bool:
        """Check if the action type requires manual approval."""
        return action_type.lower() in [a.lower() for a in self._config.require_approval]

    async def _execute(self, action: RecommendedAction, outcome: ActionOutcome) -> None:
        """Execute a remediation action via MCP."""
        try:
            action_id = action.action_id if action.action_id else action.action_type
            reason = action.reason or "Agent-initiated remediation"
            result = await self._mcp.execute_remediation(action_id=action_id, reason=reason)
            outcome.executed = True
            outcome.result = result
            if result.success:
                logger.info("Action %s executed successfully: %s", action_id, result.message)
            else:
                logger.warning("Action %s executed but failed: %s", action_id, result.message)
        except Exception as exc:
            outcome.error = str(exc)
            logger.error("Failed to execute action %s: %s", action.action_id, exc)

    async def _escalate(
        self,
        action: RecommendedAction,
        diagnosis: Diagnosis,
        outcome: ActionOutcome,
    ) -> None:
        """Escalate an action that requires manual approval via webhook notification."""
        outcome.escalated = True

        if self._notifier is None:
            logger.warning("No notifier configured; escalation logged but not sent")
            return

        payload = {
            "type": "escalation",
            "action": {
                "id": action.action_id,
                "type": action.action_type,
                "reason": action.reason,
                "priority": action.priority,
            },
            "diagnosis": {
                "root_cause": diagnosis.root_cause,
                "severity": diagnosis.severity,
                "confidence": diagnosis.confidence,
            },
            "message": (
                f"Action '{action.action_type}' requires manual approval. "
                f"Root cause: {diagnosis.root_cause}"
            ),
        }

        try:
            await self._notifier.send(payload)
            logger.info("Escalation notification sent for action %s", action.action_id)
        except Exception as exc:
            logger.error("Failed to send escalation notification: %s", exc)
