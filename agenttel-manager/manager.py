"""AgentTel Manager — lightweight HTTP service to manage agents and generate telemetry."""

from __future__ import annotations

import asyncio
import collections
import json
import logging
import os
import random
import signal
import time
import uuid
from typing import Any

import httpx
from aiohttp import web

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("agenttel-manager")

# ── Configuration from environment ────────────────────────────────────

MCP_HOST = os.environ.get("MCP_HOST", "localhost")
MCP_PORT = os.environ.get("MCP_PORT", "8081")
OTEL_COLLECTOR_URL = os.environ.get("OTEL_COLLECTOR_URL", "http://localhost:4318")
PAYMENT_SERVICE_URL = os.environ.get("PAYMENT_SERVICE_URL", "http://localhost:8080")
INSTRUMENT_HOST = os.environ.get("INSTRUMENT_HOST", "localhost")
INSTRUMENT_PORT = os.environ.get("INSTRUMENT_PORT", "8082")
SERVER_PORT = int(os.environ.get("MANAGER_PORT", "8083"))

# ── In-memory state ──────────────────────────────────────────────────

monitor_process: asyncio.subprocess.Process | None = None
monitor_started_at: float | None = None
monitor_last_error: str | None = None
monitor_logs: collections.deque[str] = collections.deque(maxlen=200)
log_reader_task: asyncio.Task[None] | None = None
frontend_telemetry_count = 0


# ── Helpers ───────────────────────────────────────────────────────────

def _hex(n: int) -> str:
    return uuid.uuid4().hex[:n]


def _trace_id() -> str:
    return uuid.uuid4().hex


def _span_id() -> str:
    return uuid.uuid4().hex[:16]


def _now_nanos() -> str:
    return str(int(time.time() * 1e9))


def _nanos_ago(ms: int) -> str:
    return str(int(time.time() * 1e9) - int(ms * 1e6))


async def _read_stream(stream: asyncio.StreamReader, prefix: str) -> None:
    """Read subprocess output line-by-line into the in-memory log buffer."""
    while True:
        line = await stream.readline()
        if not line:
            break
        text = line.decode("utf-8", errors="replace").rstrip()
        monitor_logs.append(f"[{prefix}] {text}")
        logger.info("[monitor-%s] %s", prefix, text)


# ── Monitor management ───────────────────────────────────────────────

async def start_monitor(request: web.Request) -> web.Response:
    global monitor_process, monitor_started_at, monitor_last_error, log_reader_task

    if monitor_process is not None and monitor_process.returncode is None:
        return web.json_response({"error": "Monitor is already running"}, status=409)

    try:
        body = await request.json()
    except Exception:
        return web.json_response({"error": "Invalid JSON body"}, status=400)

    api_key = body.get("api_key", "").strip()
    if not api_key:
        return web.json_response({"error": "api_key is required"}, status=400)

    model = body.get("model", "claude-sonnet-4-5-20250929")
    interval = body.get("interval", 10)
    dry_run = body.get("dry_run", False)

    # Build environment — key lives only in subprocess memory
    env = {
        **os.environ,
        "ANTHROPIC_API_KEY": api_key,
        "MCP_HOST": MCP_HOST,
        "MCP_PORT": MCP_PORT,
    }
    if OTEL_COLLECTOR_URL:
        env["OTEL_EXPORTER_OTLP_ENDPOINT"] = OTEL_COLLECTOR_URL

    # Write a temporary in-memory config override via env
    env["AGENTTEL_MONITOR_MODEL"] = model
    env["AGENTTEL_MONITOR_INTERVAL"] = str(interval)
    env["AGENTTEL_MONITOR_DRY_RUN"] = str(dry_run).lower()

    monitor_logs.clear()
    monitor_last_error = None

    try:
        monitor_process = await asyncio.create_subprocess_exec(
            "python", "-m", "agenttel_monitor",
            "--config", "/app/monitor/monitor.yml",
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            env=env,
        )
        monitor_started_at = time.time()

        # Start background log readers
        async def _read_both() -> None:
            tasks = []
            if monitor_process.stdout:
                tasks.append(_read_stream(monitor_process.stdout, "out"))
            if monitor_process.stderr:
                tasks.append(_read_stream(monitor_process.stderr, "err"))
            if tasks:
                await asyncio.gather(*tasks)

        log_reader_task = asyncio.create_task(_read_both())

        logger.info("Monitor started with PID %s", monitor_process.pid)
        return web.json_response({
            "status": "started",
            "pid": monitor_process.pid,
            "model": model,
            "interval": interval,
            "dry_run": dry_run,
        })
    except Exception as e:
        monitor_last_error = str(e)
        logger.error("Failed to start monitor: %s", e)
        return web.json_response({"error": str(e)}, status=500)


