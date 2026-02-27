# AgentTel — API Quick Reference

> **Developer-facing API surface for the AgentTel library.**

---

## 1. Annotations

### Service-Level (on `@SpringBootApplication` or main class)

```java
@AgentObservable(
    service = "payment-service",       // Overrides otel service.name if set
    team = "payments-platform",        // → agenttel.topology.team
    tier = ServiceTier.CRITICAL,       // → agenttel.topology.tier
    domain = "commerce",               // → agenttel.topology.domain
    onCallChannel = "#payments-oncall" // → agenttel.topology.on_call_channel
)
@DeclareDependency(
    name = "payment-gateway",
    type = DependencyType.EXTERNAL_API,
    criticality = DependencyCriticality.REQUIRED,
    timeoutMs = 5000,
    circuitBreaker = true
)
@DeclareDependency(
    name = "postgres-orders",
    type = DependencyType.DATABASE,
    criticality = DependencyCriticality.REQUIRED
)
@DeclareConsumer(
    name = "notification-service",
    pattern = ConsumptionPattern.ASYNC
)
@SpringBootApplication
public class PaymentServiceApplication { }
```

### Operation-Level (on controller methods / service methods)

```java
@AgentOperation(
    // Baselines
    expectedLatencyP50 = "45ms",
    expectedLatencyP99 = "200ms",
    expectedErrorRate = 0.001,

    // Decision metadata
    retryable = true,
    idempotent = true,
    runbookUrl = "https://wiki/runbooks/process-payment",
    fallbackDescription = "Returns cached pricing from last successful gateway call",
    escalationLevel = EscalationLevel.PAGE_ONCALL,

    // Safety
    safeToRestart = false
)
@PostMapping("/pay")
public PaymentResult processPayment(@RequestBody PaymentRequest req) { ... }
```

---

## 2. Programmatic API

For cases where annotations aren't sufficient or you need dynamic values.

### Topology Registration

```java
@Component
public class DynamicTopologySetup implements AgentTelConfigurer {

    @Override
    public void configure(AgentTelEngine engine) {
        engine.topology()
            .declareDependency("feature-flag-service")
                .type(DependencyType.INTERNAL_SERVICE)
                .criticality(DependencyCriticality.OPTIONAL)
                .register();

        // Dynamic dependency discovered at runtime
        engine.topology()
            .declareDependency(paymentProvider.getName())
                .type(DependencyType.EXTERNAL_API)
                .criticality(DependencyCriticality.REQUIRED)
                .timeout(Duration.ofSeconds(5))
                .register();
    }
}
```

### Manual Span Enrichment

```java
@Service
public class PaymentService {

    private final AgentTelEngine agentTel;

    public PaymentResult processPayment(PaymentRequest req) {
        Span span = Span.current();

        // Add dynamic causal hint
        agentTel.enrichSpan(span, CausalHint.builder()
            .category(CauseCategory.DEPENDENCY_FAILURE)
            .dependency("payment-gateway")
            .hint("Gateway returning 503 since " + gatewayDegradedSince)
            .build());

        // Add dynamic decision metadata
        agentTel.enrichSpan(span, DecisionContext.builder()
            .knownIssueId("PAY-4521")
            .retryAfter(Duration.ofSeconds(30))
            .build());

        // ... business logic ...
    }
}
```

### Manual Event Emission

```java
// Emit a custom structured event
agentTel.events().emit(
    AgentTelEvent.anomalyDetected()
        .metric("payment_success_rate")
        .currentValue(0.85)
        .baselineValue(0.998)
        .anomalyScore(0.95)
        .probableCause(ProbableCause.builder()
            .category(CauseCategory.DEPENDENCY_FAILURE)
            .dependency("payment-gateway")
            .evidence("5xx rate at 15% over last 5 minutes")
            .build())
        .suggestAction("check_dependency_health", "payment-gateway")
        .suggestAction("activate_fallback", "Enable cached pricing")
        .suggestAction("notify_team", "#payments-oncall")
        .build()
);

// Emit dependency state change
agentTel.events().emit(
    AgentTelEvent.dependencyStateChange()
        .dependency("payment-gateway")
        .previousState(DependencyState.HEALTHY)
        .newState(DependencyState.DEGRADED)
        .evidence("5xx rate exceeded 10% threshold over 60s window")
        .affectedOperations("/pay", "/refund")
        .build()
);
```

### Dependency Health Reporting

```java
// Report dependency health (can be called from health checks, circuit breakers, etc.)
agentTel.dependencies().reportHealth("payment-gateway", HealthStatus.DEGRADED,
    "Connection pool at 95% capacity, p99 latency at 8s");

agentTel.dependencies().reportHealth("payment-gateway", HealthStatus.UNHEALTHY,
    "All connections timed out");

agentTel.dependencies().reportHealth("payment-gateway", HealthStatus.HEALTHY,
    "Recovery confirmed — error rate back to 0.1%");
```

---

## 3. Configuration

### Spring Boot `application.yml`

