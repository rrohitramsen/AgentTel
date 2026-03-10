package io.agenttel.agentic.trace;

import io.agenttel.agentic.AgentType;
import io.agenttel.agentic.MemoryOperation;
import io.agenttel.agentic.OrchestrationPattern;
import io.agenttel.agentic.config.AgentConfig;
import io.agenttel.agentic.config.AgentConfigRegistry;
import io.agenttel.api.attributes.AgenticAttributes;
import io.agenttel.agentic.orchestration.EvalLoopOrchestration;
import io.agenttel.agentic.orchestration.Orchestration;
import io.agenttel.agentic.orchestration.ParallelOrchestration;
import io.agenttel.agentic.orchestration.SequentialOrchestration;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.util.UUID;

/**
 * Main entry point for agent observability instrumentation.
 *
 * <p>Use the builder to configure agent identity, then create invocation and
 * orchestration scopes:
 *
 * <pre>{@code
 * AgentTracer tracer = AgentTracer.create(openTelemetry)
 *     .agentName("incident-responder")
 *     .agentType(AgentType.SINGLE)
 *     .build();
 *
 * try (AgentInvocation invocation = tracer.invoke("Diagnose high latency")) {
 *     invocation.step(StepType.THOUGHT, "Need to check metrics");
 *     invocation.complete(true);
 * }
 * }</pre>
 */
public class AgentTracer {

    private static final String INSTRUMENTATION_NAME = "io.agenttel.agentic";

    private final Tracer tracer;
    private final String agentName;
    private final AgentType agentType;
    private final String framework;
    private final String agentVersion;
    private final AgentConfigRegistry configRegistry;

    private AgentTracer(Builder builder) {
        this.tracer = builder.openTelemetry.getTracer(INSTRUMENTATION_NAME);
        this.agentName = builder.agentName;
        this.agentType = builder.agentType;
        this.framework = builder.framework;
        this.agentVersion = builder.agentVersion;
        this.configRegistry = builder.configRegistry;
    }

    /**
     * Creates a builder for configuring an AgentTracer.
     */
    public static Builder create(OpenTelemetry openTelemetry) {
        return new Builder(openTelemetry);
    }

    /**
     * Returns the underlying OTel tracer.
     */
    public Tracer tracer() {
        return tracer;
    }

    // --- Agent Invocations ---

    /**
     * Begins an agent invocation with a goal description.
     */
    public AgentInvocation invoke(String goal) {
        return invoke(agentName, goal);
    }

    /**
     * Begins an agent invocation for a named agent with a goal description.
     * If an {@link AgentConfigRegistry} is available, per-agent config is applied
     * automatically (agent type, framework, version, maxSteps).
     */
    public AgentInvocation invoke(String name, String goal) {
        String invocationId = UUID.randomUUID().toString();
        var spanBuilder = tracer.spanBuilder("invoke_agent")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.AGENT_NAME, name)
                .setAttribute(AgenticAttributes.INVOCATION_ID, invocationId)
                .setAttribute(AgenticAttributes.INVOCATION_GOAL, goal);

        if (agentType != null) {
            spanBuilder.setAttribute(AgenticAttributes.AGENT_TYPE, agentType.getValue());
        }
        if (framework != null) {
            spanBuilder.setAttribute(AgenticAttributes.AGENT_FRAMEWORK, framework);
        }
        if (agentVersion != null) {
            spanBuilder.setAttribute(AgenticAttributes.AGENT_VERSION, agentVersion);
        }

