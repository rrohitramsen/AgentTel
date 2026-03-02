"""MCP tool: apply_improvements — detect AND apply low-risk instrumentation improvements."""

from __future__ import annotations

import json
import logging
import re
from pathlib import Path
from typing import Any

import httpx
import yaml

from ..feedback.applier import FeedbackApplier
from ..feedback.engine import FeedbackEngine
from ..feedback.models import RiskLevel
from ..mcp.models import ToolDefinition

logger = logging.getLogger(__name__)

TOOL_DEFINITION = ToolDefinition(
    name="apply_improvements",
    description=(
        "Detect AND apply instrumentation improvements. Fetches live health data "
        "from the backend MCP server, compares against agenttel.yml config, and "
        "auto-applies low-risk changes (baseline calibration). Returns what was "
        "applied and what needs human review."
    ),
    inputSchema={
        "type": "object",
        "properties": {
            "config_path": {
                "type": "string",
                "description": "Path to the agenttel.yml configuration file",
            },
            "health_endpoint": {
                "type": "string",
                "description": "Backend MCP URL (default: http://payment-service:8081)",
            },
            "dry_run": {
                "type": "string",
                "description": "If 'true', detect improvements without applying. Default: 'false'.",
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


def _parse_health_text(raw: str) -> dict[str, Any]:
    """Parse the text-format health response into a dict for FeedbackEngine."""
    result: dict[str, Any] = {"operations": [], "slo_statuses": []}

    for line in raw.split("\n"):
        # "  POST /api/payments: err=14.8% p50=57ms p99=149ms"
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

        # "  payment-availability: budget=0.0% burn=333.3x"
        m = re.match(
            r"^\s{2}(\S+):\s+budget=([0-9.]+)%\s+burn=([0-9.]+)x",
            line,
        )
        if m:
            result["slo_statuses"].append({
                "name": m.group(1),
                "budget_remaining": float(m.group(2)) / 100,
                "burn_rate": float(m.group(3)),
            })

    return result


async def _fetch_health(endpoint: str) -> dict[str, Any] | None:
    """Fetch health data from backend MCP server."""
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

            # Extract text content from MCP response
            result = data.get("result", {})
            content = result.get("content", [])
            if content:
                text = content[0].get("text", "")
                return _parse_health_text(text)
    except Exception as exc:
        logger.warning("Failed to fetch health data from %s: %s", endpoint, exc)

    return None


async def handle(arguments: dict[str, str]) -> str:
    import os

    config_path = Path(arguments.get("config_path", ""))
    dry_run = arguments.get("dry_run", "false").lower() == "true"
    health_endpoint = arguments.get(
        "health_endpoint",
        f"http://{os.environ.get('MCP_HOST', 'localhost')}:{os.environ.get('MCP_PORT', '8081')}",
    )

    if not config_path.exists():
        return json.dumps({"error": f"Config not found: {config_path}"})

    config = _load_config(config_path)
    if config is None:
        return json.dumps({"error": f"Failed to parse: {config_path}"})

    engine = FeedbackEngine()
    applier = FeedbackApplier()

    # Phase 1: Detect from config
    config_events = engine.detect_from_config(config)

    # Phase 2: Detect from live health data
    health_events = []
    health_data = await _fetch_health(health_endpoint)
    if health_data:
        health_events = engine.detect_from_health(config, health_data)
        # Also calibrate missing baselines from observed data
        for op_health in health_data.get("operations", []):
            op_name = op_health.get("operation", "")
            observed_p50 = op_health.get("latency_p50_ms")
            observed_p99 = op_health.get("latency_p99_ms")
            # Find matching config operation with missing baselines
            for op_cfg in config.get("operations", []):
                if op_cfg.get("name") == op_name:
                    baseline = op_cfg.get("baseline", {})
                    if baseline.get("p50") in (None, "TODO") and observed_p50:
                        health_events.append(
                            _calibration_event(op_name, "p50", observed_p50)
                        )
                    if baseline.get("p99") in (None, "TODO") and observed_p99:
                        health_events.append(
                            _calibration_event(op_name, "p99", observed_p99)
                        )

    all_events = config_events + health_events

    if dry_run:
        # Dry-run: return all findings without applying anything
        findings = [
            {
                "id": i,
                "trigger": e.trigger.value,
                "risk_level": e.risk_level.value,
                "target": e.target,
                "current_value": e.current_value,
                "suggested_value": e.suggested_value,
                "reasoning": e.reasoning,
                "auto_applicable": e.auto_applicable,
            }
            for i, e in enumerate(all_events)
        ]
        result = {
            "config_path": str(config_path),
            "dry_run": True,
            "findings": findings,
            "total_findings": len(findings),
            "by_risk": {
                "low": len([e for e in all_events if e.risk_level == RiskLevel.LOW]),
                "medium": len([e for e in all_events if e.risk_level == RiskLevel.MEDIUM]),
                "high": len([e for e in all_events if e.risk_level == RiskLevel.HIGH]),
            },
            "health_data_available": health_data is not None,
        }
        logger.info("Dry-run: found %d improvements", len(findings))
        return json.dumps(result, indent=2)

    # Phase 3: Apply eligible improvements
    applied = applier.apply(config_path, all_events)

    # Phase 4: Categorize remaining events
    pending = [
        {
            "trigger": e.trigger.value,
            "risk_level": e.risk_level.value,
            "target": e.target,
            "reasoning": e.reasoning,
        }
        for e in all_events
        if not (e.risk_level == RiskLevel.LOW and e.auto_applicable)
    ]

    result = {
        "config_path": str(config_path),
        "applied": applied,
        "applied_count": len(applied),
        "pending": pending,
        "pending_count": len(pending),
        "health_data_available": health_data is not None,
        "total_detections": len(all_events),
    }

    if applied:
        logger.info("Applied %d improvements to %s", len(applied), config_path)
    if pending:
        logger.info("%d improvements need human review", len(pending))

    return json.dumps(result, indent=2)


def _calibration_event(op_name: str, metric: str, observed: float):
    """Create a feedback event for baseline calibration from observed data."""
    from ..feedback.models import FeedbackEvent, FeedbackTrigger, RiskLevel

    return FeedbackEvent(
        trigger=FeedbackTrigger.STALE_BASELINE,
        risk_level=RiskLevel.LOW,
        target=op_name,
        current_value="TODO",
        suggested_value=f"{metric}={observed:.0f}ms",
        reasoning=(
            f"Calibrating {op_name} {metric} baseline to observed {observed:.0f}ms "
            f"from live traffic."
        ),
        auto_applicable=True,
    )
