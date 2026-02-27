# API Reference

Complete reference for the AgentTel API surface — annotations, programmatic APIs, configuration properties, and enums.

---

## Annotations

### @AgentObservable

Service-level annotation declaring topology metadata. Applied to the main application class.

```java
@AgentObservable(
    service = "payment-service",
    team = "payments-platform",
    tier = ServiceTier.CRITICAL,
    domain = "commerce",
    onCallChannel = "#payments-oncall"
)
@SpringBootApplication
public class PaymentServiceApplication { }
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `service` | `String` | `""` | Service name (defaults to Spring application name) |
| `team` | `String` | **required** | Owning team identifier |
| `tier` | `ServiceTier` | `STANDARD` | Service criticality tier |
| `domain` | `String` | `""` | Business domain |
| `onCallChannel` | `String` | `""` | Escalation channel |

### @DeclareDependency

Declares a service dependency. Applied to the main application class. Repeatable.

```java
@DeclareDependency(
    name = "postgres",
    type = DependencyType.DATABASE,
    criticality = DependencyCriticality.REQUIRED,
    timeoutMs = 5000,
    circuitBreaker = true
)
@DeclareDependency(
    name = "stripe-api",
    type = DependencyType.EXTERNAL_API,
    criticality = DependencyCriticality.REQUIRED,
    fallback = "Return cached pricing"
)
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `name` | `String` | **required** | Dependency name |
| `type` | `DependencyType` | **required** | Dependency type |
| `criticality` | `DependencyCriticality` | `REQUIRED` | Impact of failure |
| `protocol` | `String` | `""` | Communication protocol |
| `timeoutMs` | `long` | `0` | Configured timeout in milliseconds |
| `circuitBreaker` | `boolean` | `false` | Whether circuit breaker is enabled |
| `fallback` | `String` | `""` | Fallback description |
| `healthEndpoint` | `String` | `""` | Health check endpoint |

### @DeclareConsumer

Declares a downstream consumer of this service. Applied to the main application class. Repeatable.

```java
@DeclareConsumer(
    name = "checkout-service",
    pattern = ConsumptionPattern.SYNCHRONOUS,
    slaLatencyMs = 200
)
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `name` | `String` | **required** | Consumer service name |
| `pattern` | `ConsumptionPattern` | `SYNCHRONOUS` | How the consumer calls this service |
| `slaLatencyMs` | `long` | `0` | Consumer's latency SLA |

### @AgentOperation

Method-level annotation declaring operational semantics. Applied to Spring MVC/WebFlux endpoints or any traced method.

```java
@AgentOperation(
    expectedLatencyP50 = "45ms",
    expectedLatencyP99 = "200ms",
    expectedErrorRate = 0.001,
    retryable = true,
    idempotent = true,
    runbookUrl = "https://wiki/runbooks/process-payment",
    fallbackDescription = "Return cached pricing",
    escalationLevel = EscalationLevel.PAGE_ONCALL,
    safeToRestart = true
)
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `expectedLatencyP50` | `String` | `""` | Expected P50 latency (e.g., `"45ms"`, `"1.5s"`) |
| `expectedLatencyP99` | `String` | `""` | Expected P99 latency |
| `expectedErrorRate` | `double` | `0.0` | Expected error rate (0.0–1.0) |
| `retryable` | `boolean` | `false` | Whether the operation can be retried |
| `retryAfterMs` | `long` | `0` | Suggested retry delay |
| `idempotent` | `boolean` | `false` | Whether retries are safe (same result) |
| `runbookUrl` | `String` | `""` | Operational runbook URL |
| `fallbackDescription` | `String` | `""` | Description of fallback behavior |
| `escalationLevel` | `EscalationLevel` | `NOTIFY_TEAM` | How to escalate issues |
| `safeToRestart` | `boolean` | `false` | Whether the service can be safely restarted |

---

## Programmatic API

### AgentTelEngine

The main orchestrator. Builds and wires all core components.

