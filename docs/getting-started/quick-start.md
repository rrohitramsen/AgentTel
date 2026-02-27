# Quick Start

Get up and running with AgentTel in minutes.

## 1. Add Dependencies

=== "Maven"

    ```xml
    <dependencies>
        <!-- Core: span enrichment, baselines, anomaly detection, SLO tracking -->
        <dependency>
            <groupId>io.agenttel</groupId>
            <artifactId>agenttel-spring-boot-starter</artifactId>
            <version>0.1.0-alpha</version>
        </dependency>

        <!-- Optional: GenAI instrumentation -->
        <dependency>
            <groupId>io.agenttel</groupId>
            <artifactId>agenttel-genai</artifactId>
            <version>0.1.0-alpha</version>
        </dependency>

        <!-- Optional: Agent interface layer (MCP server, incident context, remediation) -->
        <dependency>
            <groupId>io.agenttel</groupId>
            <artifactId>agenttel-agent</artifactId>
            <version>0.1.0-alpha</version>
        </dependency>
    </dependencies>
    ```

=== "Gradle (Kotlin)"

    ```kotlin
    dependencies {
        // Core: span enrichment, baselines, anomaly detection, SLO tracking
        implementation("io.agenttel:agenttel-spring-boot-starter:0.1.0-alpha")

        // Optional: GenAI instrumentation
        implementation("io.agenttel:agenttel-genai:0.1.0-alpha")

        // Optional: Agent interface layer (MCP server, incident context, remediation)
        implementation("io.agenttel:agenttel-agent:0.1.0-alpha")
    }
    ```

=== "Gradle (Groovy)"

    ```groovy
    dependencies {
        // Core: span enrichment, baselines, anomaly detection, SLO tracking
        implementation 'io.agenttel:agenttel-spring-boot-starter:0.1.0-alpha'

        // Optional: GenAI instrumentation
        implementation 'io.agenttel:agenttel-genai:0.1.0-alpha'

        // Optional: Agent interface layer (MCP server, incident context, remediation)
        implementation 'io.agenttel:agenttel-agent:0.1.0-alpha'
    }
    ```

## 2. Configure Your Service

All enrichment is driven by YAML configuration — no code changes needed:

```yaml
# application.yml
agenttel:
  # Topology: set once on the OTel Resource, associated with all telemetry
  topology:
    team: payments-platform
    tier: critical
    domain: commerce
    on-call-channel: "#payments-oncall"
  dependencies:
    - name: postgres
      type: database
      criticality: required
      timeout-ms: 5000
      circuit-breaker: true
    - name: stripe-api
      type: rest_api
      criticality: required
      fallback: "Return cached pricing"

  # Reusable operational profiles — reduce repetition across operations
  profiles:
    critical-write:
      retryable: false
      escalation-level: page_oncall
      safe-to-restart: false
    read-only:
      retryable: true
      idempotent: true
      escalation-level: notify_team

  # Per-operation baselines and decision metadata
  # Use bracket notation [key] for operation names with special characters
  operations:
    "[POST /api/payments]":
      profile: critical-write
      expected-latency-p50: "45ms"
      expected-latency-p99: "200ms"
      expected-error-rate: 0.001
      retryable: true               # overrides profile default
      idempotent: true
      runbook-url: "https://wiki/runbooks/process-payment"
    "[GET /api/payments/{id}]":
      profile: read-only
      expected-latency-p50: "15ms"
      expected-latency-p99: "80ms"

  baselines:
    rolling-window-size: 1000
    rolling-min-samples: 10
  anomaly-detection:
    z-score-threshold: 3.0
```

## 3. Optional: Annotate for IDE Support

Annotations are optional — YAML config above is sufficient. Use `@AgentOperation` when you want IDE autocomplete and compile-time validation:

