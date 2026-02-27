# AgentTel — Implementation Roadmap

> **Phased build plan to ship AgentTel from zero to production-ready.**

---

## Overview

The roadmap is structured in **4 phases** designed to ship incrementally — each phase produces a usable, release-worthy artifact.

| Phase | Name | Duration | Output |
|-------|------|----------|--------|
| **Phase 1** | Foundation | 2–3 weeks | `agenttel-api` + `agenttel-core` + basic Spring Boot starter |
| **Phase 2** | GenAI Instrumentation | 2–3 weeks | `agenttel-genai` with Spring AI + LangChain4j |
| **Phase 3** | Intelligence | 2–3 weeks | Rolling baselines, anomaly detection, structured events |
| **Phase 4** | Ecosystem | Ongoing | Quarkus/Micronaut support, Micrometer bridge, docs, community |

---

## Phase 1: Foundation (v0.1.0-alpha)

> **Goal:** Ship the core library with annotations, static baselines, topology declarations, and Spring Boot auto-configuration.

### Tasks

#### 1.1 Project Scaffolding
- [ ] Initialize Gradle multi-module project (Kotlin DSL)
- [ ] Set up version catalog (`libs.versions.toml`)
- [ ] Configure OTel SDK dependencies (BOM alignment)
- [ ] Set up CI (GitHub Actions: build, test, lint)
- [ ] Configure publishing to Maven Central (Sonatype OSSRH)
- [ ] Apache 2.0 license, CONTRIBUTING.md, CODE_OF_CONDUCT.md

#### 1.2 `agenttel-api` Module
- [ ] Define all annotation types:
  - `@AgentObservable` (class-level)
  - `@DeclareDependency` / `@DeclareDependencies` (class-level, repeatable)
  - `@DeclareConsumer` / `@DeclareConsumers` (class-level, repeatable)
  - `@AgentOperation` (method-level)
- [ ] Define enums: `ServiceTier`, `DependencyType`, `DependencyCriticality`, `EscalationLevel`, `CauseCategory`, `ImpactScope`, `BusinessImpact`
- [ ] Define `AgentTelAttributes` class with all `AttributeKey` constants
- [ ] Define event name constants
- [ ] Zero external dependencies — JDK 17 only
- [ ] Unit tests for annotation validation

#### 1.3 `agenttel-core` Module
- [ ] `TopologyRegistry` — reads `@DeclareDependency` annotations, stores dependency graph, serializes to JSON for Resource attributes
- [ ] `StaticBaselineProvider` — reads `@AgentOperation` annotations for baselines
- [ ] `SpanEnrichmentProcessor` — OTel `SpanProcessor` that:
  - Attaches topology attributes
  - Attaches static baselines
  - Attaches decision metadata (retryable, runbook, etc.)
- [ ] `AgentTelResourceProvider` — OTel `ResourceProvider` SPI that:
  - Sets `agenttel.topology.*` Resource attributes
  - Serializes dependency/consumer declarations
- [ ] `AgentTelEventEmitter` — wraps OTel Logger to emit structured events
- [ ] `DeploymentEventEmitter` — emits `agenttel.deployment.info` on startup
- [ ] Integration tests with `InMemorySpanExporter`

#### 1.4 `agenttel-spring-boot-starter` Module
- [ ] `AgentTelAutoConfiguration` — wires engine with Spring Boot
- [ ] `AgentTelProperties` — `@ConfigurationProperties` for `application.yml`
- [ ] Annotation scanning via `BeanPostProcessor`
- [ ] AOP-based `@AgentOperation` interceptor
- [ ] Health indicator for topology status
- [ ] Integration test with embedded Spring Boot app

#### 1.5 Example Application
- [ ] `spring-boot-example/` — simple REST API demonstrating:
  - Service with declared dependencies
  - Endpoints with `@AgentOperation`
  - Deployment event on startup
  - Docker Compose with OTel Collector + Jaeger for visualization

### Phase 1 Deliverables
- Published Maven artifacts: `agenttel-api`, `agenttel-core`, `agenttel-spring-boot-starter`
- Working example application
- README with quickstart guide

---

## Phase 2: GenAI Instrumentation (v0.2.0-alpha)

> **Goal:** Enrich existing JVM GenAI observability with agent-ready context, and provide full instrumentation where none exists today.

### Tasks

#### 2.1 GenAI Convention Implementation
- [ ] Define `GenAiAttributes` class implementing `gen_ai.*` OTel semconv in Java
- [ ] Define `AgentTelGenAiAttributes` for `agenttel.genai.*` extensions
- [ ] Create span/event builders following OTel GenAI semconv exactly
- [ ] Map operation names: `chat`, `text_completion`, `embeddings`, etc.

