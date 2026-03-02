# Frequently Asked Questions

**Does AgentTel require code changes?**
No. All enrichment can be driven entirely from YAML configuration (`application.yml` for Spring Boot, or `agenttel.yml` for the javaagent extension). Annotations like `@AgentOperation` are optional and only needed when you want IDE autocomplete and compile-time validation.

**How does this differ from Datadog/New Relic agents?**
Vendor agents collect metrics, traces, and logs. AgentTel enriches telemetry with *agent-actionable context* — baselines (what "normal" looks like), decision metadata (retryable, runbook URL, escalation level), and topology (who owns this, what depends on what). This is the structured context AI agents need to autonomously reason about and resolve incidents, not just observe them.

**Will this increase my telemetry size?**
Topology attributes are set once on the OTel Resource (not repeated on every span). Baselines and decision metadata add ~300-500 bytes per enriched operation span. Only operations with registered metadata are enriched — internal framework spans (Spring dispatcher, Tomcat, etc.) are not affected.

**Can I use this without Spring Boot?**
Yes. The javaagent extension works with any JVM application — no Spring dependency. The core library can also be used programmatically via `AgentTelEngine.builder()` for custom integration.

**What's the performance overhead?**
Span enrichment adds sub-millisecond latency per span (hash map lookups). Rolling baselines and anomaly detection use O(1) sliding windows. The library is designed for high-throughput production use.

**Does it work with my existing OTel setup?**
Yes. AgentTel registers as a standard OTel `SpanProcessor` and `ResourceProvider`. It is fully compatible with any OTel-compatible backend (Jaeger, Grafana Tempo, Datadog, New Relic, Honeycomb, etc.) and does not modify existing spans — it only adds attributes.

**What are operation profiles?**
Predefined sets of operational defaults (retry policy, escalation level, etc.) that reduce config repetition. Define a profile once, reference it from multiple operations. Per-operation values override profile defaults.

**How does YAML config interact with annotations?**
YAML config takes priority. When both YAML config and `@AgentOperation` annotations define the same operation, the YAML values are used. Annotations fill in gaps for operations not defined in config.

**Does AgentTel support frontend/browser applications?**
Yes. The `@agenttel/web` npm package provides a TypeScript browser SDK that auto-instruments page loads, SPA navigation, API calls, clicks, and errors. It supports per-route baselines, journey tracking, client-side anomaly detection (rage clicks, error loops, API cascades), and W3C Trace Context for cross-stack correlation with your backend.

**How does cross-stack correlation work?**
The browser SDK injects a W3C `traceparent` header on all outgoing `fetch` and `XMLHttpRequest` calls. The backend picks up this trace context automatically (standard OTel behavior). The SDK also extracts backend trace IDs from response headers. This allows the `get_cross_stack_context` MCP tool to link a user's browser interaction all the way through to backend operations.

**What is the instrument agent?**
`agenttel-instrument` is a Python MCP server you run alongside your IDE (Cursor, Claude Code, VS Code). It exposes 7 tools that let your AI assistant analyze your codebase, generate AgentTel config, validate instrumentation, and auto-apply low-risk improvements — for both backend and frontend.

**What languages does AgentTel support?**
Backend: Java, Kotlin, Scala, and any JVM language (via the Spring Boot starter or javaagent extension). Frontend: TypeScript/JavaScript in the browser (via the `@agenttel/web` SDK). Tooling: The instrument agent is written in Python but works with any codebase.

**How many MCP tools are available?**
The backend MCP server provides 9 tools: `get_service_health`, `get_incident_context`, `list_remediation_actions`, `execute_remediation`, `get_recent_agent_actions`, `get_slo_report`, `get_executive_summary`, `get_trend_analysis`, and `get_cross_stack_context`. The instrument agent provides 7 additional tools for instrumentation automation.
