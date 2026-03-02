# Contributing to AgentTel

Thank you for your interest in contributing to AgentTel. This guide will help you get started.

## Development Setup

### Prerequisites

- JDK 17 or later (backend modules)
- Node.js 20+ and npm (frontend SDK — `agenttel-web`)
- Python 3.11+ (instrument agent — `agenttel-instrument`)
- Git

### Building

**Backend (JVM):**

```bash
git clone https://github.com/rrohitramsen/AgentTel.git
cd AgentTel
./gradlew clean build
```

**Frontend SDK:**

```bash
cd agenttel-web
npm install
npm run build
npm test
```

**Instrument Agent:**

```bash
cd agenttel-instrument
pip install -e .
```

### Running Tests

```bash
# All JVM tests
./gradlew test

# Specific module
./gradlew :agenttel-core:test
./gradlew :agenttel-agent:test

# Frontend SDK tests
cd agenttel-web && npm test

# Docker demo (integration)
cd examples/spring-boot-example
docker compose -f docker/docker-compose.yml up --build
```

### Project Structure

```
agenttel-api/                 # Annotations, attributes, enums (zero dependencies)
agenttel-core/                # Runtime engine (span enrichment, baselines, anomaly detection)
agenttel-genai/               # GenAI instrumentation (LangChain4j, Spring AI, provider SDKs)
agenttel-agent/               # Agent interface layer (MCP server, health, incidents, reporting)
agenttel-spring-boot-starter/ # Spring Boot auto-configuration
agenttel-javaagent-extension/ # Zero-code OTel javaagent extension
agenttel-web/                 # Browser telemetry SDK (TypeScript)
agenttel-instrument/          # IDE MCP server for instrumentation automation (Python)
agenttel-testing/             # Test utilities
examples/                     # Example applications
```

## How to Contribute

### Reporting Bugs

Open a [GitHub issue](https://github.com/rrohitramsen/AgentTel/issues/new?template=bug_report.md) with:

- A clear description of the problem
- Steps to reproduce
- Expected vs actual behavior
- Java version, OTel SDK version, and framework versions

### Suggesting Features

Open a [GitHub issue](https://github.com/rrohitramsen/AgentTel/issues/new?template=feature_request.md) describing:

- The use case or problem you're solving
- Your proposed approach (if any)
- How it fits with AgentTel's design principles

### Submitting Pull Requests

1. Fork the repository
2. Create a feature branch from `main`
3. Make your changes
4. Ensure all tests pass: `./gradlew clean build`
5. Submit a pull request against `main`

### PR Guidelines

- **Keep PRs focused.** One feature or fix per PR.
- **Include tests.** New functionality should have test coverage.
- **Follow existing patterns.** Look at existing code for conventions.
- **Update documentation** if your change affects the public API.

## Code Style

**Java (backend modules):**

- Java 17+ features are welcome (records, sealed classes, pattern matching)
- Use the existing formatting conventions in the codebase
- Prefer clarity over cleverness
- No wildcard imports except `java.util.*`
- All public classes should have Javadoc

**TypeScript (agenttel-web):**

- Strict mode, no `any` types
- Follow existing naming conventions (camelCase for variables, PascalCase for types)

**Python (agenttel-instrument):**

- Type hints on all public functions
- Async-first (use `async`/`await` for I/O)

## Module Guidelines

### agenttel-api

- **Zero dependencies.** This module must never depend on OTel, Spring, or any other library.
- All public annotations and constants live here.

### agenttel-core

- Depends only on `agenttel-api` and the OpenTelemetry SDK.
- All data structures must be thread-safe for concurrent access.
- Prefer bounded data structures to prevent memory leaks.

### agenttel-genai

- All GenAI framework dependencies must be `compileOnly`.
- Use `@ConditionalOnClass` for Spring auto-configuration.
- New provider integrations should follow the existing wrapper pattern.

### agenttel-agent

- Depends on `agenttel-core`.
- MCP tools should return prompt-optimized text, not raw JSON dumps.
- All agent actions must be tracked for auditability.
- Reporting components (TrendAnalyzer, SloReportGenerator, etc.) should produce concise output optimized for LLM context windows.

### agenttel-javaagent-extension

- Must not depend on Spring.
- Uses OTel SPI (`AutoConfigurationCustomizerProvider`) for zero-code integration.
- Reads config from `agenttel.yml`, system properties, or environment variables.

### agenttel-web

- TypeScript with strict mode. Target ES2020.
- Auto-instrumentation trackers must not capture PII — use `data-agenttel-target` for element identification.
- Dual build output: CommonJS + ES Modules via Rollup.
- Tests use Jest with jsdom environment.
- Attribute keys should mirror backend `AgentTelAttributes` under the `agenttel.client.*` namespace.

### agenttel-instrument

- Python 3.11+ with async (aiohttp).
- Tools should propose changes, not apply them directly (except `apply_improvements` for low-risk items).
- Risk-based classification: low (auto-applicable), medium (suggest), high (human-only).
- Must connect to backend MCP server for live health data when calibrating baselines.

## Testing

- **JVM modules:** JUnit 5 + AssertJ + Mockito. Integration tests use `InMemorySpanExporter` from OTel SDK Testing. MCP server tests use JDK's `HttpClient`.
- **Frontend SDK:** Jest + jsdom + ts-jest.
- Tests should be fast (< 1 second each).

## Questions?

Open a discussion in [GitHub Issues](https://github.com/rrohitramsen/AgentTel/issues).
