"""Config generator — produces agenttel.yml from detected endpoints and dependencies."""

from __future__ import annotations

from typing import Any

import yaml

from agenttel_cli.analyze.dependency_detector import DetectedDependency
from agenttel_cli.analyze.endpoint_detector import DetectedEndpoint

# HTTP methods considered as write operations.
_WRITE_METHODS = frozenset({"POST", "PUT", "DELETE", "PATCH"})


class ConfigGenerator:
    """Generates an agenttel.yml configuration from analysis results.

    Parameters
    ----------
    service_name:
        Logical name of the service (typically the directory name).
    """

    def __init__(self, service_name: str) -> None:
        self._service_name = service_name

    def generate(
        self,
        endpoints: list[DetectedEndpoint],
        dependencies: list[DetectedDependency],
    ) -> str:
        """Build the configuration dict and serialise it to a YAML string.

        Parameters
        ----------
        endpoints:
            Endpoints detected from the source code.
        dependencies:
            Dependencies detected from the source code.

        Returns
        -------
        str
            YAML-formatted configuration string.
        """
        config = self._build_config(endpoints, dependencies)
        return yaml.dump(config, default_flow_style=False, sort_keys=False, allow_unicode=True)

    def _build_config(
        self,
        endpoints: list[DetectedEndpoint],
        dependencies: list[DetectedDependency],
    ) -> dict[str, Any]:
        operations = [self._endpoint_to_operation(ep) for ep in endpoints]
        dep_entries = [self._dependency_to_entry(dep) for dep in dependencies]

        return {
            "service": {
                "name": self._service_name,
                "version": "1.0.0",
            },
            "operations": operations,
            "dependencies": dep_entries,
        }

    @staticmethod
    def _endpoint_to_operation(endpoint: DetectedEndpoint) -> dict[str, Any]:
        is_write = endpoint.method in _WRITE_METHODS
        profile = "standard-write" if is_write else "read-only"

        operation_name = f"{endpoint.method} {endpoint.path}"

        return {
            "name": operation_name,
            "profile": profile,
            "baseline": {
                "p50": "TODO",
                "p99": "TODO",
            },
            "decision_context": {
                "class": endpoint.class_name,
                "method": endpoint.method_name,
                "http_method": endpoint.method,
                "path": endpoint.path,
            },
        }

    @staticmethod
    def _dependency_to_entry(dependency: DetectedDependency) -> dict[str, Any]:
        return {
            "type": dependency.type,
            "name": dependency.name,
            "health_check_url": "TODO",
        }
