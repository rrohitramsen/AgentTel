# Contributing to AgentTel

Thank you for your interest in contributing to AgentTel. This guide will help you get started.

## Development Setup

### Prerequisites

- JDK 17 or later
- Git

### Building

```bash
git clone https://github.com/rrohitramsen/AgentTel.git
cd AgentTel
./gradlew clean build
```

### Running Tests

```bash
# All tests
./gradlew test

# Specific module
./gradlew :agenttel-core:test
./gradlew :agenttel-agent:test
```

### Project Structure

```
agenttel-api/                 # Annotations, attributes, enums (zero dependencies)
agenttel-core/                # Runtime engine (span enrichment, baselines, anomaly detection)
agenttel-genai/               # GenAI instrumentation (LangChain4j, Spring AI, provider SDKs)
agenttel-agent/               # Agent interface layer (MCP server, health, incidents)
agenttel-spring-boot-starter/ # Spring Boot auto-configuration
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

- Java 17+ features are welcome (records, sealed classes, pattern matching)
- Use the existing formatting conventions in the codebase
- Prefer clarity over cleverness
- No wildcard imports except `java.util.*`
- All public classes should have Javadoc

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

## Testing

- Unit tests use JUnit 5 + AssertJ + Mockito
- Integration tests use `InMemorySpanExporter` from OTel SDK Testing
- MCP server tests use JDK's `HttpClient`
- Tests should be fast (< 1 second each)

## Questions?

Open a discussion in [GitHub Issues](https://github.com/rrohitramsen/AgentTel/issues).