        Span span = spanBuilder.startSpan();
        AgentInvocation inv = new AgentInvocation(span, null, tracer, name);
        applyConfig(name, span, inv);
        return inv;
    }

    /**
     * Begins an agent invocation as a child of an explicit parent context.
     */
    public AgentInvocation invoke(String name, String goal, Context parentContext) {
        String invocationId = UUID.randomUUID().toString();
        var spanBuilder = tracer.spanBuilder("invoke_agent")
                .setParent(parentContext)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.AGENT_NAME, name)
                .setAttribute(AgenticAttributes.INVOCATION_ID, invocationId)
                .setAttribute(AgenticAttributes.INVOCATION_GOAL, goal);

        if (agentType != null) {
            spanBuilder.setAttribute(AgenticAttributes.AGENT_TYPE, agentType.getValue());
        }
        if (framework != null) {
            spanBuilder.setAttribute(AgenticAttributes.AGENT_FRAMEWORK, framework);
        }
        if (agentVersion != null) {
            spanBuilder.setAttribute(AgenticAttributes.AGENT_VERSION, agentVersion);
        }

        Span span = spanBuilder.startSpan();
        AgentInvocation inv = new AgentInvocation(span, parentContext, tracer, name);
        applyConfig(name, span, inv);
        return inv;
    }

    /**
     * Applies per-agent config from the registry, if available.
     * Config values override builder defaults for type, framework, version.
     */
    private void applyConfig(String name, Span span, AgentInvocation inv) {
        if (configRegistry == null) return;
        configRegistry.getConfig(name).ifPresent(config -> {
            if (!config.type().isEmpty()) {
                span.setAttribute(AgenticAttributes.AGENT_TYPE, config.type());
            }
            if (!config.framework().isEmpty()) {
                span.setAttribute(AgenticAttributes.AGENT_FRAMEWORK, config.framework());
            }
            if (!config.version().isEmpty()) {
                span.setAttribute(AgenticAttributes.AGENT_VERSION, config.version());
            }
            if (config.maxSteps() > 0) {
                inv.maxSteps(config.maxSteps());
            }
        });
    }

    // --- Orchestration ---

    /**
     * Begins an orchestrated workflow with a specific pattern.
     */
    public Orchestration orchestrate(OrchestrationPattern pattern) {
        Span orchSpan = tracer.spanBuilder("agenttel.agentic.session")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.ORCHESTRATION_PATTERN, pattern.getValue())
                .startSpan();

        return switch (pattern) {
            case SEQUENTIAL -> new SequentialOrchestration(orchSpan, null, tracer, agentName);
            case PARALLEL -> new ParallelOrchestration(orchSpan, null, tracer, agentName);
            case EVALUATOR_OPTIMIZER -> new EvalLoopOrchestration(orchSpan, null, tracer, agentName);
            default -> new Orchestration(orchSpan, null, tracer, pattern, agentName);
        };
    }

    /**
     * Begins a sequential orchestration with a known total stage count.
     */
    public SequentialOrchestration orchestrate(OrchestrationPattern pattern, int totalStages) {
        Span orchSpan = tracer.spanBuilder("agenttel.agentic.session")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.ORCHESTRATION_PATTERN, pattern.getValue())
                .setAttribute(AgenticAttributes.ORCHESTRATION_TOTAL_STAGES, (long) totalStages)
                .startSpan();
        return new SequentialOrchestration(orchSpan, null, tracer, agentName, totalStages);
    }

    // --- Memory Access ---

    /**
     * Creates a memory access span.
     */
    public void memory(MemoryOperation operation, String storeType, long items) {
        Span memSpan = tracer.spanBuilder("agenttel.agentic.memory")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(AgenticAttributes.MEMORY_OPERATION, operation.getValue())
                .setAttribute(AgenticAttributes.MEMORY_STORE_TYPE, storeType)
                .setAttribute(AgenticAttributes.MEMORY_ITEMS, items)
                .startSpan();
        memSpan.end();
    }

    // --- Builder ---

    public static class Builder {
        private final OpenTelemetry openTelemetry;
        private String agentName = "agent";
        private AgentType agentType;
        private String framework;
        private String agentVersion;
        private AgentConfigRegistry configRegistry;

        Builder(OpenTelemetry openTelemetry) {
            this.openTelemetry = openTelemetry;
        }

        public Builder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public Builder agentType(AgentType agentType) {
            this.agentType = agentType;
            return this;
        }

        public Builder framework(String framework) {
            this.framework = framework;
            return this;
        }

        public Builder agentVersion(String agentVersion) {
            this.agentVersion = agentVersion;
            return this;
        }

        public Builder configRegistry(AgentConfigRegistry configRegistry) {
            this.configRegistry = configRegistry;
            return this;
        }

        public AgentTracer build() {
            return new AgentTracer(this);
        }
    }
}
