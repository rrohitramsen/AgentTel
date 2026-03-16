# Python API Reference

Complete API reference for the AgentTel Python SDK.

## Core

### `AgentTelEngine`

High-level orchestrator that wires all components together.

```python
from agenttel import AgentTelEngine

# From config file
engine = AgentTelEngine.from_config("agenttel.yml")

# From config object
engine = AgentTelEngine(config)

# Install into OTel SDK
engine.install(tracer_provider=None)
```

**Properties:**

| Property | Type | Description |
|----------|------|-------------|
| `config` | `AgentTelConfig` | The loaded configuration |
| `processor` | `AgentTelSpanProcessor` | The span processor |
| `topology` | `TopologyRegistry` | Service topology registry |
| `baseline_provider` | `CompositeBaselineProvider` | Combined baseline provider |
| `rolling_baselines` | `RollingBaselineProvider` | Rolling window baselines |
| `anomaly_detector` | `AnomalyDetector` | Z-score anomaly detector |
| `slo_tracker` | `SloTracker` | SLO budget tracker |
| `error_classifier` | `ErrorClassifier` | Error categorizer |
| `causality_tracker` | `CausalityTracker` | Dependency state tracker |
| `event_emitter` | `AgentTelEventEmitter` | Structured event emitter |

### `AgentTelConfig`

Pydantic model loaded from `agenttel.yml`.

```python
from agenttel import AgentTelConfig

config = AgentTelConfig.from_yaml("agenttel.yml")
config = AgentTelConfig.from_env()  # Uses AGENTTEL_CONFIG env var
```

### `AgentTelSpanProcessor`

OpenTelemetry `SpanProcessor` that enriches spans.

- `on_start()`: Attaches topology, operation context, and baseline attributes
- `on_end()`: Feeds rolling baselines, runs anomaly detection, checks SLO budgets

## Baseline Providers

### `RollingWindow`

Thread-safe ring buffer for latency statistics.

```python
from agenttel.baseline.rolling import RollingWindow

window = RollingWindow(capacity=1000)
window.record(latency_ms=42.5, is_error=False)
snapshot = window.snapshot()
# snapshot.mean, snapshot.p50, snapshot.p95, snapshot.p99, snapshot.error_rate
```

### `RollingBaselineProvider`

Provides baselines from rolling window data.

```python
from agenttel.baseline.rolling import RollingBaselineProvider

provider = RollingBaselineProvider(window_size=1000, min_samples=10)
provider.record("GET /users", latency_ms=42.5, is_error=False)
baseline = provider.get_baseline("GET /users")
```

### `CompositeBaselineProvider`

Chains providers with priority ordering (first match wins).

```python
from agenttel.baseline.composite import CompositeBaselineProvider

composite = CompositeBaselineProvider([rolling_provider, static_provider])
```

## Anomaly Detection

### `AnomalyDetector`

Z-score based anomaly detection.

```python
from agenttel.anomaly.detector import AnomalyDetector

detector = AnomalyDetector(z_score_threshold=3.0)
result = detector.detect(current_value=150.0, baseline_mean=100.0, baseline_stddev=10.0)
# result.is_anomaly, result.score, result.z_score, result.pattern
```

### `PatternMatcher`

Detects specific anomaly patterns.

```python
from agenttel.anomaly.patterns import PatternMatcher

matcher = PatternMatcher()
matcher.detect_latency_degradation(current_latency_ms, baseline)
matcher.detect_error_rate_spike(current_error_rate, baseline_error_rate)
matcher.detect_cascade_failure(dependency_health_list)
matcher.detect_sustained_degradation(recent_anomaly_results)
```

## SLO Tracking

### `SloTracker`

Thread-safe SLO tracker with error budget calculation.

```python
from agenttel.slo.tracker import SloTracker
from agenttel.models import SloDefinition

tracker = SloTracker()
tracker.register(SloDefinition(name="availability", target=0.999))
tracker.record_request("availability", is_failure=False)
status = tracker.get_status("availability")
# status.budget_remaining, status.burn_rate, status.alert_level
```

## Error Classification

### `ErrorClassifier`

Classifies exceptions and error messages.

