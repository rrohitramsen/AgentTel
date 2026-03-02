"""MCP tool: validate_instrumentation — check config completeness and detect gaps."""

from __future__ import annotations

import json
from pathlib import Path

from agenttel_cli.validate.validator import ConfigValidator
from agenttel_cli.validate.gap_detector import GapDetector
from agenttel_cli.analyze.scanner import SourceScanner
from agenttel_cli.analyze.endpoint_detector import EndpointDetector

from ..mcp.models import ToolDefinition

TOOL_DEFINITION = ToolDefinition(
    name="validate_instrumentation",
    description=(
        "Validate an agenttel.yml configuration for completeness. "
        "Optionally compares against source code to detect unconfigured "
        "endpoints and stale config entries."
    ),
    inputSchema={
        "type": "object",
        "properties": {
            "config_path": {
                "type": "string",
                "description": "Path to the agenttel.yml configuration file",
            },
            "source_dir": {
                "type": "string",
                "description": "Optional: source directory to compare against for gap detection",
            },
        },
        "required": ["config_path"],
    },
)


async def handle(arguments: dict[str, str]) -> str:
    config_path = Path(arguments.get("config_path", ""))
    source_dir = arguments.get("source_dir")

    if not config_path.exists():
        return f"Error: Configuration file not found: {config_path}"

    # Run config validation
    validator = ConfigValidator()
    issues = validator.validate(config_path)

    # Run gap detection if source directory provided
    gaps = []
    if source_dir:
        source_root = Path(source_dir)
        if source_root.is_dir():
            scanner = SourceScanner()
            source_files = scanner.scan(source_root)
            endpoints = EndpointDetector().detect(source_files)
            gap_detector = GapDetector()
            detected_gaps = gap_detector.detect(config_path, endpoints)
            gaps = [
                {"type": g.type, "details": g.details}
                for g in detected_gaps
            ]

    # Categorize issues
    errors = [i for i in issues if i.severity == "error"]
    warnings = [i for i in issues if i.severity == "warning"]
    infos = [i for i in issues if i.severity == "info"]

    result = {
        "config_path": str(config_path),
        "valid": len(errors) == 0,
        "summary": {
            "errors": len(errors),
            "warnings": len(warnings),
            "info": len(infos),
            "gaps": len(gaps),
        },
        "issues": [
            {
                "severity": i.severity,
                "message": i.message,
                "path": i.path,
            }
            for i in issues
        ],
        "gaps": gaps,
    }

    return json.dumps(result, indent=2)