#### 2.2 Spring AI Enrichment (NOT replacement)
- [ ] `AgentTelSpringAiEnricher` — `SpanProcessor` that detects Spring AI's existing Micrometer-generated `gen_ai.client.operation` spans and enriches them with:
  - `agenttel.genai.cost_usd` (calculated from model + tokens)
  - `agenttel.genai.prompt_template_id/version`
  - `agenttel.genai.rag_source_count` and relevance scores
  - `agenttel.genai.guardrail_triggered/name`
  - `agenttel.genai.cache_hit`
  - Standard AgentTel baseline/decision attributes
- [ ] Support for streaming responses
- [ ] Auto-configuration that detects Spring AI on classpath
- [ ] **Does NOT replace** Spring AI's built-in `gen_ai.*` tracing — enriches it

#### 2.3 LangChain4j Instrumentation (Full — filling the gap)
- [ ] Evaluate community `otel-genai-bridges` project for potential collaboration or lessons learned
- [ ] `AgentTelChatLanguageModelWrapper` — wraps `ChatLanguageModel`
- [ ] `AgentTelEmbeddingModelWrapper` — wraps `EmbeddingModel`
- [ ] `AgentTelContentRetrieverWrapper` — wraps RAG retrievers
  - Captures `agenttel.genai.rag_source_count` and relevance scores
- [ ] `AgentTelAiServicesInterceptor` — instruments `@AiService` proxies

#### 2.4 Provider SDK Instrumentation
- [ ] Anthropic Java SDK: intercept `MessageCreateParams` / `MessageResponse`
- [ ] OpenAI Java SDK: intercept `ChatCompletionCreateParams` / `ChatCompletion`
- [ ] AWS Bedrock SDK: intercept `InvokeModelRequest` / `InvokeModelResponse`
- [ ] Cost calculation based on model + token count

#### 2.5 Example Application
- [ ] `spring-ai-example/` — RAG-based customer support agent demonstrating:
  - Chat endpoint with Spring AI
  - RAG with vector store
  - Full GenAI telemetry in Jaeger
  - Token usage and cost tracking

### Phase 2 Deliverables
- Published `agenttel-genai` module
- Spring AI + LangChain4j auto-instrumentation
- Anthropic / OpenAI / Bedrock SDK instrumentation
- GenAI example application

---

## Phase 3: Intelligence (v0.3.0-alpha)

> **Goal:** Add rolling baselines, lightweight anomaly detection, causal tracking, and rich structured events.

### Tasks

#### 3.1 Rolling Baselines
- [ ] `RollingBaselineProvider` — maintains sliding window statistics:
  - P50, P95, P99, mean, stddev per operation
  - Configurable window (default: 7 days)
  - Lock-free concurrent data structure (ring buffer)
- [ ] `CompositeBaselineProvider` — static > rolling > default fallback chain
- [ ] Baseline persistence (optional — save/restore from file on restart)
- [ ] `agenttel.baseline.source` attribute set automatically

#### 3.2 Anomaly Detection
- [ ] `ZScoreAnomalyDetector` — compares current values against rolling baselines:
  - Configurable threshold (default Z > 3.0)
  - Produces `anomaly_score` (0.0–1.0)
- [ ] `PatternMatcher` — matches against known incident patterns:
  - `cascade-failure` — multiple dependencies failing simultaneously
  - `memory-leak` — monotonically increasing memory usage
  - `thundering-herd` — sudden traffic spike after recovery
  - `cold-start` — elevated latency after deployment
  - Extensible: users can register custom patterns
- [ ] Anomaly event emission with `agenttel.anomaly.detected`
- [ ] Configurable anomaly event sampling rate

#### 3.3 Causal Tracking
- [ ] `DependencyHealthTracker` — monitors dependency call success/failure rates:
  - Maintains per-dependency state: `healthy`, `degraded`, `unhealthy`
  - Emits `agenttel.dependency.state_change` events
- [ ] `CausalHintEnricher` — enriches error spans with:
  - Dependency name if a dependency is in degraded state
  - Deployment info if a recent deployment occurred
  - Correlated span IDs where available
- [ ] Circuit breaker state tracking (if Resilience4j / Spring Retry detected)
  - Emits `agenttel.circuit_breaker.state_change` events

#### 3.4 SLO Tracking
- [ ] `SloTracker` — tracks error budget consumption:
  - Configurable SLO targets via annotations or config
  - Emits `agenttel.slo.budget_alert` at configurable thresholds (50%, 25%, 10%)
  - Calculates burn rate

