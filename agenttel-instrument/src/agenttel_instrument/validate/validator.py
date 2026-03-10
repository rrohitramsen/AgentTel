"""Validate agenttel.yml configuration structure and completeness."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import yaml


@dataclass
class ValidationIssue:
    """A single validation finding."""

    severity: str  # "error" | "warning" | "info"
    message: str
    path: str  # YAML path, e.g. "agenttel.operations.GET /api/foo.baseline.p50"


class ConfigValidator:
    """Validates an agenttel.yml file for structural correctness and completeness."""

    def validate(self, config_path: Path) -> list[ValidationIssue]:
        issues: list[ValidationIssue] = []

        try:
            with open(config_path, encoding="utf-8") as f:
                config = yaml.safe_load(f)
        except yaml.YAMLError as exc:
            issues.append(ValidationIssue("error", f"Invalid YAML: {exc}", ""))
            return issues
        except OSError as exc:
            issues.append(ValidationIssue("error", f"Cannot read file: {exc}", ""))
            return issues

        if not isinstance(config, dict):
            issues.append(ValidationIssue("error", "Config must be a YAML mapping", ""))
            return issues

        agenttel = config.get("agenttel")
        if not agenttel:
            issues.append(ValidationIssue("error", "Missing top-level 'agenttel' key", "agenttel"))
            return issues

        self._validate_topology(agenttel, issues)
        self._validate_operations(agenttel, issues)
        self._validate_dependencies(agenttel, issues)

        return issues

    def _validate_topology(self, agenttel: dict, issues: list[ValidationIssue]) -> None:
        topo = agenttel.get("topology")
        if not topo:
            issues.append(ValidationIssue("warning", "No topology section defined", "agenttel.topology"))
            return

        for field in ("team", "tier", "domain"):
            value = topo.get(field)
            if not value or value == "TODO":
                issues.append(ValidationIssue(
                    "warning",
                    f"Topology '{field}' is missing or set to TODO",
                    f"agenttel.topology.{field}",
                ))

    def _validate_operations(self, agenttel: dict, issues: list[ValidationIssue]) -> None:
        ops = agenttel.get("operations")
        if not ops:
            issues.append(ValidationIssue("warning", "No operations defined", "agenttel.operations"))
            return

        for op_name, op_config in ops.items():
            prefix = f"agenttel.operations.{op_name}"
            if not isinstance(op_config, dict):
                issues.append(ValidationIssue("error", f"Operation '{op_name}' must be a mapping", prefix))
                continue

            baseline = op_config.get("baseline", {})
            if not baseline:
                issues.append(ValidationIssue("warning", f"No baseline for '{op_name}'", f"{prefix}.baseline"))
            else:
                for metric in ("p50", "p99"):
                    val = baseline.get(metric)
                    if val is None or val == "TODO":
                        issues.append(ValidationIssue(
                            "info",
                            f"Baseline {metric} not set for '{op_name}'",
                            f"{prefix}.baseline.{metric}",
                        ))

    def _validate_dependencies(self, agenttel: dict, issues: list[ValidationIssue]) -> None:
        deps = agenttel.get("dependencies")
        if not deps:
            return  # Dependencies are optional

        if not isinstance(deps, list):
            issues.append(ValidationIssue("error", "Dependencies must be a list", "agenttel.dependencies"))
            return

        for i, dep in enumerate(deps):
            prefix = f"agenttel.dependencies[{i}]"
            if not isinstance(dep, dict):
                issues.append(ValidationIssue("error", f"Dependency {i} must be a mapping", prefix))
                continue

            if not dep.get("name"):
                issues.append(ValidationIssue("error", f"Dependency {i} missing 'name'", f"{prefix}.name"))

            health_url = dep.get("health_check_url")
            if not health_url or health_url == "TODO":
                issues.append(ValidationIssue(
                    "warning",
                    f"No health_check_url for dependency '{dep.get('name', i)}'",
                    f"{prefix}.health_check_url",
                ))
