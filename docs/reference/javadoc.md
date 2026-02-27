# Javadoc

## Generating Javadoc

Aggregated Javadoc for all library modules can be generated with:

```bash
./gradlew aggregatedJavadoc
```

This produces a unified Javadoc site at `build/docs/javadoc/` covering all published modules.

Open `build/docs/javadoc/index.html` in a browser to view.

## Packages

| Package | Module | Description |
|---------|--------|-------------|
| `io.agenttel.api` | agenttel-api | Annotations (`@AgentOperation`, `@AgentObservable`), attribute constants, enums |
| `io.agenttel.api.annotations` | agenttel-api | Annotation types for service and operation metadata |
| `io.agenttel.core` | agenttel-core | Runtime engine, baselines, anomaly detection, SLO tracking |
| `io.agenttel.core.enrichment` | agenttel-core | Span enrichment via `AgentTelSpanProcessor` |
| `io.agenttel.core.baseline` | agenttel-core | Static and rolling baseline providers |
| `io.agenttel.core.anomaly` | agenttel-core | Z-score anomaly detection |
| `io.agenttel.core.slo` | agenttel-core | SLO budget tracking |
| `io.agenttel.genai` | agenttel-genai | GenAI instrumentation wrappers |
| `io.agenttel.agent.mcp` | agenttel-agent | MCP server and tool definitions |
| `io.agenttel.agent.context` | agenttel-agent | Agent context providers |
| `io.agenttel.agent.health` | agenttel-agent | Service health aggregation |
| `io.agenttel.agent.incident` | agenttel-agent | Incident context building |
| `io.agenttel.agent.remediation` | agenttel-agent | Remediation action framework |
| `io.agenttel.extension` | agenttel-javaagent-extension | OTel javaagent extension |
| `io.agenttel.spring.autoconfigure` | agenttel-spring-boot-starter | Spring Boot auto-configuration |
| `io.agenttel.testing` | agenttel-testing | Test utilities |

## Per-Module JARs

Individual Javadoc JARs are published alongside each module artifact to Maven Central. Your IDE can download them automatically for inline documentation.
