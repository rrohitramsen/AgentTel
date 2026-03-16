# Roadmap

What's been built, what's next, and where AgentTel is heading.

---

## Current Release: v0.3.0-alpha

Everything below is **implemented and available**.

### Python SDK (agenttel-python)

- Full feature parity with JVM SDK, packaged as `agenttel` on PyPI
- Core: semantic attributes, Pydantic models, YAML config, rolling baselines, anomaly detection, SLO tracking, error classification, causality tracking
- FastAPI integration: middleware, `@agent_operation` decorator, auto-configuration
- GenAI instrumentation: OpenAI, Anthropic, LangChain, AWS Bedrock wrappers with cost calculation
- Agent interface: MCP server (JSON-RPC 2.0) with 15 tools, RBAC, health aggregation, incident context, remediation
- Agentic observability: `AgentTracer` with 10 scope context managers, orchestration patterns, cost/quality tracking
- 74 tests across 9 test files

---

## Previous Release: v0.2.0-alpha

### Core Instrumentation

- Multi-module Gradle project (Java 17+, CI on JDK 17 + 21)
- `agenttel-api` — annotations (`@AgentOperation`, `@AgentObservable`, `@DeclareDependency`), attribute constants, enums
- `agenttel-core` — span enrichment with topology, baselines (static + rolling + composite), decisions, anomaly detection (z-score), pattern matching, SLO tracking, structured events via OTel Logs API
- `agenttel-spring-boot-starter` — auto-configuration, annotation scanning, AOP
- `agenttel-javaagent` — zero-code OTel javaagent extension (no Spring dependency)
- YAML config-driven operations with operation profiles
- Topology as OTel Resource attributes (set once per service)

### GenAI Instrumentation

- LangChain4j: tracing wrappers for chat, streaming, embedding, and RAG content retrieval
- Spring AI: span enrichment for existing Micrometer spans
- Anthropic SDK, OpenAI SDK, AWS Bedrock: tracing wrappers
- Per-model cost calculation (`gen_ai.usage.cost_usd`)
- Auto-configuration with `@ConditionalOnClass` detection

### Agent Interface (MCP Server)

- 15 MCP tools over JSON-RPC: health, incidents, remediation, SLO reports, trends, executive summaries, cross-stack context, playbooks, verification, error analysis, sessions
- `AgentContextProvider` as single entry point for all agent queries
- Structured incident packages (what's happening, what changed, what's affected, what to do)
- Remediation execution with action tracking as OTel spans
- Error classification into actionable categories (dependency_timeout, code_bug, rate_limited, etc.)
- Causality tracking with change correlation
- Machine-readable playbooks (cascade failure, error rate spike, latency degradation, memory leak)
- Action feedback loop with pre/post health verification

### Multi-Agent Support

- Role-based tool permissions (observer, diagnostician, remediator, admin)
- Agent identity extraction from HTTP headers and tool arguments
- Shared incident sessions with blackboard pattern
- Support for coordinator, parallel, swarm, and hierarchical orchestration patterns

### Frontend Telemetry

- `@agenttel/web` TypeScript browser SDK
- Auto-instrumentation: page loads, SPA navigation, API calls, clicks, errors
- Journey tracking with funnel detection and abandonment analysis
- Client-side anomaly detection: rage clicks, API failure cascades, slow loads, error loops
- W3C Trace Context cross-stack correlation
- Per-route baselines and decision metadata

### Developer Tooling

- `agenttel-instrument` — Python MCP server for IDE-based instrumentation
- Codebase analysis, config generation, validation, and auto-improvements
- `agenttel-testing` — in-memory collector, fluent assertions, JUnit 5 extension
- 274 tests across all modules

---

## Next: v0.2.0-alpha

### Agent Layer

- [ ] MCP Server authentication (API key, mTLS)
- [ ] Server-Sent Events (SSE) transport for real-time streaming
- [ ] WebSocket transport option
- [ ] Rate limiting on MCP tool invocations
- [ ] Remediation execution hooks (webhook, Kubernetes API, AWS API)

### Observability

- [ ] Dependency health state machine with transition events
- [ ] Multi-service correlation — cross-service incident grouping
- [ ] Custom metric emission alongside span enrichment

### GenAI

- [ ] Prompt/response content capture (opt-in, with PII redaction)
- [ ] Token budget tracking and alerting
- [ ] Model performance comparison spans
- [ ] Guardrail integration for content safety monitoring

---

## Planned: v0.3.0-beta

### Framework Ecosystem

- [ ] Quarkus extension
- [ ] Micronaut module
- [ ] Ktor plugin
- [ ] Kotlin DSL for idiomatic configuration

### Infrastructure

- [ ] OTel Collector processor (Go) for server-side enrichment
- [ ] Kubernetes operator for automatic MCP server deployment
- [ ] Helm chart for MCP server sidecar pattern

### Quality

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

### Documentation & Ecosystem

- [ ] Interactive tutorial / playground
- [ ] Video walkthroughs
- [ ] Migration guides from existing instrumentation
- [ ] Production deployment guide with reference architectures
- [ ] Grafana plugin for AgentTel-specific visualizations
- [ ] VS Code extension for annotation assistance

---

## Compatibility Matrix

**Backend (JVM)**

| AgentTel | Java | OTel SDK | Spring Boot | LangChain4j | Spring AI |
|----------|------|----------|-------------|-------------|-----------|
| 0.2.0-alpha | 17, 21 | 1.59.0+ | 3.4.x | 1.0.0+ | 1.0.0+ |
| 0.2.0-alpha | 17, 21 | 1.60.0+ | 3.4.x, 3.5.x | 1.0.0+ | 1.0.0+ |
| 0.3.0-beta | 17, 21, 25 | 1.62.0+ | 3.x | 1.x | 1.x |
| 1.0.0 | 17+ | 1.x | 3.x | 1.x | 1.x |

**Frontend (Browser)**

| AgentTel | TypeScript | Browsers |
|----------|------------|----------|
| 0.2.0-alpha | 4.7+ | Chrome, Firefox, Safari, Edge (ES2020+) |

**Backend (Python)**

| AgentTel | Python | OTel SDK | FastAPI |
|----------|--------|----------|---------|
| 0.3.0-alpha | 3.11+ | 1.20.0+ | 0.100.0+ |

**Tooling**

| AgentTel | Python (instrument agent) |
|----------|---------------------------|
| 0.2.0-alpha | 3.11+ |
