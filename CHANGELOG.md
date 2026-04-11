# Changelog

All notable changes to AgentTel are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0-alpha] - 2026-03-20

### Added

**Go SDK (agenttel-go) — Full Feature Parity**

Core (`go.agenttel.dev/agenttel-go`):
- Semantic attributes (~60 constants) matching Java/Python SDKs
- All enums (`ServiceTier`, `ErrorCategory`, `DependencyType`, etc.)
- YAML configuration loader from `agenttel.yml`
- `AgentTelSpanProcessor` with baseline/anomaly/SLO enrichment
- Rolling baseline provider, composite baseline, z-score anomaly detection
- SLO tracker with error budget calculation
- Error classifier, topology registry, structured event emitter
- HTTP, Gin, and gRPC middleware

GenAI Instrumentation (sub-modules):
- `go.agenttel.dev/agenttel-go/genai/openai` — OpenAI SDK wrapper (sync + streaming)
- `go.agenttel.dev/agenttel-go/genai/anthropic` — Anthropic SDK wrapper (sync + streaming)
- `GenAiSpanBuilder` for consistent span creation with cost calculation

Agentic Observability:
- `AgenticTracer` — builder pattern API for agent tracing
- Invocation, step, tool call scopes with auto-closing
- Orchestration patterns: sequential, parallel, eval-loop

Agent Interface:
- MCP server (JSON-RPC 2.0 over HTTP) with tool registry
- Bearer token auth with API key registry and RBAC
- Health aggregation, incident context, remediation registry

**Node.js SDK (@agenttel/node) — Full Feature Parity**

Core (`@agenttel/node`):
- Semantic attributes (~60 constants) matching Java/Python/Go SDKs
- All enums with TypeScript type safety
- YAML configuration loader from `agenttel.yml`
- `AgentTelSpanProcessor` with baseline/anomaly/SLO enrichment
- Rolling baseline provider, composite baseline, z-score anomaly detection
- SLO tracker with error budget calculation
- Error classifier, topology registry, structured event emitter
- Express and Fastify middleware

GenAI Instrumentation:
- OpenAI SDK wrapper (sync + streaming)
- Anthropic SDK wrapper (sync + streaming)
- `GenAiSpanBuilder` for consistent span creation with cost calculation

Agentic Observability:
- `AgenticTracer` — builder pattern API for agent tracing
- Invocation, step, tool call scopes
- Orchestration patterns: sequential, parallel, eval-loop

Agent Interface:
- MCP server (JSON-RPC 2.0 over HTTP) with tool registry
- Bearer token auth with API key registry and RBAC

**Shared Types (@agenttel/types)**
- Canonical TypeScript attribute constants, enums, and interfaces
- Dual ESM/CJS build for use by @agenttel/node and browser SDK

**Cross-SDK Improvements**
- `attributes.json` — canonical attribute schema (single source of truth)
- `scripts/validate-attributes.sh` — CI parity enforcement across all 4 SDKs
- Unified CI pipeline: Java (JDK 17/21), Go (1.24), Node (20), Python (3.11/3.12/3.13)
- MCP server auth (Bearer token + RBAC) added to Java SDK
- Example apps: Go net/http, Gin, Express, Fastify, FastAPI, Spring Boot

**Python SDK (agenttel-python) — Full Feature Parity**

Core (`agenttel`):
- Semantic attributes (~60 constants) matching `AgentTelAttributes.java`
- All enums (`ServiceTier`, `ErrorCategory`, `DependencyType`, etc.)
- Pydantic data models (`OperationBaseline`, `DependencyDescriptor`, `SloStatus`, etc.)
- YAML configuration loader from `agenttel.yml`
- `AgentTelSpanProcessor` — OTel SpanProcessor with baseline/anomaly/SLO enrichment
- `AgentTelEngine` — high-level orchestrator wiring all components
- Rolling baseline provider with thread-safe ring buffer
- Composite baseline provider (rolling + static with priority ordering)
- Z-score anomaly detection with pattern matching (latency degradation, error spike, cascade failure)
- SLO tracker with error budget calculation and alert levels
- Error classifier (exception type + message pattern matching)
- Causality tracker with dependency state changes and causal chain analysis
- Topology registry for service metadata
- Structured event emitter

FastAPI Integration (`agenttel[fastapi]`):
- `instrument_fastapi()` — one-line FastAPI integration
- `AgentTelMiddleware` — route resolution, topology, operation context
- `@agent_operation` decorator for per-endpoint configuration
- Auto-configuration from `agenttel.yml`

GenAI Instrumentation (`agenttel[openai,anthropic,langchain,bedrock]`):
- OpenAI SDK wrapper (sync + streaming)
- Anthropic SDK wrapper (sync + streaming)
- LangChain callback handler (ChatModel, Retriever)
- AWS Bedrock wrapper (`invoke_model` + `converse`)
- `ModelCostCalculator` with built-in pricing for 20+ models
- `GenAiSpanBuilder` for consistent span creation

Agent Interface (`agenttel[agent]`):
- MCP server (JSON-RPC 2.0 over HTTP) with 15 built-in tools
- `ServiceHealthAggregator` — per-operation stats and dependency health
- `AgentIdentity` and `ToolPermissionRegistry` — role-based access control
- `IncidentContextBuilder` — aggregated incident context for LLM consumption
- `RemediationRegistry` and `RemediationExecutor` — with audit logging
- `SloReportGenerator` and `TrendAnalyzer`

Agentic Observability (`agenttel`):
- 70+ agentic semantic attributes
- `AgentTracer` — builder pattern API for agent tracing
- 10 scope context managers: invocation, step, tool call, task, handoff, human checkpoint, code execution, evaluation, retriever, reranker
- Orchestration patterns: sequential, parallel, eval-loop
- `AgentCostAggregator` — token usage and cost tracking
- `LoopDetector` and `QualityTracker` — quality metrics
- `GuardrailRecorder` — guardrail trigger recording

