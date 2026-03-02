"""AgentTel CLI — analyze Java codebases and generate AgentTel configuration."""

from __future__ import annotations

import sys
from pathlib import Path

import click

from agenttel_cli.analyze.config_generator import ConfigGenerator
from agenttel_cli.analyze.dependency_detector import DependencyDetector
from agenttel_cli.analyze.endpoint_detector import EndpointDetector
from agenttel_cli.analyze.scanner import SourceScanner
from agenttel_cli.baseline.mcp_baseline_client import McpBaselineClient
from agenttel_cli.validate.gap_detector import GapDetector
from agenttel_cli.validate.validator import ConfigValidator


@click.group()
@click.version_option(version="0.1.0", prog_name="agenttel")
def cli() -> None:
    """AgentTel CLI — analyze, validate, and configure observability for Java services."""


@cli.command()
@click.argument("source_dir", type=click.Path(exists=True, file_okay=False, resolve_path=True))
@click.option(
    "--output",
    "-o",
    default="agenttel.yml",
    type=click.Path(resolve_path=True),
    help="Output path for the generated configuration file.",
)
def analyze(source_dir: str, output: str) -> None:
    """Scan a Java source tree, detect endpoints and dependencies, and generate agenttel.yml."""
    source_path = Path(source_dir)
    output_path = Path(output)

    click.echo(f"Scanning source tree: {source_path}")

    scanner = SourceScanner()
    source_files = scanner.scan(source_path)

    if not source_files:
        click.echo("No Java source files found.", err=True)
        sys.exit(1)

    click.echo(f"Found {len(source_files)} Java source file(s).")

    endpoint_detector = EndpointDetector()
    endpoints = endpoint_detector.detect(source_files)
    click.echo(f"Detected {len(endpoints)} endpoint(s).")

    dependency_detector = DependencyDetector()
    dependencies = dependency_detector.detect(source_files)
    click.echo(f"Detected {len(dependencies)} dependenc{'y' if len(dependencies) == 1 else 'ies'}.")

    generator = ConfigGenerator(service_name=source_path.name)
    config_yaml = generator.generate(endpoints=endpoints, dependencies=dependencies)

    output_path.write_text(config_yaml, encoding="utf-8")
    click.echo(f"Configuration written to {output_path}")


@cli.command()
@click.option(
    "--config",
    "-c",
    default="agenttel.yml",
    type=click.Path(exists=True, resolve_path=True),
    help="Path to the agenttel.yml configuration file.",
)
@click.option(
    "--source",
    "-s",
    default=None,
    type=click.Path(exists=True, file_okay=False, resolve_path=True),
    help="Java source directory to cross-reference against the config.",
)
def validate(config: str, source: str | None) -> None:
    """Check an existing agenttel.yml for gaps and misconfigurations."""
    config_path = Path(config)

    click.echo(f"Validating configuration: {config_path}")

    validator = ConfigValidator()
    issues = validator.validate(config_path)

    if source is not None:
        source_path = Path(source)
        click.echo(f"Cross-referencing against source: {source_path}")

        scanner = SourceScanner()
        source_files = scanner.scan(source_path)

        endpoint_detector = EndpointDetector()
        endpoints = endpoint_detector.detect(source_files)

        gap_detector = GapDetector()
        gaps = gap_detector.detect(config_path=config_path, detected_endpoints=endpoints)

        for gap in gaps:
            severity = "warning" if gap.type == "unconfigured_endpoint" else "info"
            issues.append(
                ConfigValidator.to_validation_issue(
                    severity=severity,
                    message=gap.details,
                    path=f"gaps.{gap.type}",
                )
            )

    if not issues:
        click.echo("No issues found. Configuration looks good.")
        return

    error_count = sum(1 for i in issues if i.severity == "error")
    warning_count = sum(1 for i in issues if i.severity == "warning")
    info_count = sum(1 for i in issues if i.severity == "info")

    for issue in issues:
        prefix = {"error": "ERROR", "warning": "WARN ", "info": "INFO "}[issue.severity]
        click.echo(f"  [{prefix}] {issue.message}  (at {issue.path})")

    click.echo(
        f"\nSummary: {error_count} error(s), {warning_count} warning(s), {info_count} info(s)."
    )

    if error_count > 0:
        sys.exit(1)


@cli.command("suggest-baselines")
@click.option(
    "--mcp-url",
    required=True,
    help="URL of the MCP server (e.g. http://localhost:8081).",
)
@click.option(
    "--service",
    "-s",
    default=None,
    help="Service name to query baselines for. Defaults to all services.",
)
@click.option(
    "--config",
    "-c",
    default=None,
    type=click.Path(exists=True, resolve_path=True),
    help="Path to agenttel.yml to update with suggested baselines.",
)
def suggest_baselines(mcp_url: str, service: str | None, config: str | None) -> None:
    """Query the MCP server for observed baselines and suggest values."""
    click.echo(f"Connecting to MCP server at {mcp_url}")

    client = McpBaselineClient(mcp_url=mcp_url)

    try:
        suggestions = client.fetch_baselines(service_name=service)
    except Exception as exc:
        click.echo(f"Failed to fetch baselines: {exc}", err=True)
        sys.exit(1)

    if not suggestions:
        click.echo("No baseline data available from MCP server.")
        return

    click.echo(f"Received baseline suggestions for {len(suggestions)} operation(s):\n")
    for suggestion in suggestions:
        click.echo(f"  {suggestion.operation}:")
        click.echo(f"    p50: {suggestion.p50_ms}ms")
        click.echo(f"    p99: {suggestion.p99_ms}ms")

    if config is not None:
        config_path = Path(config)
        updated_yaml = client.apply_baselines_to_config(
            config_path=config_path, suggestions=suggestions
        )
        config_path.write_text(updated_yaml, encoding="utf-8")
        click.echo(f"\nBaselines applied to {config_path}")


def main() -> None:
    """Entry point for the CLI."""
    cli()


if __name__ == "__main__":
    main()
