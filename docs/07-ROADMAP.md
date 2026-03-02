# Roadmap

AgentTel follows a phased release plan, building from core instrumentation through GenAI support to a complete agent interface layer.

---

## Release History

### v0.1.0-alpha — Foundation + GenAI + Agent Layer

**Status: Implemented**

#### Phase 1: Foundation

Core instrumentation and span enrichment.

- [x] Gradle multi-module project setup (Java 17+)
- [x] `agenttel-api` — Annotations (`@AgentOperation`, `@AgentObservable`, `@DeclareDependency`), attribute constants, enums
- [x] `agenttel-core` — `AgentTelSpanProcessor` with topology, baseline, and decision enrichment
- [x] `agenttel-core` — `TopologyRegistry` with dependency graph serialization
- [x] `agenttel-core` — `StaticBaselineProvider` from annotation metadata
- [x] `agenttel-core` — `RollingBaselineProvider` with lock-free ring buffer sliding windows
- [x] `agenttel-core` — `CompositeBaselineProvider` chaining static → rolling → default
- [x] `agenttel-core` — `AnomalyDetector` with z-score based deviation detection
- [x] `agenttel-core` — `PatternMatcher` detecting cascade failures, latency degradation, error rate spikes, memory leaks
- [x] `agenttel-core` — `SloTracker` with error budget consumption and threshold alerting
- [x] `agenttel-core` — `AgentTelEventEmitter` for structured events via OTel Logs API
- [x] `agenttel-spring-boot-starter` — Auto-configuration with `@ConfigurationProperties`
- [x] `agenttel-testing` — Test utilities
- [x] CI/CD with GitHub Actions (JDK 17 + 21 matrix)
- [x] Grafana dashboard templates (overview + GenAI)

#### Phase 2: GenAI Instrumentation

Observability for AI/ML workloads on the JVM.

- [x] `agenttel-genai` — `gen_ai.*` and `agenttel.genai.*` attribute constants
- [x] `agenttel-genai` — `ModelCostCalculator` with per-model pricing for Claude, GPT, and Bedrock models
- [x] `agenttel-genai` — `SpringAiSpanEnricher` enriching existing Micrometer spans
- [x] `agenttel-genai` — `CostEnrichingSpanExporter` with delegating `SpanData` pattern
- [x] `agenttel-genai` — LangChain4j instrumentation: `TracingChatLanguageModel`, `TracingStreamingChatLanguageModel`, `TracingEmbeddingModel`, `TracingContentRetriever`
- [x] `agenttel-genai` — Anthropic SDK instrumentation: `TracingAnthropicClient`
- [x] `agenttel-genai` — OpenAI SDK instrumentation: `TracingOpenAIClient`
- [x] `agenttel-genai` — AWS Bedrock instrumentation: `TracingBedrockRuntimeClient`
- [x] Spring Boot auto-configuration with `@ConditionalOnClass` detection
- [x] LangChain4j example application

#### Phase 3: Agent Interface Layer

Complete toolkit for AI agent interaction with production systems.

- [x] `agenttel-agent` — `ServiceHealthAggregator` for real-time health from span data
- [x] `agenttel-agent` — `IncidentContext` / `IncidentContextBuilder` for structured incident packages
- [x] `agenttel-agent` — `RemediationAction` / `RemediationRegistry` / `RemediationExecutor` for agent-executable fixes
- [x] `agenttel-agent` — `AgentActionTracker` recording agent decisions as OTel spans
- [x] `agenttel-agent` — `ContextFormatter` with prompt-optimized output formats (compact, full, JSON)
- [x] `agenttel-agent` — `AgentContextProvider` as single entry point for agent queries
- [x] `agenttel-agent` — `McpServer` implementing Model Context Protocol over JSON-RPC
- [x] `agenttel-agent` — `AgentTelMcpServerBuilder` with 5 pre-registered tools
- [x] Full test coverage for all agent layer components

#### Phase 4: Frontend Telemetry & Tooling

Full-stack telemetry and developer experience.

