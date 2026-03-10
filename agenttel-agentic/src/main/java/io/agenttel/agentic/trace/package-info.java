/**
 * Core tracing API for agent observability.
 *
 * <p>{@link io.agenttel.agentic.trace.AgentTracer} is the main entry point for creating
 * agent invocation spans, step spans, tool call spans, and handoff spans.
 *
 * <p>All scope classes implement {@link AutoCloseable} for use with try-with-resources.
 */
package io.agenttel.agentic.trace;