```yaml
agenttel:
  enabled: true                          # Master switch (default: true)

  # Topology (can also be set via annotations — config overrides annotations)
  topology:
    team: "payments-platform"
    tier: critical                        # critical | standard | internal | experimental
    domain: "commerce"
    on-call-channel: "#payments-oncall"
    repo-url: "https://github.com/myorg/payment-service"

  # Dependencies
  dependencies:
    - name: payment-gateway
      type: external_api
      criticality: required
      timeout-ms: 5000
      circuit-breaker: true
      health-endpoint: "https://gateway.pay.com/health"
      fallback: "cached_response"
    - name: postgres-orders
      type: database
      criticality: required
    - name: redis-cache
      type: cache
      criticality: degraded

  # Consumers
  consumers:
    - name: notification-service
      pattern: async
      sla-latency-ms: 500

  # Baselines
  baselines:
    source: composite                    # static | rolling | composite (default: static)
    rolling:
      window: 7d                         # Rolling window duration
      min-samples: 100                   # Min samples before rolling baselines activate
      persistence:
        enabled: true                    # Save baselines to file on shutdown
        path: /tmp/agenttel-baselines.json

  # Anomaly Detection
  anomaly-detection:
    enabled: true                        # Default: true
    z-score-threshold: 3.0               # Default: 3.0
    event-sampling-rate: 1.0             # 0.0–1.0, default: 1.0 (emit all)
    patterns:
      cascade-failure: true
      memory-leak: true
      thundering-herd: true
      cold-start: true

  # SLO Tracking
  slo:
    enabled: false                       # Default: false
    targets:
      - name: "payment-availability"
        metric: availability
        target: 0.999
        window: 30d
        alert-thresholds: [0.5, 0.25, 0.1]  # Emit events at these budget remaining levels

  # GenAI
  genai:
    enabled: true                        # Default: true (if agenttel-genai on classpath)
    capture-message-content: false       # Default: false (privacy)
    cost-tracking: true                  # Calculate estimated cost per call
    prompt-template-tracking: true       # Track prompt template IDs

  # Deployment info (auto-detected if not set)
  deployment:
    emit-on-startup: true                # Emit agenttel.deployment.info event
    version: "${app.version}"            # Auto-detect from manifest if not set
    commit-sha: "${git.commit.id}"       # Auto-detect from git.properties if available
```

---

## 4. Kotlin DSL (Alternative API)

For Kotlin users, a more idiomatic DSL:

```kotlin
@SpringBootApplication
@AgentObservable(service = "payment-service")
class PaymentServiceApplication

fun main(args: Array<String>) {
    runApplication<PaymentServiceApplication>(*args) {
        agentTel {
            topology {
                team = "payments-platform"
                tier = ServiceTier.CRITICAL

                dependency("payment-gateway") {
                    type = DependencyType.EXTERNAL_API
                    criticality = DependencyCriticality.REQUIRED
                    timeoutMs = 5000
                    circuitBreaker = true
                }

                consumer("notification-service") {
                    pattern = ConsumptionPattern.ASYNC
                }
            }

            baselines {
                source = BaselineSource.COMPOSITE
                rollingWindow = 7.days
            }

            anomalyDetection {
                enabled = true
                zScoreThreshold = 3.0
            }
        }
    }
}
```

---

## 5. Enum Reference

```java
public enum ServiceTier {
    CRITICAL,       // Revenue-impacting, requires immediate response
    STANDARD,       // Important but not revenue-critical
    INTERNAL,       // Internal tooling, lower priority
    EXPERIMENTAL    // Experimental/canary services
}

public enum DependencyType {
    INTERNAL_SERVICE,
    EXTERNAL_API,
    DATABASE,
    MESSAGE_BROKER,
    CACHE,
    OBJECT_STORE,
    IDENTITY_PROVIDER
}

public enum DependencyCriticality {
    REQUIRED,       // Service cannot function without it
    DEGRADED,       // Service can function with reduced capability
    OPTIONAL        // Service can function normally without it
}

public enum EscalationLevel {
    AUTO_RESOLVE,       // Agent can handle it
    NOTIFY_TEAM,        // Send a notification, no urgency
    PAGE_ONCALL,        // Page the on-call engineer
    INCIDENT_COMMANDER  // This is a major incident
}

public enum CauseCategory {
    DEPENDENCY_FAILURE,
    RESOURCE_EXHAUSTION,
    CONFIG_CHANGE,
    DEPLOYMENT,
    TRAFFIC_SPIKE,
    DATA_QUALITY,
    UNKNOWN
}

public enum DependencyState {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    UNKNOWN
}

public enum ImpactScope {
    SINGLE_REQUEST,
    SINGLE_USER,
    SUBSET_USERS,
    ALL_USERS,
    MULTI_SERVICE
}

public enum BusinessImpact {
    NONE,
    DEGRADED_EXPERIENCE,
    FEATURE_UNAVAILABLE,
    REVENUE_IMPACTING,
    DATA_LOSS
}

public enum BaselineSource {
    STATIC,         // From annotations / config
    ROLLING_7D,     // Rolling 7-day window
    ML_MODEL,       // External ML model
    SLO             // From SLO definitions
}

public enum ConsumptionPattern {
    SYNC,           // Synchronous call
    ASYNC,          // Async messaging
    BATCH,          // Batch processing
    STREAMING       // Streaming consumption
}
```

---

## 6. Maven / Gradle Dependency

```xml
<!-- Maven -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.agenttel</groupId>
            <artifactId>agenttel-bom</artifactId>
            <version>0.1.0-alpha</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Core (always needed) -->
    <dependency>
        <groupId>io.agenttel</groupId>
        <artifactId>agenttel-spring-boot-starter</artifactId>
    </dependency>

    <!-- GenAI instrumentation (optional) -->
    <dependency>
        <groupId>io.agenttel</groupId>
        <artifactId>agenttel-genai</artifactId>
    </dependency>

    <!-- Testing (optional) -->
    <dependency>
        <groupId>io.agenttel</groupId>
        <artifactId>agenttel-testing</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

```kotlin
// Gradle (Kotlin DSL)
dependencies {
    implementation(platform("io.agenttel:agenttel-bom:0.1.0-alpha"))
    implementation("io.agenttel:agenttel-spring-boot-starter")
    implementation("io.agenttel:agenttel-genai")        // optional
    testImplementation("io.agenttel:agenttel-testing")   // optional
}
```