async def stop_monitor(_request: web.Request) -> web.Response:
    global monitor_process, monitor_started_at, log_reader_task

    if monitor_process is None or monitor_process.returncode is not None:
        return web.json_response({"error": "Monitor is not running"}, status=409)

    pid = monitor_process.pid
    monitor_process.terminate()
    try:
        await asyncio.wait_for(monitor_process.wait(), timeout=10)
    except asyncio.TimeoutError:
        monitor_process.kill()
        await monitor_process.wait()

    if log_reader_task:
        log_reader_task.cancel()
        log_reader_task = None

    monitor_logs.append("[manager] Monitor stopped")
    monitor_process = None
    monitor_started_at = None

    logger.info("Monitor stopped (PID %s)", pid)
    return web.json_response({"status": "stopped", "pid": pid})


async def get_monitor_logs(_request: web.Request) -> web.Response:
    return web.json_response({"logs": list(monitor_logs)})


async def get_status(_request: web.Request) -> web.Response:
    global monitor_last_error

    # Check monitor
    monitor_running = monitor_process is not None and monitor_process.returncode is None
    if monitor_process and monitor_process.returncode is not None and monitor_last_error is None:
        monitor_last_error = f"Exited with code {monitor_process.returncode}"

    monitor_status: dict[str, Any] = {
        "running": monitor_running,
        "pid": monitor_process.pid if monitor_running else None,
        "uptime_seconds": round(time.time() - monitor_started_at) if monitor_running and monitor_started_at else None,
        "last_error": monitor_last_error,
    }

    # Check instrument agent
    instrument_status: dict[str, Any] = {"running": False, "tools": []}
    try:
        async with httpx.AsyncClient(timeout=3) as client:
            resp = await client.post(
                f"http://{INSTRUMENT_HOST}:{INSTRUMENT_PORT}/mcp",
                json={"jsonrpc": "2.0", "method": "tools/list", "params": {}, "id": 1},
            )
            if resp.status_code == 200:
                data = resp.json()
                tools = data.get("result", {}).get("tools", [])
                instrument_status = {
                    "running": True,
                    "url": f"http://{INSTRUMENT_HOST}:{INSTRUMENT_PORT}",
                    "tools": [t.get("name", "") for t in tools],
                }
    except Exception:
        pass

    # Check backend MCP
    backend_status: dict[str, Any] = {"running": False, "tools": []}
    try:
        async with httpx.AsyncClient(timeout=3) as client:
            resp = await client.post(
                f"http://{MCP_HOST}:{MCP_PORT}/mcp",
                json={"jsonrpc": "2.0", "method": "tools/list", "params": {}, "id": 1},
            )
            if resp.status_code == 200:
                data = resp.json()
                tools = data.get("result", {}).get("tools", [])
                backend_status = {
                    "running": True,
                    "url": f"http://{MCP_HOST}:{MCP_PORT}",
                    "tools": [t.get("name", "") for t in tools],
                }
    except Exception:
        pass

    # Check collector by sending an empty OTLP request (it returns 200 or 400, not connection refused)
    collector_running = False
    try:
        async with httpx.AsyncClient(timeout=3) as client:
            resp = await client.post(
                f"{OTEL_COLLECTOR_URL}/v1/traces",
                json={"resourceSpans": []},
                headers={"Content-Type": "application/json"},
            )
            # Any response (200, 400, etc.) means collector is reachable
            collector_running = True
    except Exception:
        pass

    return web.json_response({
        "monitor": monitor_status,
        "instrument": instrument_status,
        "backend": backend_status,
        "collector": {"running": collector_running},
        "frontend_telemetry": {"generated_count": frontend_telemetry_count},
    })


# ── Frontend telemetry generation ────────────────────────────────────

