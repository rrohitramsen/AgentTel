# API Documentation

AgentTel provides three layers of API documentation: interactive REST endpoint docs via Swagger UI, MCP tool documentation, and aggregated Javadoc for the library API.

---

## Swagger UI (REST Endpoints)

The Spring Boot example ships with [springdoc-openapi](https://springdoc.org/), which auto-generates an OpenAPI 3.0 spec from Spring MVC annotations and serves an interactive Swagger UI.

### Running

```bash
./gradlew :examples:spring-boot-example:bootRun
```

### Endpoints

| URL | Description |
|-----|-------------|
| [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | Interactive Swagger UI |
| [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) | OpenAPI 3.0 JSON spec |
| [http://localhost:8080/v3/api-docs.yaml](http://localhost:8080/v3/api-docs.yaml) | OpenAPI 3.0 YAML spec |

The Swagger UI shows all REST endpoints with:
- Request/response schemas (with examples)
- AgentTel enrichment profiles referenced in operation descriptions
- A "Try it out" button for live testing

### Adding to Your Own Service

Add the springdoc dependency:

```kotlin
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
```

Annotate your controller methods with `@Operation`, `@ApiResponse`, `@Tag`, and your models with `@Schema` — see the [PaymentController](../examples/spring-boot-example/src/main/java/io/agenttel/example/PaymentController.java) for a working example.

---

## MCP Tool Documentation

The MCP server exposes an HTML documentation page at `/mcp/docs` that lists all registered tools with their parameters, example JSON-RPC requests, and curl commands.

### Running

Start the example application (the MCP server runs on port 8081 by default):

```bash
./gradlew :examples:spring-boot-example:bootRun
```

### Endpoints

| URL | Description |
|-----|-------------|
| [http://localhost:8081/mcp/docs](http://localhost:8081/mcp/docs) | HTML tool documentation |
| [http://localhost:8081/health](http://localhost:8081/health) | MCP server health check |
| POST [http://localhost:8081/mcp](http://localhost:8081/mcp) | JSON-RPC 2.0 endpoint |

The docs page is auto-generated from the registered `McpToolDefinition` objects, so it always reflects the actual tools available on the running server.

### Listing Tools via JSON-RPC

You can also list tools programmatically:

```bash
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

---

## Javadoc

Aggregated Javadoc for all library modules can be generated with a single Gradle command:

```bash
./gradlew aggregatedJavadoc
```

This produces a unified Javadoc site at `build/docs/javadoc/` covering all published modules:

- `io.agenttel.api` — Annotations, attribute constants, enums
- `io.agenttel.core` — Runtime engine, baselines, anomaly detection
- `io.agenttel.genai` — GenAI instrumentation wrappers
- `io.agenttel.agent` — MCP server, health, incidents, remediation
- `io.agenttel.extension` — JavaAgent extension
- `io.agenttel.spring` — Spring Boot auto-configuration
- `io.agenttel.testing` — Test utilities

Open `build/docs/javadoc/index.html` in a browser to view.

Individual module Javadoc JARs are also published to Maven Central with each release.
