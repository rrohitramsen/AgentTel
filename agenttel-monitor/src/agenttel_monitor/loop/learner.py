from __future__ import annotations

import json
import logging
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from ..config.types import LearningConfig
from .actor import ActResult
from .investigator import InvestigationContext
from .reasoner import Diagnosis
from .verifier import VerificationResult

logger = logging.getLogger(__name__)


@dataclass
class IncidentRecord:
    """A single incident record persisted for future learning."""
    timestamp: str = ""
    root_cause: str = ""
    severity: str = ""
    confidence: float = 0.0
    reasoning: str = ""
    actions_taken: list[dict[str, Any]] = field(default_factory=list)
    actions_escalated: list[dict[str, Any]] = field(default_factory=list)
    recovered: bool = False
    verification_status: str = ""
    anomalous_operations: list[str] = field(default_factory=list)
    context_summary: str = ""


class Learner:
    """Records incident outcomes to a JSON file for future reference and learning."""

    def __init__(self, config: LearningConfig) -> None:
        self._config = config
        self._history_path = Path(config.history_file)

    async def record(
        self,
        context: InvestigationContext,
        diagnosis: Diagnosis,
        act_result: ActResult,
        verification: VerificationResult,
    ) -> None:
        """Record an incident and its outcome."""
        record = IncidentRecord(
            timestamp=datetime.now(timezone.utc).isoformat(),
            root_cause=diagnosis.root_cause,
            severity=diagnosis.severity,
            confidence=diagnosis.confidence,
            reasoning=diagnosis.reasoning,
            recovered=verification.recovered,
            verification_status=verification.post_status,
            anomalous_operations=[
                op.operation for op in context.watch_result.anomalous_operations
            ],
            context_summary=context.executive_summary.summary,
        )

        # Summarize executed actions
        for outcome in act_result.outcomes:
            action_info = {
                "action_id": outcome.action.action_id,
                "action_type": outcome.action.action_type,
                "reason": outcome.action.reason,
            }
            if outcome.executed:
                action_info["success"] = (
                    outcome.result.success if outcome.result else False
                )
                action_info["message"] = (
                    outcome.result.message if outcome.result else ""
                )
                record.actions_taken.append(action_info)
            elif outcome.escalated:
                record.actions_escalated.append(action_info)

        self._persist(record)

        logger.info(
            "Incident recorded: root_cause='%s', recovered=%s",
            diagnosis.root_cause[:80],
            verification.recovered,
        )

    def _persist(self, record: IncidentRecord) -> None:
        """Append the record to the JSON history file (synchronous I/O)."""
        history: list[dict[str, Any]] = []

        if self._history_path.exists():
            try:
                raw = self._history_path.read_text(encoding="utf-8")
                history = json.loads(raw) if raw.strip() else []
            except (json.JSONDecodeError, OSError) as exc:
                logger.warning("Could not read history file, starting fresh: %s", exc)
                history = []

        history.append(asdict(record))

        # Enforce max entries (keep most recent)
        if len(history) > self._config.max_entries:
            history = history[-self._config.max_entries :]

        # Ensure parent directory exists
        self._history_path.parent.mkdir(parents=True, exist_ok=True)

        self._history_path.write_text(
            json.dumps(history, indent=2, ensure_ascii=False),
            encoding="utf-8",
        )

    def get_recent_incidents(self, count: int = 5) -> list[IncidentRecord]:
        """Load the most recent N incidents from history."""
        if not self._history_path.exists():
            return []

        try:
            raw = self._history_path.read_text(encoding="utf-8")
            history = json.loads(raw) if raw.strip() else []
        except (json.JSONDecodeError, OSError):
            return []

        records: list[IncidentRecord] = []
        for entry in history[-count:]:
            try:
                records.append(IncidentRecord(**entry))
            except Exception:
                continue

        return records