JOURNEY_STEPS = [
    {"route": "/products", "title": "Browse Products", "criticality": "high"},
    {"route": "/products/123", "title": "View Product", "criticality": "medium"},
    {"route": "/cart", "title": "Shopping Cart", "criticality": "high"},
    {"route": "/checkout", "title": "Checkout", "criticality": "critical"},
    {"route": "/checkout/payment", "title": "Payment", "criticality": "critical"},
    {"route": "/confirmation", "title": "Order Confirmation", "criticality": "high"},
]


def _make_resource_attrs() -> list[dict]:
    return [
        {"key": "service.name", "value": {"stringValue": "checkout-web"}},
        {"key": "agenttel.client.app.name", "value": {"stringValue": "checkout-web"}},
        {"key": "agenttel.client.app.version", "value": {"stringValue": "1.0.0"}},
        {"key": "agenttel.client.app.platform", "value": {"stringValue": "web"}},
        {"key": "agenttel.client.app.environment", "value": {"stringValue": "demo"}},
        {"key": "agenttel.client.topology.team", "value": {"stringValue": "frontend-platform"}},
        {"key": "agenttel.client.topology.domain", "value": {"stringValue": "checkout"}},
    ]


def _build_span(
    trace_id: str,
    name: str,
    kind: int,
    duration_ms: int,
    parent_span_id: str = "",
    attributes: list[dict] | None = None,
    status_code: int = 0,
    start_offset_ms: int = 0,
) -> dict:
    now_ns = int(time.time() * 1e9) - int(start_offset_ms * 1e6)
    return {
        "traceId": trace_id,
        "spanId": _span_id(),
        "parentSpanId": parent_span_id,
        "name": name,
        "kind": kind,
        "startTimeUnixNano": str(now_ns - int(duration_ms * 1e6)),
        "endTimeUnixNano": str(now_ns),
        "attributes": attributes or [],
        "events": [],
        "status": {"code": status_code, "message": ""},
    }


def _generate_journey(
    journey_index: int,
    scenario: str = "normal",
) -> tuple[list[dict], str | None]:
    """Generate spans for one full checkout journey. Returns (spans, backend_trace_id)."""
    spans: list[dict] = []
    trace_id = _trace_id()
    backend_trace_id: str | None = None

    total_steps = len(JOURNEY_STEPS)
    journey_started_at = _now_nanos()
    base_offset = random.randint(0, 5000)  # stagger journeys

    for step_idx, step in enumerate(JOURNEY_STEPS):
        # Page load timing
        if scenario == "slow":
            load_ms = random.randint(2000, 6000)
        elif scenario == "errors" and random.random() < 0.1:
            load_ms = random.randint(3000, 8000)
        else:
            load_ms = random.randint(80, 400)

        offset = base_offset + step_idx * random.randint(3000, 8000)

        page_span_id = _span_id()

        # Page load span
        page_attrs = [
            {"key": "agenttel.client.page.route", "value": {"stringValue": step["route"]}},
            {"key": "agenttel.client.page.title", "value": {"stringValue": step["title"]}},
            {"key": "agenttel.client.page.business_criticality", "value": {"stringValue": step["criticality"]}},
            {"key": "agenttel.client.baseline.page_load_p50_ms", "value": {"intValue": "200"}},
            {"key": "agenttel.client.baseline.page_load_p99_ms", "value": {"intValue": "1500"}},
            {"key": "agenttel.client.baseline.source", "value": {"stringValue": "static"}},
            {"key": "agenttel.client.journey.name", "value": {"stringValue": "checkout-flow"}},
            {"key": "agenttel.client.journey.step", "value": {"intValue": str(step_idx + 1)}},
            {"key": "agenttel.client.journey.total_steps", "value": {"intValue": str(total_steps)}},
            {"key": "agenttel.client.journey.started_at", "value": {"stringValue": journey_started_at}},
        ]

        # Anomaly detection for slow pages
        if load_ms > 1500:
            z_score = round((load_ms - 200) / 300, 2)
            page_attrs.extend([
                {"key": "agenttel.client.anomaly.detected", "value": {"boolValue": True}},
                {"key": "agenttel.client.anomaly.score", "value": {"doubleValue": min(z_score / 10, 1.0)}},
                {"key": "agenttel.client.anomaly.pattern", "value": {"stringValue": "SLOW_PAGE_LOAD"}},
            ])

        spans.append(_build_span(
            trace_id, f"page_load {step['route']}", 1, load_ms,
            attributes=page_attrs, start_offset_ms=offset,
        ))

        # User interaction span (click/scroll)
        interaction_ms = random.randint(5, 50)
        interaction_attrs = [
            {"key": "agenttel.client.interaction.type", "value": {"stringValue": "click"}},
            {"key": "agenttel.client.interaction.target", "value": {"stringValue": f"button.{step['route'].strip('/').replace('/', '-') or 'home'}"}},
            {"key": "agenttel.client.interaction.outcome", "value": {"stringValue": "success"}},
        ]
        spans.append(_build_span(
            trace_id, f"interaction {step['route']}", 1, interaction_ms,
            parent_span_id=page_span_id,
            attributes=interaction_attrs,
            start_offset_ms=offset - load_ms,
        ))

        # Payment step: make API call span
        if step["route"] == "/checkout/payment":
            api_duration = random.randint(30, 200)
            if scenario == "slow":
                api_duration = random.randint(500, 3000)

            is_error = scenario == "errors" and random.random() < 0.2
            status = 500 if is_error else 200

            api_span_id = _span_id()
            api_attrs = [
                {"key": "http.method", "value": {"stringValue": "POST"}},
                {"key": "http.url", "value": {"stringValue": f"{PAYMENT_SERVICE_URL}/api/payments"}},
                {"key": "http.status_code", "value": {"intValue": str(status)}},
                {"key": "agenttel.client.interaction.type", "value": {"stringValue": "api_call"}},
                {"key": "agenttel.client.interaction.response_time_ms", "value": {"intValue": str(api_duration)}},
                {"key": "agenttel.client.interaction.outcome", "value": {"stringValue": "error" if is_error else "success"}},
                {"key": "agenttel.client.baseline.api_call_p50_ms", "value": {"intValue": "50"}},
                {"key": "agenttel.client.correlation.backend_service", "value": {"stringValue": "payments-platform"}},
                {"key": "agenttel.client.correlation.backend_operation", "value": {"stringValue": "POST /api/payments"}},
            ]
            spans.append(_build_span(
                trace_id, "fetch POST /api/payments", 3, api_duration,
                parent_span_id=page_span_id,
                attributes=api_attrs,
                status_code=2 if is_error else 0,
                start_offset_ms=offset - load_ms + random.randint(10, 50),
            ))
            backend_trace_id = trace_id

    return spans, backend_trace_id