```java
AgentTelEngine engine = AgentTelEngine.builder()
    .openTelemetry(openTelemetry)
    .topology(topologyRegistry)
    .addStaticBaseline("POST /api/payments",
        new OperationBaseline(45.0, 200.0, 0.001))
    .patternMatcher(new PatternMatcher(2.0, 5.0, 3))
    .rollingBaselineProvider(new RollingBaselineProvider(1000, 10))
    .sloTracker(sloTracker)
    .build();

// Get the SpanProcessor to register with OTel SDK
SpanProcessor processor = engine.createSpanProcessor();
```

### TopologyRegistry

```java
TopologyRegistry topology = new TopologyRegistry();
topology.setTeam("payments-platform");
topology.setTier(ServiceTier.CRITICAL);
topology.setDomain("commerce");
topology.setOnCallChannel("#payments-oncall");

topology.registerDependency(new DependencyDescriptor(
    "postgres", DependencyType.DATABASE, DependencyCriticality.REQUIRED,
    "postgresql", 5000, true, "", "/health/postgres"
));

topology.registerConsumer(new ConsumerDescriptor(
    "checkout-service", ConsumptionPattern.SYNCHRONOUS, 200
));
```

### RollingBaselineProvider

```java
RollingBaselineProvider rolling = new RollingBaselineProvider(1000, 10);

// Record observations (typically done by SpanProcessor)
rolling.record("POST /api/payments", 45.0, false);

// Query baseline
Optional<RollingWindow.Snapshot> snapshot = rolling.getSnapshot("POST /api/payments");
// snapshot.p50(), snapshot.p99(), snapshot.mean(), snapshot.stddev(), snapshot.errorRate()

// As a BaselineProvider
Optional<OperationBaseline> baseline = rolling.getBaseline("POST /api/payments");
```

### CompositeBaselineProvider

```java
// Chain: static → rolling → default
CompositeBaselineProvider composite = new CompositeBaselineProvider(
    staticProvider, rollingProvider
);

// Returns the first non-empty baseline
Optional<OperationBaseline> baseline = composite.getBaseline("POST /api/payments");
```

### SloTracker

```java
SloTracker tracker = new SloTracker();

tracker.register(SloDefinition.builder("payment-availability")
    .operationName("POST /api/payments")
    .type(SloDefinition.SloType.AVAILABILITY)
    .target(0.999)  // 99.9%
    .build());

tracker.register(SloDefinition.builder("payment-latency")
    .operationName("POST /api/payments")
    .type(SloDefinition.SloType.LATENCY_P99)
    .target(0.99)   // 99% under P99 threshold
    .build());

// Record (typically done by SpanProcessor)
tracker.recordSuccess("POST /api/payments");
tracker.recordFailure("POST /api/payments");

// Query
SloStatus status = tracker.getStatus("payment-availability");
// status.target()          → 0.999
// status.actual()          → 0.995
// status.budgetRemaining() → 0.50
// status.burnRate()        → 0.50

// Alert check
List<SloAlert> alerts = tracker.checkAlerts();
// alerts[0].severity()  → CRITICAL | WARNING | INFO
```

### PatternMatcher

```java
PatternMatcher matcher = new PatternMatcher(2.0, 5.0, 3);

// Feed observations (typically done by SpanProcessor)
matcher.recordLatency("POST /api/payments", 312.0);
matcher.recordDependencyError("stripe-api");

// Detect patterns
List<IncidentPattern> patterns = matcher.detectPatterns(
    "POST /api/payments", 312.0, true, baselineSnapshot
);
// patterns may contain: LATENCY_DEGRADATION, CASCADE_FAILURE, etc.
```

### ServiceHealthAggregator

```java
ServiceHealthAggregator health = new ServiceHealthAggregator(rollingBaselines, sloTracker);

// Feed span data
health.recordSpan("POST /api/payments", 312.0, false);
health.recordDependencyCall("stripe-api", 2100.0, true);

// Query
ServiceHealthSummary summary = health.getHealthSummary("payment-service");
Optional<OperationSummary> op = health.getOperationHealth("POST /api/payments");
```

