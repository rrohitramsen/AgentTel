from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field

from ..llm.provider import LlmProvider
from ..llm.prompts import DIAGNOSIS_PROMPT, SYSTEM_PROMPT
from .investigator import InvestigationContext

logger = logging.getLogger(__name__)


@dataclass
class RecommendedAction:
    """A single recommended remediation action from the LLM."""
    action_id: str = ""
    action_type: str = ""
    reason: str = ""
    priority: int = 0


@dataclass
class Diagnosis:
    """Structured diagnosis produced by the reasoning step."""
    root_cause: str = ""
    severity: str = "unknown"
    confidence: float = 0.0
    reasoning: str = ""
    recommended_actions: list[RecommendedAction] = field(default_factory=list)
    raw_response: str = ""


class Reasoner:
    """Sends investigation context to the LLM and parses a structured diagnosis."""

    def __init__(self, llm: LlmProvider) -> None:
        self._llm = llm

    async def diagnose(self, context: InvestigationContext) -> Diagnosis:
        """Ask the LLM to reason about the incident and return a structured diagnosis."""
        user_prompt = DIAGNOSIS_PROMPT.format(context=context.to_text())

        logger.info("Sending investigation context to LLM for diagnosis")
        raw_response = await self._llm.complete(system=SYSTEM_PROMPT, user=user_prompt)
        logger.debug("LLM raw response:\n%s", raw_response)

        return self._parse_response(raw_response)

    def _parse_response(self, raw: str) -> Diagnosis:
        """Parse the structured LLM response into a Diagnosis object."""
        diagnosis = Diagnosis(raw_response=raw)

        # Parse ROOT_CAUSE
        root_cause_match = re.search(
            r"ROOT_CAUSE:\s*(.+?)(?=\n(?:SEVERITY|CONFIDENCE|REASONING|RECOMMENDED_ACTIONS):|\Z)",
            raw,
            re.DOTALL,
        )
        if root_cause_match:
            diagnosis.root_cause = root_cause_match.group(1).strip()

        # Parse SEVERITY
        severity_match = re.search(r"SEVERITY:\s*(\S+)", raw)
        if severity_match:
            diagnosis.severity = severity_match.group(1).strip().lower()

        # Parse CONFIDENCE
        confidence_match = re.search(r"CONFIDENCE:\s*([\d.]+)", raw)
        if confidence_match:
            try:
                diagnosis.confidence = float(confidence_match.group(1))
            except ValueError:
                diagnosis.confidence = 0.0

        # Parse REASONING
        reasoning_match = re.search(
            r"REASONING:\s*(.+?)(?=\n(?:RECOMMENDED_ACTIONS):|\Z)",
            raw,
            re.DOTALL,
        )
        if reasoning_match:
            diagnosis.reasoning = reasoning_match.group(1).strip()

        # Parse RECOMMENDED_ACTIONS
        actions_match = re.search(r"RECOMMENDED_ACTIONS:\s*(.+)", raw, re.DOTALL)
        if actions_match:
            actions_text = actions_match.group(1).strip()
            diagnosis.recommended_actions = self._parse_actions(actions_text)

        if not diagnosis.root_cause:
            logger.warning("Could not parse ROOT_CAUSE from LLM response")
            diagnosis.root_cause = raw[:500] if raw else "Unable to determine root cause"

        return diagnosis

    def _parse_actions(self, text: str) -> list[RecommendedAction]:
        """Parse the recommended actions section.

        Expected format per action line:
          - ACTION_ID: <id>, TYPE: <type>, REASON: <reason>, PRIORITY: <n>
        Falls back to simpler bullet-point parsing.
        """
        actions: list[RecommendedAction] = []
        priority_counter = 1

        for line in text.strip().splitlines():
            line = line.strip().lstrip("-").strip()
            if not line:
                continue

            action = RecommendedAction(priority=priority_counter)

            # Try structured format
            id_match = re.search(r"ACTION_ID:\s*(\S+)", line)
            type_match = re.search(r"TYPE:\s*(\S+)", line)
            reason_match = re.search(r"REASON:\s*(.+?)(?:,\s*PRIORITY:|\Z)", line)
            prio_match = re.search(r"PRIORITY:\s*(\d+)", line)

            if id_match:
                action.action_id = id_match.group(1).strip(",")
            if type_match:
                action.action_type = type_match.group(1).strip(",")
            if reason_match:
                action.reason = reason_match.group(1).strip()
            if prio_match:
                action.priority = int(prio_match.group(1))

            # Fallback: use the entire line as the reason
            if not action.action_id and not action.action_type:
                action.reason = line
                action.action_type = self._infer_action_type(line)

            actions.append(action)
            priority_counter += 1

        return actions

    @staticmethod
    def _infer_action_type(text: str) -> str:
        """Attempt to infer action type from free-text description."""
        text_lower = text.lower()
        if "circuit" in text_lower or "breaker" in text_lower:
            return "circuit_breaker"
        if "cache" in text_lower and "flush" in text_lower:
            return "cache_flush"
        if "rollback" in text_lower:
            return "rollback"
        if "restart" in text_lower:
            return "restart"
        if "scale" in text_lower:
            return "scale"
        return "unknown"