## [0.2.0-alpha] - 2026-03-10

### Added

**Agentic Observability (agenttel-agentic)**
- `AgentTracer` for tracing agent invocations with full lifecycle support (steps, tool calls, tasks, handoffs)
- `AgentInvocation` with auto-closing scope, step counting, loop detection, and cost aggregation
- `AgentType` enum (SINGLE, REACT, PLAN_AND_EXECUTE, MULTI_AGENT, CUSTOM)
- `StepType` enum (THOUGHT, OBSERVATION, ACTION, PLANNING, EVALUATION, REFLECTION)
- `AgentCostAggregator` SpanProcessor for rolling up GenAI token costs to parent agent spans
- `LoopDetector` with configurable threshold for detecting repetitive tool call patterns
- `AgentConfig` and `AgentConfigRegistry` for config-driven agent identity and guardrails
- `@AgentMethod` annotation for zero-code agent invocation wrapping via AOP
- `AgentMethodAspect` for automatic `AgentInvocation` scope management on annotated methods
- `AgenticAttributes` attribute keys for agent spans (agent.name, agent.type, agent.framework, etc.)
- `AgenticAssertions` fluent test assertions for agent span verification

**Config-Driven Agent Observability**
- YAML configuration for agent identity (`agenttel.agentic.agents.*`): type, framework, version, maxSteps, loopThreshold, costBudgetUsd
- Global agentic defaults (`agenttel.agentic.loop-threshold`, `agenttel.agentic.default-max-steps`)
- Config priority: YAML > `@AgentMethod` annotation > programmatic `AgentTracer.Builder` defaults
- `AgentTelAgenticAutoConfiguration` wiring config registry, tracer, and AOP aspect

**Spring Boot Starter Enhancements**
- `McpServer` exposed as a proper Spring bean for testability and injection
- `McpServer.getPort()` returns actual bound port (supports port 0 for random assignment)

### Fixed
- `PaymentServiceIntegrationTest` BindException when MCP port 8081 is already in use

## [0.1.0-alpha] - 2025-02-27

### Added

**Core Instrumentation (agenttel-api, agenttel-core)**
- `@AgentOperation` annotation for declaring operational semantics (baselines, retryability, runbooks)
- `@AgentObservable`, `@DeclareDependency`, `@DeclareConsumer` annotations for service topology
- `AgentTelSpanProcessor` enriching spans with topology, baseline, decision, anomaly, and SLO attributes
- `TopologyRegistry` for dependency graph management with JSON serialization
- `StaticBaselineProvider` from annotation metadata
- `RollingBaselineProvider` with lock-free ring buffer sliding windows (configurable window size and min samples)
- `CompositeBaselineProvider` chaining static, rolling, and default baselines
- `AnomalyDetector` with z-score based deviation detection
- `PatternMatcher` detecting CASCADE_FAILURE, LATENCY_DEGRADATION, ERROR_RATE_SPIKE, and MEMORY_LEAK patterns
- `SloTracker` with error budget consumption tracking and threshold alerting (50%, 25%, 10%)
- `AgentTelEventEmitter` for structured events via the OTel Logs API
- `DeploymentEventEmitter` for deployment tracking

**GenAI Instrumentation (agenttel-genai)**
- LangChain4j instrumentation: `TracingChatLanguageModel`, `TracingStreamingChatLanguageModel`, `TracingEmbeddingModel`, `TracingContentRetriever`
- Spring AI span enrichment via `SpringAiSpanEnricher`
- `CostEnrichingSpanExporter` for adding cost_usd at export time
- Anthropic Java SDK instrumentation: `TracingAnthropicClient`
- OpenAI Java SDK instrumentation: `TracingOpenAIClient`
- AWS Bedrock SDK instrumentation: `TracingBedrockRuntimeClient`
- `ModelCostCalculator` with pricing for Claude, GPT-4, GPT-4o, and Bedrock models
- Spring Boot auto-configuration with `@ConditionalOnClass` activation

**Agent Interface Layer (agenttel-agent)**
- `McpServer` implementing Model Context Protocol over JSON-RPC 2.0
- `AgentTelMcpServerBuilder` with 5 pre-registered tools (get_service_health, get_incident_context, list_remediation_actions, execute_remediation, get_recent_agent_actions)
- `ServiceHealthAggregator` for real-time health from span data
- `IncidentContext` / `IncidentContextBuilder` for structured incident packages (what's happening, what changed, what's affected, what to do)
- `RemediationAction` / `RemediationRegistry` / `RemediationExecutor` with approval workflows
- `AgentActionTracker` recording agent decisions and actions as OTel spans
- `ContextFormatter` with prompt-optimized output formats (compact, full, JSON)
- `AgentContextProvider` as single entry point for agent queries

**Spring Boot Integration (agenttel-spring-boot-starter)**
- Auto-configuration for all core components
- `@ConfigurationProperties` binding for agenttel.* properties
- `AgentTelAnnotationBeanPostProcessor` for annotation scanning
- `AgentTelHealthIndicator` for Actuator integration

**Infrastructure**
- CI/CD with GitHub Actions (JDK 17 + 21 matrix)
- Maven Central publishing pipeline
- Grafana dashboard templates (overview + GenAI)
- Spring Boot and LangChain4j example applications

[0.2.0-alpha]: https://github.com/agenttel/agenttel-sdk/releases/tag/v0.2.0-alpha
[0.1.0-alpha]: https://github.com/agenttel/agenttel-sdk/releases/tag/v0.1.0-alpha
