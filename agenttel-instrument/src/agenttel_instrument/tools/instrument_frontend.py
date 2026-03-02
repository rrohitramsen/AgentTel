"""MCP tool: instrument_frontend — generate frontend AgentTel Web SDK instrumentation."""

from __future__ import annotations

import json
import re
from pathlib import Path

from ..mcp.models import ToolDefinition

TOOL_DEFINITION = ToolDefinition(
    name="instrument_frontend",
    description=(
        "Generate frontend AgentTel Web SDK instrumentation for a React/TypeScript project. "
        "Scans for React Router routes and generates agenttel.config.ts and SDK initialization code. "
        "Does NOT modify files directly."
    ),
    inputSchema={
        "type": "object",
        "properties": {
            "source_dir": {
                "type": "string",
                "description": "Path to the frontend source directory",
            },
            "backend_url": {
                "type": "string",
                "description": "Backend API URL for OTLP collector proxy (default: /otlp)",
            },
        },
        "required": ["source_dir"],
    },
)

# Patterns to detect React Router routes
_ROUTE_PATTERNS = [
    re.compile(r'<Route\s+path\s*=\s*"([^"]+)"', re.MULTILINE),
    re.compile(r"<Route\s+path\s*=\s*'([^']+)'", re.MULTILINE),
    re.compile(r'path:\s*"([^"]+)"', re.MULTILINE),
    re.compile(r"path:\s*'([^']+)'", re.MULTILINE),
]


def _scan_for_routes(root: Path) -> list[str]:
    """Scan TypeScript/JavaScript files for React Router route paths."""
    routes: set[str] = set()

    extensions = {".tsx", ".ts", ".jsx", ".js"}
    for ext in extensions:
        for file_path in root.rglob(f"*{ext}"):
            if "node_modules" in str(file_path):
                continue
            try:
                content = file_path.read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError):
                continue

            for pattern in _ROUTE_PATTERNS:
                for match in pattern.finditer(content):
                    route = match.group(1)
                    if route and not route.startswith(":") and route != "*":
                        routes.add(route)

    return sorted(routes)


def _infer_criticality(route: str) -> str:
    """Infer business criticality from route path."""
    revenue_keywords = ["checkout", "payment", "purchase", "order", "cart", "billing"]
    engagement_keywords = ["dashboard", "profile", "settings", "search", "browse"]

    lower = route.lower()
    for keyword in revenue_keywords:
        if keyword in lower:
            return "revenue"
    for keyword in engagement_keywords:
        if keyword in lower:
            return "engagement"
    return "engagement"


async def handle(arguments: dict[str, str]) -> str:
    source_dir = arguments.get("source_dir", ".")
    backend_url = arguments.get("backend_url", "/otlp")
    root = Path(source_dir)

    if not root.is_dir():
        return f"Error: '{source_dir}' is not a valid directory."

    routes = _scan_for_routes(root)
    app_name = root.resolve().name

    # Generate route config entries
    route_entries: list[str] = []
    for route in routes:
        criticality = _infer_criticality(route)
        route_entries.append(
            f"    '{route}': {{\n"
            f"      businessCriticality: '{criticality}',\n"
            f"      baseline: {{ pageLoadP50Ms: 800, pageLoadP99Ms: 2500 }},\n"
            f"    }},"
        )

    routes_block = "\n".join(route_entries) if route_entries else (
        "    '/': {\n"
        "      businessCriticality: 'engagement',\n"
        "      baseline: { pageLoadP50Ms: 800, pageLoadP99Ms: 2500 },\n"
        "    },"
    )

    # Generate the config file
    config_content = f"""import type {{ AgentTelWebConfig }} from '@agenttel/web';

export const agenttelConfig: AgentTelWebConfig = {{
  appName: '{app_name}',
  appVersion: '1.0.0',
  environment: 'development',
  collectorEndpoint: '{backend_url}',
  team: 'frontend',
  domain: 'your-domain',

  routes: {{
{routes_block}
  }},

  anomalyDetection: {{
    rageClickThreshold: 3,
    rageClickWindowMs: 2000,
    errorLoopThreshold: 5,
    errorLoopWindowMs: 30_000,
    apiFailureCascadeThreshold: 3,
    apiFailureCascadeWindowMs: 10_000,
  }},

  samplingRate: 1.0,
  debug: true,
}};
"""

    # Generate the init code
    init_code = """import { AgentTelWeb } from '@agenttel/web';
import { agenttelConfig } from './agenttel.config';

AgentTelWeb.init(agenttelConfig);
"""

    result = {
        "app_name": app_name,
        "routes_detected": routes,
        "proposed_changes": {
            "1_install_dependency": {
                "description": "Install the AgentTel Web SDK",
                "command": "npm install @agenttel/web",
            },
            "2_create_config": {
                "description": "Create agenttel.config.ts in your src/ directory",
                "filename": "agenttel.config.ts",
                "content": config_content,
            },
            "3_add_init_code": {
                "description": "Add SDK initialization to your main entry file (e.g., main.tsx)",
                "code": init_code,
                "note": "Add this before ReactDOM.createRoot() or at the top of your entry file",
            },
        },
        "instructions": (
            f"To instrument {app_name}:\n"
            f"1. Run: npm install @agenttel/web\n"
            f"2. Create src/agenttel.config.ts with the generated config\n"
            f"3. Add the SDK init code to your entry file (main.tsx)\n"
            f"4. Update route baselines after observing real traffic\n"
            f"5. Add journey definitions for multi-step user flows"
        ),
    }

    return json.dumps(result, indent=2)
