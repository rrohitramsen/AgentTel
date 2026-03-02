"""Config validator — checks agenttel.yml for completeness and correctness."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


@dataclass(frozen=True)
class ValidationIssue:
    """A single validation finding."""

    severity: str  # "error", "warning", or "info"
    message: str
    path: str  # Dot-delimited path into the config (e.g. "operations[0].baseline.p50").


class ConfigValidator:
    """Loads an agenttel.yml and validates it for completeness."""

    def validate(self, config_path: Path) -> list[ValidationIssue]:
        """Validate the configuration file at *config_path*.

        Parameters
        ----------
        config_path:
            Path to the agenttel.yml file.

        Returns
        -------
        list[ValidationIssue]
            All issues found during validation.
        """
        try:
            raw = config_path.read_text(encoding="utf-8")
            config = yaml.safe_load(raw)
        except yaml.YAMLError as exc:
            return [
                ValidationIssue(
                    severity="error",
                    message=f"Failed to parse YAML: {exc}",
                    path="(root)",
                )
            ]

        if not isinstance(config, dict):
            return [
                ValidationIssue(
                    severity="error",
                    message="Configuration root must be a mapping.",
                    path="(root)",
                )
            ]

        issues: list[ValidationIssue] = []
        issues.extend(self._validate_service(config))
        issues.extend(self._validate_operations(config))
        issues.extend(self._validate_dependencies(config))
        return issues

    # ------------------------------------------------------------------
    # Section validators
    # ------------------------------------------------------------------

    @staticmethod
    def _validate_service(config: dict[str, Any]) -> list[ValidationIssue]:
        issues: list[ValidationIssue] = []
        service = config.get("service")
        if not service:
            issues.append(
                ValidationIssue(severity="error", message="Missing 'service' section.", path="service")
            )
        elif not service.get("name"):
            issues.append(
                ValidationIssue(
                    severity="error", message="Service name is required.", path="service.name"
                )
            )
        return issues

    @staticmethod
    def _validate_operations(config: dict[str, Any]) -> list[ValidationIssue]:
        issues: list[ValidationIssue] = []
        operations = config.get("operations")

        if not operations:
            issues.append(
                ValidationIssue(
                    severity="warning",
                    message="No operations defined.",
                    path="operations",
                )
            )
            return issues

        for idx, op in enumerate(operations):
            prefix = f"operations[{idx}]"
            name = op.get("name", f"<unnamed-{idx}>")

            # Baselines.
            baseline = op.get("baseline", {})
            if not baseline or baseline.get("p50") in (None, "TODO"):
                issues.append(
                    ValidationIssue(
                        severity="warning",
                        message=f"Operation '{name}' is missing a p50 baseline.",
                        path=f"{prefix}.baseline.p50",
                    )
                )
            if not baseline or baseline.get("p99") in (None, "TODO"):
                issues.append(
                    ValidationIssue(
                        severity="warning",
                        message=f"Operation '{name}' is missing a p99 baseline.",
                        path=f"{prefix}.baseline.p99",
                    )
                )

            # Escalation level.
            if not op.get("escalation_level"):
                issues.append(
                    ValidationIssue(
                        severity="warning",
                        message=f"Operation '{name}' is missing an escalation level.",
                        path=f"{prefix}.escalation_level",
                    )
                )

            # Runbook URL.
            if not op.get("runbook_url"):
                issues.append(
                    ValidationIssue(
                        severity="info",
                        message=f"Operation '{name}' has no runbook URL.",
                        path=f"{prefix}.runbook_url",
                    )
                )

        return issues

    @staticmethod
    def _validate_dependencies(config: dict[str, Any]) -> list[ValidationIssue]:
        issues: list[ValidationIssue] = []
        dependencies = config.get("dependencies")

        if not dependencies:
            return issues  # Dependencies are optional.

        for idx, dep in enumerate(dependencies):
            prefix = f"dependencies[{idx}]"
            name = dep.get("name", f"<unnamed-{idx}>")

            health_url = dep.get("health_check_url")
            if not health_url or health_url == "TODO":
                issues.append(
                    ValidationIssue(
                        severity="warning",
                        message=f"Dependency '{name}' has no health check URL.",
                        path=f"{prefix}.health_check_url",
                    )
                )

        return issues

    # ------------------------------------------------------------------
    # Utility
    # ------------------------------------------------------------------

    @staticmethod
    def to_validation_issue(severity: str, message: str, path: str) -> ValidationIssue:
        """Create a :class:`ValidationIssue` — convenience helper for external callers."""
        return ValidationIssue(severity=severity, message=message, path=path)
