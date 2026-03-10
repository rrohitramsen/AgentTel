"""Generate agenttel.yml configuration from detected endpoints and dependencies."""

from __future__ import annotations

import yaml

from .endpoint_detector import Endpoint
from .dependency_detector import Dependency


class ConfigGenerator:
    """Generates an agenttel.yml YAML configuration from analysis results."""

    def __init__(self, service_name: str) -> None:
        self.service_name = service_name

    def generate(self, endpoints: list[Endpoint], dependencies: list[Dependency]) -> str:
        """Return a complete agenttel.yml as a YAML string."""
        config: dict = {
            "agenttel": {
                "topology": {
                    "team": "TODO",
                    "tier": "tier-1",
                    "domain": "TODO",
                },
                "operations": {},
                "dependencies": [],
            },
        }

        # Add operations from endpoints
        for ep in endpoints:
            op_name = f"{ep.method} {ep.path}"
            config["agenttel"]["operations"][op_name] = {
                "baseline": {
                    "p50": "TODO",
                    "p99": "TODO",
                },
                "decision": {
                    "retryable": ep.method in ("GET", "HEAD", "OPTIONS"),
                    "idempotent": ep.method in ("GET", "PUT", "DELETE", "HEAD", "OPTIONS"),
                },
            }

        # Add dependencies
        for dep in dependencies:
            config["agenttel"]["dependencies"].append({
                "name": dep.name,
                "type": dep.type,
                "criticality": "required",
                "health_check_url": "TODO",
            })

        return yaml.dump(config, default_flow_style=False, sort_keys=False)
