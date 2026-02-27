# Changelog

All notable changes to AgentTel are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[0.1.0-alpha]: https://github.com/rrohitramsen/AgentTel/releases/tag/v0.1.0-alpha