```java
@AgentOperation(profile = "critical-write")
@PostMapping("/api/payments")
public ResponseEntity<PaymentResult> processPayment(@RequestBody PaymentRequest req) {
    // Your business logic — spans are enriched automatically
}
```

!!! info
    When both YAML config and annotations define the same operation, YAML config takes priority. Per-operation values override profile defaults.

## 4. Start the MCP Server (Optional)

```java
// Expose telemetry to AI agents via MCP
McpServer mcp = new AgentTelMcpServerBuilder()
    .port(8081)
    .contextProvider(agentContextProvider)
    .remediationExecutor(remediationExecutor)
    .build();
mcp.start();
```

AI agents can now call tools like `get_service_health`, `get_incident_context`, `list_remediation_actions`, and `execute_remediation` over JSON-RPC.

## 5. What You Get

**Resource attributes** (set once per service, associated with all telemetry):

```
agenttel.topology.team         = "payments-platform"
agenttel.topology.tier         = "critical"
agenttel.topology.domain       = "commerce"
agenttel.topology.on_call_channel = "#payments-oncall"
agenttel.topology.dependencies = [{"name":"postgres","type":"database",...}]
```

**Span attributes** (per operation, only on operations with registered metadata):

```
agenttel.baseline.latency_p50_ms = 45.0
agenttel.baseline.latency_p99_ms = 200.0
agenttel.baseline.error_rate     = 0.001
agenttel.baseline.source         = "static"
agenttel.decision.retryable      = true
agenttel.decision.runbook_url    = "https://wiki/runbooks/process-payment"
agenttel.decision.escalation_level = "page_oncall"
agenttel.anomaly.detected        = false
agenttel.slo.budget_remaining    = 0.85
```

**Incident context** via MCP when something goes wrong:

```
=== INCIDENT inc-a3f2b1c4 ===
SEVERITY: HIGH
SUMMARY: POST /api/payments experiencing elevated error rate (5.2%)

## WHAT IS HAPPENING
Error Rate: 5.2% (baseline: 0.1%)
Latency P50: 312ms (baseline: 45ms)
Patterns: ERROR_RATE_SPIKE

## WHAT CHANGED
Last Deploy: v2.1.0 at 2025-01-15T14:30:00Z

## WHAT IS AFFECTED
Scope: operation_specific
User-Facing: YES
Affected Deps: stripe-api

## SUGGESTED ACTIONS
  - [HIGH] rollback_deployment: Rollback to previous version (NEEDS APPROVAL)
  - [MEDIUM] enable_circuit_breakers: Circuit break stripe-api
```

## Zero-Code Mode (JavaAgent Extension)

For applications where you cannot add a library dependency, use the javaagent extension. No code changes, no Spring dependency:

```bash
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.javaagent.extensions=agenttel-javaagent-extension.jar \
     -Dagenttel.config.file=agenttel.yml \
     -jar myapp.jar
```

The extension reads configuration from `agenttel.yml` (same YAML format as above), system properties (`-Dagenttel.topology.team=payments`), or environment variables (`AGENTTEL_TOPOLOGY_TEAM=payments`).

## Examples

| Example | Description | Run Command |
|---------|-------------|-------------|
| [Spring Boot Example](https://github.com/rrohitramsen/AgentTel/tree/main/examples/spring-boot-example) | Payment service with span enrichment, topology, baselines, anomaly detection, and MCP server | `./gradlew :examples:spring-boot-example:bootRun` |
| [LangChain4j Example](https://github.com/rrohitramsen/AgentTel/tree/main/examples/langchain4j-example) | GenAI tracing with LangChain4j — chat spans, token tracking, and cost calculation | `./gradlew :examples:langchain4j-example:run` |

## Next Steps

- [Architecture](../concepts/architecture.md) — understand how AgentTel works under the hood
- [Agent Layer](../guides/agent-layer.md) — deep dive into MCP server, incidents, and remediation
- [API Reference](../reference/api-reference.md) — full configuration and annotation reference