async def generate_frontend_telemetry(request: web.Request) -> web.Response:
    global frontend_telemetry_count

    try:
        body = await request.json()
    except Exception:
        body = {}

    count = min(body.get("count", 5), 50)
    scenario = body.get("scenario", "normal")
    delay_ms = body.get("delay_ms", 200)

    if scenario not in ("normal", "slow", "errors"):
        return web.json_response({"error": "scenario must be normal, slow, or errors"}, status=400)

    all_spans: list[dict] = []
    trace_ids: list[str] = []

    for i in range(count):
        spans, backend_trace_id = _generate_journey(i, scenario)
        all_spans.extend(spans)
        if backend_trace_id:
            trace_ids.append(backend_trace_id)

        # Also make a real backend request to generate backend traces
        try:
            async with httpx.AsyncClient(timeout=10) as client:
                amount = round(random.uniform(10, 500), 2)
                await client.post(
                    f"{PAYMENT_SERVICE_URL}/api/payments",
                    json={"amount": amount, "currency": "USD"},
                )
        except Exception:
            pass

        if delay_ms > 0 and i < count - 1:
            await asyncio.sleep(delay_ms / 1000)

    # Send all frontend spans to OTEL collector
    payload = {
        "resourceSpans": [{
            "resource": {"attributes": _make_resource_attrs()},
            "scopeSpans": [{
                "scope": {"name": "@agenttel/web", "version": "1.0.0"},
                "spans": all_spans,
            }],
        }],
    }

    sent = False
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(
                f"{OTEL_COLLECTOR_URL}/v1/traces",
                json=payload,
                headers={"Content-Type": "application/json"},
            )
            sent = resp.status_code < 300
    except Exception as e:
        logger.error("Failed to send frontend spans to collector: %s", e)

    span_count = len(all_spans)
    frontend_telemetry_count += span_count

    return web.json_response({
        "generated": span_count,
        "journeys": count,
        "scenario": scenario,
        "traces": trace_ids[:10],
        "sent_to_collector": sent,
    })


# ── Agent logs & config proxies ──────────────────────────────────────

