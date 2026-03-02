"""MCP tool: suggest_improvements — suggest missing baselines, uncovered endpoints, etc."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import yaml

from ..feedback.models import FeedbackEvent, FeedbackTrigger, RiskLevel
from ..mcp.models import ToolDefinition

TOOL_DEFINITION = ToolDefinition(
    name="suggest_improvements",
    description=(
        "Analyze an agenttel.yml configuration and suggest improvements: "
        "missing baselines, uncovered endpoints, stale thresholds, missing runbooks. "
        "Returns structured feedback events with risk levels and auto-apply eligibility."
    ),
    inputSchema={
        "type": "object",
        "properties": {
            "config_path": {
                "type": "string",
                "description": "Path to the agenttel.yml configuration file",
            },
            "service_name": {
                "type": "string",
                "description": "Optional: service name for baseline suggestions from MCP",
            },
        },
        "required": ["config_path"],
    },
)


def _load_config(config_path: Path) -> dict[str, Any] | None:
    try:
        raw = config_path.read_text(encoding="utf-8")
        config = yaml.safe_load(raw)
        return config if isinstance(config, dict) else None
    except (yaml.YAMLError, OSError):
        return None


def _detect_improvements(config: dict[str, Any]) -> list[FeedbackEvent]:
    """Detect improvement opportunities from config alone."""
    events: list[FeedbackEvent] = []

    operations = config.get("operations", [])
    for op in operations:
        name = op.get("name", "unknown")
        baseline = op.get("baseline", {})

        # MISSING_BASELINE
        if baseline.get("p50") in (None, "TODO"):
            events.append(FeedbackEvent(
                trigger=FeedbackTrigger.MISSING_BASELINE,
                risk_level=RiskLevel.LOW,
                target=name,
                current_value="TODO",
                suggested_value="Deploy and run suggest-baselines after 1 hour of traffic",
                reasoning=f"Operation '{name}' has no P50 baseline configured.",
                auto_applicable=False,
            ))

        if baseline.get("p99") in (None, "TODO"):
            events.append(FeedbackEvent(
                trigger=FeedbackTrigger.MISSING_BASELINE,
                risk_level=RiskLevel.LOW,
                target=name,
                current_value="TODO",
                suggested_value="Deploy and run suggest-baselines after 1 hour of traffic",
                reasoning=f"Operation '{name}' has no P99 baseline configured.",
                auto_applicable=False,
            ))

        # MISSING_RUNBOOK
        if not op.get("runbook_url"):
            events.append(FeedbackEvent(
                trigger=FeedbackTrigger.MISSING_RUNBOOK,
                risk_level=RiskLevel.MEDIUM,
                target=name,
                reasoning=f"Operation '{name}' has no runbook URL. Agents cannot look up resolution steps.",
                auto_applicable=False,
            ))

    # Check dependencies
    for dep in config.get("dependencies", []):
        dep_name = dep.get("name", "unknown")
        if dep.get("health_check_url") in (None, "TODO"):
            events.append(FeedbackEvent(
                trigger=FeedbackTrigger.MISSING_RUNBOOK,
                risk_level=RiskLevel.MEDIUM,
                target=f"dependency:{dep_name}",
                reasoning=f"Dependency '{dep_name}' has no health check URL configured.",
                auto_applicable=False,
            ))

    return events


async def handle(arguments: dict[str, str]) -> str:
    config_path = Path(arguments.get("config_path", ""))

    if not config_path.exists():
        return json.dumps({"error": f"Config not found: {config_path}"})

    config = _load_config(config_path)
    if config is None:
        return json.dumps({"error": f"Failed to parse: {config_path}"})

    events = _detect_improvements(config)

    result = {
        "config_path": str(config_path),
        "total_suggestions": len(events),
        "by_risk": {
            "low": len([e for e in events if e.risk_level == RiskLevel.LOW]),
            "medium": len([e for e in events if e.risk_level == RiskLevel.MEDIUM]),
            "high": len([e for e in events if e.risk_level == RiskLevel.HIGH]),
        },
        "suggestions": [e.model_dump() for e in events],
    }

    return json.dumps(result, indent=2)
