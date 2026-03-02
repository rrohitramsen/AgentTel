"""MCP tool: analyze_codebase — scan source code to detect framework, endpoints, dependencies."""

from __future__ import annotations

import json
from pathlib import Path

from agenttel_cli.analyze.scanner import SourceScanner
from agenttel_cli.analyze.endpoint_detector import EndpointDetector
from agenttel_cli.analyze.dependency_detector import DependencyDetector

from ..mcp.models import ToolDefinition

TOOL_DEFINITION = ToolDefinition(
    name="analyze_codebase",
    description=(
        "Scan a Java/Spring Boot source directory to detect framework, "
        "REST endpoints, and infrastructure dependencies. Returns a structured "
        "analysis that can be used to generate AgentTel configuration."
    ),
    inputSchema={
        "type": "object",
        "properties": {
            "source_dir": {
                "type": "string",
                "description": "Path to the source directory to analyze",
            },
        },
        "required": ["source_dir"],
    },
)


async def handle(arguments: dict[str, str]) -> str:
    source_dir = arguments.get("source_dir", ".")
    root = Path(source_dir)

    if not root.is_dir():
        return f"Error: '{source_dir}' is not a valid directory."

    scanner = SourceScanner()
    source_files = scanner.scan(root)

    if not source_files:
        return f"No Java source files found in '{source_dir}'."

    endpoint_detector = EndpointDetector()
    endpoints = endpoint_detector.detect(source_files)

    dependency_detector = DependencyDetector()
    dependencies = dependency_detector.detect(source_files)

    # Detect framework
    framework = "Unknown"
    for sf in source_files:
        if "@SpringBootApplication" in sf.content:
            framework = "Spring Boot"
            break

    result = {
        "source_dir": str(root.resolve()),
        "framework": framework,
        "files_scanned": len(source_files),
        "endpoints": [
            {
                "method": ep.method,
                "path": ep.path,
                "class": ep.class_name,
                "handler": ep.method_name,
            }
            for ep in endpoints
        ],
        "dependencies": [
            {
                "type": dep.type,
                "name": dep.name,
                "evidence": dep.evidence,
            }
            for dep in dependencies
        ],
        "summary": (
            f"Analyzed {len(source_files)} Java files. "
            f"Found {len(endpoints)} REST endpoints and "
            f"{len(dependencies)} dependencies. "
            f"Framework: {framework}."
        ),
    }

    return json.dumps(result, indent=2)