#### 3.5 Structured Event Enrichment
- [ ] All structured events follow the JSON schemas defined in semantic conventions
- [ ] Events include `suggested_actions` arrays where applicable
- [ ] Events carry correlation IDs linking to related spans

### Phase 3 Deliverables
- Rolling baselines + anomaly detection
- Causal tracking with dependency health monitoring
- SLO budget tracking
- Rich structured events with suggested actions

---

## Phase 4: Ecosystem (v0.4.0+ → v1.0.0)

> **Goal:** Broaden framework support, build community, stabilize conventions, and work toward a 1.0 release.

### Tasks

#### 4.1 Framework Expansion
- [ ] `agenttel-quarkus` — Quarkus extension with CDI-based wiring
- [ ] `agenttel-micronaut` — Micronaut module with AOP integration
- [ ] `agenttel-ktor` — Ktor plugin for Kotlin-native HTTP
- [ ] `agenttel-micrometer-bridge` — for apps using Micrometer without full OTel

#### 4.2 OTel Collector Processor (Optional)
- [ ] `agenttelprocessor` — OTel Collector processor (Go) that:
  - Validates AgentTel attributes on incoming telemetry
  - Can enrich telemetry with topology from an external registry
  - Formats agent-ready summaries for downstream AI agents

#### 4.3 OTel Community Engagement
- [ ] Propose `agenttel.topology.*` conventions to OTel Semantic Conventions SIG
- [ ] Propose `agenttel.baseline.*` and `agenttel.decision.*` conventions
- [ ] Contribute JVM GenAI instrumentation back to `opentelemetry-java-contrib`
- [ ] Present at KubeCon, OTel community meetings

#### 4.4 Documentation & Community
- [ ] Full documentation site (Docusaurus or MkDocs)
- [ ] Interactive examples and tutorials
- [ ] Grafana dashboard templates for AgentTel signals
- [ ] Integration guides for Datadog, Grafana, Splunk, Logz.io
- [ ] Blog posts and conference talks

#### 4.5 Stabilization
- [ ] Promote semantic conventions from Experimental → Stable
- [ ] API stability guarantees (no breaking changes post-1.0)
- [ ] Performance benchmarking suite
- [ ] Security audit
- [ ] Release v1.0.0

---

## Technology Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Build system** | Gradle (Kotlin DSL) | Standard for JVM open source; version catalog support |
| **Language** | Java 17 (API/core), Kotlin (tests/examples) | Maximum compatibility; Kotlin for ergonomics where it matters |
| **OTel SDK** | `opentelemetry-java` 1.55+ | Latest stable; required for GenAI semconv support |
| **Testing** | JUnit 5 + AssertJ + Testcontainers | Industry standard; Testcontainers for integration tests |
| **CI** | GitHub Actions | Free for open source; wide ecosystem |
| **Publishing** | Maven Central via Sonatype | Standard JVM artifact distribution |
| **License** | Apache 2.0 | Compatible with OTel; enterprise-friendly |
| **Min JDK** | 17 | Spring Boot 3+ requires 17; reasonable baseline in 2026 |

---

## Success Metrics

| Metric | Phase 1 Target | Phase 4 Target |
|--------|---------------|----------------|
| Maven Central downloads | 100/month | 10,000/month |
| GitHub stars | 50 | 1,000 |
| Contributors | 3 | 20+ |
| Framework integrations | Spring Boot | 4+ frameworks |
| GenAI libraries instrumented | 0 | 5+ |
| OTel semconv proposals accepted | 0 | 2+ |
| Production deployments | 1 (own) | 50+ |

---

## Getting Started with Development

### Prerequisites

```bash
# Required
java --version   # 17+
gradle --version # 8.5+

# Recommended for local testing
docker --version # For OTel Collector + Jaeger
```

### First Build

```bash
git clone https://github.com/agenttel/agenttel.git
cd agenttel
./gradlew build
```

### Running the Example

```bash
cd examples/spring-boot-example
docker-compose up -d    # Starts OTel Collector + Jaeger
./gradlew bootRun
# Visit http://localhost:8080 — then check Jaeger at http://localhost:16686
```

---

## What to Build First

If you're picking up this project and want to start coding immediately, here's the priority order:

1. **`agenttel-api` annotations + attributes** — this is the public API contract; get it right first
2. **`agenttel-core` TopologyRegistry + SpanEnrichmentProcessor** — the minimal viable engine
3. **`agenttel-spring-boot-starter` auto-config** — makes it immediately usable
4. **Example app with Docker Compose** — proves it works end-to-end
5. **`agenttel-genai` Spring AI instrumentation** — the most differentiated feature

Everything else builds on top of these five pieces.