### MCP Server

```java
McpServer server = new AgentTelMcpServerBuilder()
    .port(8081)
    .contextProvider(agentContextProvider)
    .remediationExecutor(remediationExecutor)
    .build();

// Register custom tools
server.registerTool(
    new McpToolDefinition("custom_tool", "Description",
        Map.of("param", new ParameterDefinition("string", "Param description")),
        List.of("param")),
    args -> "Result: " + args.get("param")
);

server.start();
// ...
server.stop();
```

---

## Configuration Properties

All properties are under the `agenttel` prefix in `application.yml` or `application.properties`.

### Topology

```yaml
agenttel:
  topology:
    team: payments-platform       # Owning team
    tier: critical                # critical | standard | internal | experimental
    domain: commerce              # Business domain
    on-call-channel: "#payments-oncall"
    repo-url: "https://github.com/org/payment-service"
```

### Dependencies

```yaml
agenttel:
  dependencies:
    - name: postgres
      type: database              # database | rest_api | grpc | message_broker | cache | ...
      criticality: required       # required | degraded | optional
      protocol: postgresql
      timeout-ms: 5000
      circuit-breaker: true
      fallback: "Return cached data"
      health-endpoint: "/health/postgres"
    - name: stripe-api
      type: rest_api
      criticality: required
```

### Consumers

```yaml
agenttel:
  consumers:
    - name: checkout-service
      pattern: synchronous        # synchronous | asynchronous | batch | streaming
      sla-latency-ms: 200
```

### Baselines

```yaml
agenttel:
  baselines:
    rolling-window-size: 1000     # Observations per sliding window (default: 1000)
    rolling-min-samples: 10       # Min samples before baseline is valid (default: 10)
```

### Anomaly Detection

```yaml
agenttel:
  anomaly-detection:
    z-score-threshold: 3.0        # Z-score threshold for anomaly detection (default: 3.0)
```

---

## Enums Reference

### ServiceTier

| Value | Description |
|-------|-------------|
| `CRITICAL` | User-facing, revenue-impacting |
| `STANDARD` | Important but not immediately revenue-impacting |
| `INTERNAL` | Internal tooling |
| `EXPERIMENTAL` | Non-production |

### DependencyType

| Value | Description |
|-------|-------------|
| `INTERNAL_SERVICE` | Another service in the same organization |
| `EXTERNAL_API` | Third-party API |
| `DATABASE` | Database (SQL or NoSQL) |
| `MESSAGE_BROKER` | Kafka, RabbitMQ, SQS, etc. |
| `CACHE` | Redis, Memcached, etc. |
| `OBJECT_STORE` | S3, GCS, etc. |
| `IDENTITY_PROVIDER` | Auth0, Okta, etc. |

### DependencyCriticality

| Value | Description |
|-------|-------------|
| `REQUIRED` | Failure causes outage |
| `DEGRADED` | Failure causes reduced functionality |
| `OPTIONAL` | Failure has no user impact |

### EscalationLevel

| Value | Description |
|-------|-------------|
| `AUTO_RESOLVE` | Agent can handle autonomously |
| `NOTIFY_TEAM` | Async notification |
| `PAGE_ONCALL` | Page on-call engineer |
| `INCIDENT_COMMANDER` | Full incident management |

### ConsumptionPattern

| Value | Description |
|-------|-------------|
| `SYNCHRONOUS` | Request-response |
| `ASYNCHRONOUS` | Fire-and-forget or callback |
| `BATCH` | Periodic bulk processing |
| `STREAMING` | Continuous data flow |

### IncidentPattern

| Value | Description |
|-------|-------------|
| `CASCADE_FAILURE` | Multiple downstream failures |
| `MEMORY_LEAK` | Monotonically increasing latency |
| `THUNDERING_HERD` | Traffic spike after recovery |
| `COLD_START` | High latency on fresh instances |
| `ERROR_RATE_SPIKE` | Sudden error rate increase |
| `LATENCY_DEGRADATION` | Sustained latency elevation |
