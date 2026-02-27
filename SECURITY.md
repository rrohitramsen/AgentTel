# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.1.x-alpha | Yes |

## Reporting a Vulnerability

If you discover a security vulnerability in AgentTel, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, please email: **rrohitramsen@gmail.com**

Include:

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

## Response Timeline

- **Acknowledgment:** Within 48 hours
- **Assessment:** Within 7 days
- **Fix:** Depending on severity, typically within 14 days

## Scope

The following are in scope for security reports:

- **MCP Server** — Authentication bypass, injection attacks, unauthorized access to tools
- **Remediation Framework** — Unauthorized execution of remediation actions
- **Telemetry Data** — Unintended capture of sensitive data (PII, credentials, secrets)
- **Dependencies** — Known vulnerabilities in direct dependencies

## Security Design

AgentTel follows these security principles:

- **No secrets in telemetry.** AgentTel does not capture request/response bodies, headers, or PII. Only operational metadata (latency, error rates, topology) is recorded.
- **Approval workflow.** Remediation actions marked `requiresApproval = true` cannot be executed without explicit authorization.
- **Audit trail.** All agent actions are recorded as OpenTelemetry spans, providing a complete audit log.
- **No default credentials.** The MCP server does not include built-in authentication. It should be deployed behind a reverse proxy or API gateway with appropriate auth in production.
