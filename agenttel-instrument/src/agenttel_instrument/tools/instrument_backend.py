"""MCP tool: instrument_backend — generate backend instrumentation artifacts."""

from __future__ import annotations

import json
from pathlib import Path

from agenttel_cli.analyze.scanner import SourceScanner
from agenttel_cli.analyze.endpoint_detector import EndpointDetector
from agenttel_cli.analyze.dependency_detector import DependencyDetector
from agenttel_cli.analyze.config_generator import ConfigGenerator

from ..mcp.models import ToolDefinition

TOOL_DEFINITION = ToolDefinition(
    name="instrument_backend",
    description=(
        "Generate backend AgentTel instrumentation for a Java/Spring Boot project. "
        "Returns proposed changes: Gradle/Maven dependency, @AgentObservable annotation, "
        "and agenttel.yml configuration. Does NOT modify files directly."
    ),
    inputSchema={
        "type": "object",
        "properties": {
            "source_dir": {
                "type": "string",
                "description": "Path to the backend source directory",
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

    endpoints = EndpointDetector().detect(source_files)
    dependencies = DependencyDetector().detect(source_files)

    service_name = root.resolve().name
    config_yaml = ConfigGenerator(service_name).generate(endpoints, dependencies)

    # Find the main application class
    main_class = None
    main_class_file = None
    for sf in source_files:
        if "@SpringBootApplication" in sf.content:
            for line in sf.content.split("\n"):
                if "class " in line:
                    parts = line.split("class ")
                    if len(parts) > 1:
                        main_class = parts[1].split()[0].strip("{").strip()
                        main_class_file = str(sf.path)
                    break
            break

    # Build the Gradle dependency snippet
    gradle_dep = 'implementation("io.agenttel:agenttel-spring-boot-starter:0.1.0-alpha")'

    # Build the Maven dependency snippet
    maven_dep = """<dependency>
    <groupId>io.agenttel</groupId>
    <artifactId>agenttel-spring-boot-starter</artifactId>
    <version>0.1.0-alpha</version>
</dependency>"""

    # Build the annotation to add
    annotation = """@AgentObservable(
    team = "your-team",
    tier = "tier-1",
    domain = "your-domain"
)"""

    result = {
        "service_name": service_name,
        "endpoints_found": len(endpoints),
        "dependencies_found": len(dependencies),
        "proposed_changes": {
            "1_add_dependency": {
                "description": "Add AgentTel Spring Boot Starter to your build file",
                "gradle_kts": gradle_dep,
                "maven": maven_dep,
            },
            "2_add_annotation": {
                "description": "Add @AgentObservable to your main application class",
                "target_file": main_class_file,
                "target_class": main_class,
                "annotation": annotation,
                "import": "import io.agenttel.api.annotation.AgentObservable;",
            },
            "3_create_config": {
                "description": "Create agenttel.yml in src/main/resources/",
                "filename": "agenttel.yml",
                "content": config_yaml,
            },
        },
        "instructions": (
            f"To instrument {service_name}:\n"
            f"1. Add the agenttel-spring-boot-starter dependency to your build file\n"
            f"2. Add @AgentObservable annotation to {main_class or 'your main class'}\n"
            f"3. Save the generated agenttel.yml to src/main/resources/\n"
            f"4. Run `agenttel validate` to check the configuration\n"
            f"5. After deploying, run `agenttel suggest-baselines` to fill in baselines"
        ),
    }

    return json.dumps(result, indent=2)