- [x] `agenttel-web` — TypeScript browser SDK (`@agenttel/web`) with auto-instrumentation of page loads, SPA navigation, API calls, clicks, and errors
- [x] `agenttel-web` — Journey tracking with multi-step funnel detection, completion rates, and abandonment analysis
- [x] `agenttel-web` — Client-side anomaly detection: rage clicks, API failure cascades, slow page loads, error loops, funnel drop-offs
- [x] `agenttel-web` — W3C Trace Context injection and backend trace ID correlation for cross-stack linking
- [x] `agenttel-web` — Per-route baselines and decision metadata (escalation levels, runbooks, fallbacks)
- [x] `agenttel-web` — React checkout example application
- [x] `agenttel-instrument` — Python MCP server for IDE-based instrumentation automation
- [x] `agenttel-instrument` — `analyze_codebase` tool for Java/Spring Boot source scanning
- [x] `agenttel-instrument` — `instrument_backend` and `instrument_frontend` config generation
- [x] `agenttel-instrument` — `validate_instrumentation` with source code cross-referencing
- [x] `agenttel-instrument` — `suggest_improvements` and `apply_improvements` with live health data integration
- [x] `agenttel-agent` — `TrendAnalyzer` for operation metric trend analysis
- [x] `agenttel-agent` — `SloReportGenerator` for LLM-optimized SLO compliance reports
- [x] `agenttel-agent` — `ExecutiveSummaryBuilder` for high-level service status summaries
- [x] `agenttel-agent` — `CrossStackContextBuilder` for correlated frontend-backend context

---

## Planned: v0.2.0-alpha

### Agent Layer Enhancements

- [ ] MCP Server authentication (API key, mTLS)
- [ ] Server-Sent Events (SSE) transport for real-time streaming
- [ ] WebSocket transport option
- [ ] Configurable tool permissions per agent identity
- [ ] Rate limiting on MCP tool invocations
- [ ] Remediation action execution hooks (webhook, Kubernetes API, AWS API)

### Observability Enhancements

- [ ] Causality tracking — linking anomalies to recent deployments and config changes
- [ ] Dependency health state machine with transition events
- [ ] Multi-service correlation — cross-service incident grouping
- [ ] Custom metric emission alongside span enrichment

### GenAI Enhancements

- [ ] Prompt/response content capture (opt-in, with PII redaction)
- [ ] Token budget tracking and alerting
- [ ] Model performance comparison spans
- [ ] Guardrail integration for content safety monitoring

---

## Planned: v0.3.0-beta

### Framework Ecosystem

- [ ] Quarkus extension (`agenttel-quarkus`)
- [ ] Micronaut module (`agenttel-micronaut`)
- [ ] Ktor plugin (`agenttel-ktor`)
- [ ] Kotlin DSL for idiomatic Kotlin configuration

### Infrastructure Integration

- [ ] OTel Collector processor (Go) for server-side enrichment and validation
- [ ] Kubernetes operator for automatic MCP server deployment
- [ ] Helm chart for MCP server sidecar pattern

### Testing and Validation

- [ ] Performance benchmarks (enrichment overhead per span)
- [ ] Load testing with sustained high throughput
- [ ] Security audit of MCP server

---

## Planned: v1.0.0

### Stability

- [ ] API stability guarantee — no breaking changes without major version bump
- [ ] Semantic versioning commitment
- [ ] Long-term support (LTS) release

### Standards

- [ ] OpenTelemetry community proposal for `agenttel.*` conventions
- [ ] OTel GenAI SIG collaboration on `gen_ai.*` attribute stabilization
- [ ] CNCF project submission

### Documentation

- [ ] Interactive tutorial / playground
- [ ] Video walkthroughs
- [ ] Migration guides from existing instrumentation
- [ ] Production deployment guide with reference architectures

### Ecosystem

- [ ] Grafana plugin for AgentTel-specific visualizations
- [ ] VS Code extension for `@AgentOperation` annotation assistance
- [x] ~~CLI tool for validating AgentTel configuration~~ — implemented as `agenttel-instrument` MCP server with `validate_instrumentation` and `suggest_improvements` tools

---

## Compatibility Matrix

**Backend (JVM)**

| AgentTel Version | Java | OTel SDK | Spring Boot | LangChain4j | Spring AI |
|-----------------|------|----------|-------------|-------------|-----------|
| 0.1.0-alpha | 17, 21 | 1.59.0+ | 3.4.x | 1.0.0+ | 1.0.0+ |
| 0.2.0-alpha (planned) | 17, 21 | 1.60.0+ | 3.4.x, 3.5.x | 1.0.0+ | 1.0.0+ |
| 0.3.0-beta (planned) | 17, 21, 25 | 1.62.0+ | 3.x | 1.x | 1.x |
| 1.0.0 (planned) | 17+ | 1.x | 3.x | 1.x | 1.x |

**Frontend (Browser)**

| AgentTel Version | TypeScript | Browsers |
|-----------------|------------|----------|
| 0.1.0-alpha | 4.7+ | Chrome, Firefox, Safari, Edge (ES2020+) |

**Tooling**

| AgentTel Version | Python (instrument agent) |
|-----------------|---------------------------|
| 0.1.0-alpha | 3.11+ |