async def get_instrument_logs(_request: web.Request) -> web.Response:
    """Proxy to instrument agent's /logs endpoint."""
    try:
        async with httpx.AsyncClient(timeout=3) as client:
            resp = await client.get(f"http://{INSTRUMENT_HOST}:{INSTRUMENT_PORT}/logs")
            if resp.status_code == 200:
                return web.json_response(resp.json())
    except Exception as e:
        logger.warning("Failed to fetch instrument logs: %s", e)
    return web.json_response({"logs": []})


async def get_instrument_config(_request: web.Request) -> web.Response:
    """Proxy to instrument agent's /config endpoint (managed agenttel.yml)."""
    try:
        async with httpx.AsyncClient(timeout=3) as client:
            resp = await client.get(f"http://{INSTRUMENT_HOST}:{INSTRUMENT_PORT}/config")
            if resp.status_code == 200:
                return web.json_response(resp.json())
    except Exception as e:
        logger.warning("Failed to fetch instrument config: %s", e)
    return web.json_response({"error": "Not available"}, status=503)


async def get_backend_activity(_request: web.Request) -> web.Response:
    """Fetch recent agent actions from the backend MCP server."""
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.post(
                f"http://{MCP_HOST}:{MCP_PORT}/mcp",
                json={
                    "jsonrpc": "2.0",
                    "method": "tools/call",
                    "params": {"name": "get_recent_agent_actions", "arguments": {}},
                    "id": 1,
                },
            )
            if resp.status_code == 200:
                data = resp.json()
                content = data.get("result", {}).get("content", [])
                text = content[0].get("text", "") if content else ""
                return web.json_response({"logs": text.strip().split("\n") if text.strip() else []})
    except Exception as e:
        logger.warning("Failed to fetch backend activity: %s", e)
    return web.json_response({"logs": []})


async def get_backend_config(_request: web.Request) -> web.Response:
    """Fetch service health + SLO summary from the backend MCP server."""
    result: dict[str, str | None] = {"health": None, "slo": None}
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.post(
                f"http://{MCP_HOST}:{MCP_PORT}/mcp",
                json={
                    "jsonrpc": "2.0",
                    "method": "tools/call",
                    "params": {"name": "get_service_health", "arguments": {}},
                    "id": 1,
                },
            )
            if resp.status_code == 200:
                data = resp.json()
                content = data.get("result", {}).get("content", [])
                if content:
                    result["health"] = content[0].get("text", "")

            resp2 = await client.post(
                f"http://{MCP_HOST}:{MCP_PORT}/mcp",
                json={
                    "jsonrpc": "2.0",
                    "method": "tools/call",
                    "params": {"name": "get_slo_report", "arguments": {}},
                    "id": 2,
                },
            )
            if resp2.status_code == 200:
                data2 = resp2.json()
                content2 = data2.get("result", {}).get("content", [])
                if content2:
                    result["slo"] = content2[0].get("text", "")
    except Exception as e:
        logger.warning("Failed to fetch backend config: %s", e)
    return web.json_response(result)


# ── Health ───────────────────────────────────────────────────────────

async def health(_request: web.Request) -> web.Response:
    return web.json_response({"status": "ok"})


# ── App setup ────────────────────────────────────────────────────────

def create_app() -> web.Application:
    app = web.Application()

    # CORS — allow dashboard origin
    import aiohttp_cors
    cors = aiohttp_cors.setup(app, defaults={
        "*": aiohttp_cors.ResourceOptions(
            allow_credentials=True,
            expose_headers="*",
            allow_headers="*",
            allow_methods="*",
        ),
    })

    routes = [
        web.get("/health", health),
        web.get("/status", get_status),
        web.post("/start-monitor", start_monitor),
        web.post("/stop-monitor", stop_monitor),
        web.get("/monitor-logs", get_monitor_logs),
        web.get("/instrument-logs", get_instrument_logs),
        web.get("/instrument-config", get_instrument_config),
        web.get("/backend-activity", get_backend_activity),
        web.get("/backend-config", get_backend_config),
        web.post("/generate-frontend-telemetry", generate_frontend_telemetry),
    ]
    for route in routes:
        cors.add(app.router.add_route(route.method, route.path, route.handler))

    return app


if __name__ == "__main__":
    logger.info("AgentTel Manager starting on port %s", SERVER_PORT)
    app = create_app()
    web.run_app(app, host="0.0.0.0", port=SERVER_PORT)
