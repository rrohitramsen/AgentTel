"""Feedback engine data models — triggers, risk levels, and events."""

from __future__ import annotations

from enum import Enum
from typing import Optional
from pydantic import BaseModel


class FeedbackTrigger(str, Enum):
    """Types of feedback events detected by the engine."""
    MISSING_BASELINE = "missing_baseline"
    STALE_BASELINE = "stale_baseline"
    UNCOVERED_ENDPOINT = "uncovered_endpoint"
    UNCOVERED_ROUTE = "uncovered_route"
    MISSING_RUNBOOK = "missing_runbook"
    SLO_BURN_RATE_HIGH = "slo_burn_rate_high"
    UNMONITORED_SERVICE = "unmonitored_service"


class RiskLevel(str, Enum):
    """Risk classification for auto-apply decisions."""
    LOW = "low"       # Auto-apply: baseline updates
    MEDIUM = "medium"  # Suggest + require approval: config changes
    HIGH = "high"      # Never auto-apply: code changes


class FeedbackEvent(BaseModel):
    """A single feedback event — an improvement opportunity detected by the engine."""
    trigger: FeedbackTrigger
    risk_level: RiskLevel
    target: str
    current_value: Optional[str] = None
    suggested_value: Optional[str] = None
    reasoning: str
    auto_applicable: bool = False
