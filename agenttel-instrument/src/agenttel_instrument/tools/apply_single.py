"""MCP tool: apply_single_improvement — apply one user-approved improvement."""

from __future__ import annotations

import json
import logging
import os
import re
from pathlib import Path
from typing import Any

import httpx
import yaml

from ..feedback.applier import FeedbackApplier
from ..feedback.engine import FeedbackEngine
from ..feedback.models import FeedbackEvent, FeedbackTrigger, RiskLevel
from ..mcp.models import ToolDefinition

logger = logging.getLogger(__name__)

TOOL_DEFINITION = ToolDefinition(
    name="apply_single_improvement",
    description=(
        "Apply a single user-approved improvement to the agenttel.yml config. "
        "Identifies the finding by trigger type and target name."
    ),
    inputSchema={
        "type": "object",
        "properties": {
            "config_path": {
                "type": "string",
                "description": "Path to the agenttel.yml configuration file",
            },
            "trigger": {
                "type": "string",
                "description": "The trigger type (e.g. missing_baseline, stale_baseline, missing_runbook)",
            },
            "target": {
                "type": "string",
                "description": "The target operation or entity name",
            },
            "suggested_value": {
                "type": "string",
                "description": "The suggested value from the dry-run finding (optional, for disambiguation)",
            },
            "health_endpoint": {
                "type": "string",
                "description": "Backend MCP URL (default: http://payment-service:8081)",
            },
        },
        "required": ["config_path", "trigger", "target"],
    },
)


def _load_config(config_path: Path) -> dict[str, Any] | None:
    try:
        raw = config_path.read_text(encoding="utf-8")
        config = yaml.safe_load(raw)
        return config if isinstance(config, dict) else None
    except (yaml.YAMLError, OSError):
        return None


def _parse_health_text(raw: str) -> dict[str, Any]:
    """Parse the text-format health response into a dict for FeedbackEngine."""
    result: dict[str, Any] = {"operations": [], "slo_statuses": []}
    for line in raw.split("\n"):
        m = re.match(
            r"^\s{2}(\S+(?:\s+\S+)*?):\s+err=([0-9.]+)%\s+p50=(\d+)ms\s+p99=(\d+)ms",
            line,
        )
        if m:
            result["operations"].append({
                "operation": m.group(1),
                "error_rate": float(m.group(2)) / 100,
                "latency_p50_ms": float(m.group(3)),
                "latency_p99_ms": float(m.group(4)),
            })
            continue
        m = re.match(r"^\s{2}(\S+):\s+budget=([0-9.]+)%\s+burn=([0-9.]+)x", line)
        if m:
            result["slo_statuses"].append({
                "name": m.group(1),
                "budget_remaining": float(m.group(2)) / 100,
                "burn_rate": float(m.group(3)),
            })
    return result


async def _fetch_health(endpoint: str) -> dict[str, Any] | None:
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            payload = {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "params": {"name": "get_service_health", "arguments": {}},
                "id": 1,
            }
            resp = await client.post(f"{endpoint}/mcp", json=payload)
            resp.raise_for_status()
            data = resp.json()
            content = data.get("result", {}).get("content", [])
            if content:
                return _parse_health_text(content[0].get("text", ""))
    except Exception as exc:
        logger.warning("Failed to fetch health data from %s: %s", endpoint, exc)
    return None


def _calibration_event(op_name: str, metric: str, observed: float) -> FeedbackEvent:
    return FeedbackEvent(
        trigger=FeedbackTrigger.STALE_BASELINE,
        risk_level=RiskLevel.LOW,
        target=op_name,
        current_value="TODO",
        suggested_value=f"{metric}={observed:.0f}ms",
        reasoning=f"Calibrating {op_name} {metric} baseline to observed {observed:.0f}ms from live traffic.",
        auto_applicable=True,
    )