```python
from agenttel.error.classifier import ErrorClassifier

classifier = ErrorClassifier()
category = classifier.classify_exception(TimeoutError("connection timed out"))
# ErrorCategory.DEPENDENCY_TIMEOUT
is_transient = classifier.is_transient(category)  # True
```

## FastAPI Integration

### `instrument_fastapi(app, config_path, engine)`

One-line FastAPI instrumentation. See [FastAPI Guide](../guides/python-fastapi.md).

### `@agent_operation(...)`

Decorator for setting operation context on spans. See [FastAPI Guide](../guides/python-fastapi.md).

## GenAI Instrumentation

### `instrument_openai(client)`

Wraps OpenAI client for automatic tracing. See [GenAI Guide](../guides/python-genai.md).

### `ModelCostCalculator`

Calculates cost for model invocations. See [GenAI Guide](../guides/python-genai.md).

## Agent / MCP

### `McpServer`

JSON-RPC 2.0 server exposing 15 built-in tools.

```python
from agenttel.agent.mcp_server import McpServer, create_default_tools

registry = create_default_tools(engine)
server = McpServer(registry, host="0.0.0.0", port=8091)
await server.start()
```

### `ServiceHealthAggregator`

Aggregates per-operation and dependency health from span data.

```python
from agenttel.agent.health import ServiceHealthAggregator

agg = ServiceHealthAggregator(service_name="my-service")
agg.record_operation("GET /users", latency_ms=42.5, is_error=False)
summary = agg.get_summary()
# summary.status: "HEALTHY" | "DEGRADED" | "CRITICAL"
```

## Agentic Observability

### `AgentTracer`

Builder pattern API for agentic spans.

```python
from agenttel.agentic import AgentTracer
from agenttel.enums import AgentType, StepType

tracer = (AgentTracer.create()
    .agent_name("my-agent")
    .agent_type(AgentType.SINGLE)
    .framework("langchain")
    .build())

with tracer.invoke("goal description") as invocation:
    with invocation.step(StepType.THOUGHT, "reasoning"):
        pass
    with invocation.tool_call("tool_name") as tool:
        tool.set_result({"key": "value"})
    invocation.complete(goal_achieved=True)
```

### Scope Context Managers

All scopes are Python context managers:

| Scope | Created Via | Purpose |
|-------|-----------|---------|
| `AgentInvocation` | `tracer.invoke(goal)` | Top-level invocation |
| `StepScope` | `invocation.step(type, desc)` | Reasoning step |
| `ToolCallScope` | `invocation.tool_call(name)` | Tool invocation |
| `TaskScope` | `invocation.task(name)` | Task decomposition |
| `HandoffScope` | `invocation.handoff(to_agent)` | Agent-to-agent delegation |
| `HumanCheckpointScope` | `invocation.human_checkpoint(type)` | Human-in-the-loop |
| `CodeExecutionScope` | `invocation.code_execution(lang)` | Code interpreter |
| `EvaluationScope` | `invocation.evaluation(scorer)` | Scoring/evaluation |
| `RetrieverScope` | `invocation.retriever(query)` | RAG retrieval |

### Orchestration Patterns

```python
from agenttel.agentic.orchestration import (
    SequentialOrchestration,
    ParallelOrchestration,
    EvalLoopOrchestration,
)
```

## Enums

All enums in `agenttel.enums`:

| Enum | Values |
|------|--------|
| `ServiceTier` | critical, standard, internal, experimental |
| `ErrorCategory` | dependency_timeout, connection_error, code_bug, rate_limited, auth_failure, resource_exhaustion, data_validation, unknown |
| `DependencyType` | internal_service, external_api, database, message_broker, cache, object_store, identity_provider |
| `DependencyCriticality` | required, degraded, optional |
| `SloType` | availability, latency_p99, latency_p50, error_rate |
| `AgentType` | single, orchestrator, worker, evaluator, critic, router |
| `OrchestrationPattern` | react, sequential, parallel, handoff, orchestrator_workers, evaluator_optimizer, group_chat, swarm, hierarchical |
| `StepType` | thought, action, observation, evaluation, revision |
| `GuardrailAction` | block, warn, log, modify |