def _find_matching_event(
    events: list[FeedbackEvent],
    trigger: str,
    target: str,
    suggested_value: str | None,
) -> FeedbackEvent | None:
    """Find an event matching the given trigger+target, with optional suggested_value disambiguation."""
    candidates = [
        e for e in events
        if e.trigger.value == trigger and e.target == target
    ]
    if not candidates:
        return None
    if len(candidates) == 1:
        return candidates[0]
    # Multiple matches (e.g. two stale_baseline for same target, different metrics)
    if suggested_value:
        for c in candidates:
            if c.suggested_value == suggested_value:
                return c
    return candidates[0]


async def handle(arguments: dict[str, str]) -> str:
    config_path = Path(arguments.get("config_path", ""))
    trigger = arguments.get("trigger", "")
    target = arguments.get("target", "")
    suggested_value = arguments.get("suggested_value")
    health_endpoint = arguments.get(
        "health_endpoint",
        f"http://{os.environ.get('MCP_HOST', 'localhost')}:{os.environ.get('MCP_PORT', '8081')}",
    )

    if not trigger or not target:
        return json.dumps({"applied": False, "reason": "trigger and target are required"})

    if not config_path.exists():
        return json.dumps({"applied": False, "reason": f"Config not found: {config_path}"})

    config = _load_config(config_path)
    if config is None:
        return json.dumps({"applied": False, "reason": f"Failed to parse: {config_path}"})

    # Re-run detection pipeline
    engine = FeedbackEngine()
    config_events = engine.detect_from_config(config)

    health_events: list[FeedbackEvent] = []
    health_data = await _fetch_health(health_endpoint)
    if health_data:
        health_events = engine.detect_from_health(config, health_data)
        for op_health in health_data.get("operations", []):
            op_name = op_health.get("operation", "")
            observed_p50 = op_health.get("latency_p50_ms")
            observed_p99 = op_health.get("latency_p99_ms")
            for op_cfg in config.get("operations", []):
                if op_cfg.get("name") == op_name:
                    baseline = op_cfg.get("baseline", {})
                    if baseline.get("p50") in (None, "TODO") and observed_p50:
                        health_events.append(_calibration_event(op_name, "p50", observed_p50))
                    if baseline.get("p99") in (None, "TODO") and observed_p99:
                        health_events.append(_calibration_event(op_name, "p99", observed_p99))

    all_events = config_events + health_events

    event = _find_matching_event(all_events, trigger, target, suggested_value)
    if event is None:
        return json.dumps({
            "applied": False,
            "reason": f"No matching finding for {trigger} on {target} (may have already been applied)",
        })

    # Enrich missing_baseline events with observed health data
    if (
        event.trigger == FeedbackTrigger.MISSING_BASELINE
        and event.suggested_value is None
        and health_data
    ):
        for op_health in health_data.get("operations", []):
            if op_health.get("operation") == event.target:
                config_reload = _load_config(config_path) or {}
                for op_cfg in config_reload.get("operations", []):
                    if op_cfg.get("name") == event.target:
                        baseline = op_cfg.get("baseline", {})
                        parts = []
                        if baseline.get("p50") in (None, "TODO") and op_health.get("latency_p50_ms"):
                            parts.append(f"p50={op_health['latency_p50_ms']:.0f}ms")
                        if baseline.get("p99") in (None, "TODO") and op_health.get("latency_p99_ms"):
                            parts.append(f"p99={op_health['latency_p99_ms']:.0f}ms")
                        if parts:
                            event = FeedbackEvent(
                                trigger=event.trigger,
                                risk_level=event.risk_level,
                                target=event.target,
                                current_value=event.current_value,
                                suggested_value=parts[0],
                                reasoning=f"Calibrating {event.target} baseline to observed {parts[0]} from live traffic.",
                                auto_applicable=True,
                            )
                        break
                break

    applier = FeedbackApplier()
    description = applier.apply_single(config_path, event)

    if description:
        logger.info("Applied improvement [%s] for %s: %s", trigger, target, description)
        return json.dumps({"applied": True, "description": description})
    else:
        return json.dumps({
            "applied": False,
            "reason": f"Could not apply {trigger} for {target}",
        })
