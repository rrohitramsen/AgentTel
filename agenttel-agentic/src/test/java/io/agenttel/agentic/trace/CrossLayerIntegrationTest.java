package io.agenttel.agentic.trace;

import io.agenttel.agentic.AgentType;
import io.agenttel.agentic.StepType;
import io.agenttel.agentic.cost.AgentCostAggregator;
import io.agenttel.api.attributes.AgenticAttributes;
import io.agenttel.genai.conventions.AgentTelGenAiAttributes;
import io.agenttel.genai.conventions.GenAiAttributes;
import io.agenttel.genai.trace.GenAiSpanBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-layer integration test verifying trace linkage across
 * Backend → Agent → GenAI layers.
 */
class CrossLayerIntegrationTest {

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private OpenTelemetry otel;
    private AgentTracer agentTracer;
    private Tracer backendTracer;
    private GenAiSpanBuilder genAiSpanBuilder;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        AgentCostAggregator costAggregator = new AgentCostAggregator();

        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(costAggregator)
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        otel = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        agentTracer = AgentTracer.create(otel)
                .agentName("incident-responder")
                .agentType(AgentType.SINGLE)
                .framework("custom")
                .build();

        backendTracer = otel.getTracer("io.agenttel.backend");
        genAiSpanBuilder = new GenAiSpanBuilder(otel.getTracer("io.agenttel.genai"));
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void singleTraceConnectsBackendAgentAndGenAi() {
        // Layer 1: Backend HTTP span
        Span backendSpan = backendTracer.spanBuilder("GET /api/diagnose")
                .startSpan();
        String expectedTraceId;

        try (Scope backendScope = backendSpan.makeCurrent()) {
            expectedTraceId = backendSpan.getSpanContext().getTraceId();

            // Layer 2: Agent invocation (child of backend)
            try (AgentInvocation invocation = agentTracer.invoke("Diagnose high latency")) {
                invocation.step(StepType.THOUGHT, "Need to analyze metrics");

                // Layer 3: GenAI call (child of agent invocation via Context.current())
                Span genAiSpan = genAiSpanBuilder.startSpan("chat", "claude-3-opus", "anthropic", null);
                try (Scope genAiScope = genAiSpan.makeCurrent()) {
                    genAiSpanBuilder.endSpanSuccess(genAiSpan, "claude-3-opus", "resp-123",
                            500, 200, List.of("stop"));
                }

                invocation.complete(true);
            }
        } finally {
            backendSpan.end();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        // Backend + invocation + step + GenAI = 4 spans
        assertThat(spans).hasSize(4);

        // All spans share the same trace ID
        for (SpanData span : spans) {
            assertThat(span.getTraceId()).isEqualTo(expectedTraceId);
        }

        // Verify parent-child chain: backend -> invocation -> genai
        SpanData backend = findSpan(spans, "GET /api/diagnose");
        SpanData invocation = findSpan(spans, "invoke_agent");
        SpanData genAi = findSpan(spans, "chat claude-3-opus");

        assertThat(invocation.getParentSpanId()).isEqualTo(backend.getSpanId());
        assertThat(genAi.getParentSpanId()).isEqualTo(invocation.getSpanId());

        // Verify GenAI attributes are present
        assertThat(genAi.getAttributes().get(GenAiAttributes.GEN_AI_OPERATION_NAME)).isEqualTo("chat");
        assertThat(genAi.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM)).isEqualTo("anthropic");
        assertThat(genAi.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS)).isEqualTo(500L);
    }

    @Test
    void traceContextPropagation_acrossAgentHandoff() {
        // Agent A invokes, then hands off to Agent B
        AgentTracer agentA = AgentTracer.create(otel)
                .agentName("triage-agent")
                .agentType(AgentType.ORCHESTRATOR)
                .build();

        AgentTracer agentB = AgentTracer.create(otel)
                .agentName("billing-specialist")
                .agentType(AgentType.WORKER)
                .build();

        String expectedTraceId;

        try (AgentInvocation invA = agentA.invoke("Handle support ticket")) {
            expectedTraceId = Span.current().getSpanContext().getTraceId();

            // Handoff from A to B
            try (HandoffScope handoff = invA.handoff("billing-specialist", "Billing dispute")) {
                // Agent B operates within the handoff context
                try (AgentInvocation invB = agentB.invoke("Resolve billing dispute")) {
                    invB.step(StepType.ACTION, "Checking account");
                    invB.complete(true);
                }
                handoff.success();
            }

            invA.complete(true);
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();

        // All spans in the same trace
        for (SpanData span : spans) {
            assertThat(span.getTraceId()).isEqualTo(expectedTraceId);
        }

        // Verify chain: invA -> handoff -> invB -> step
        SpanData invASpan = spans.stream()
                .filter(s -> s.getName().equals("invoke_agent")
                        && "triage-agent".equals(s.getAttributes().get(AgenticAttributes.AGENT_NAME)))
                .findFirst().orElseThrow();

        SpanData handoffSpan = findSpan(spans, "agenttel.agentic.handoff");
        assertThat(handoffSpan.getParentSpanId()).isEqualTo(invASpan.getSpanId());

        SpanData invBSpan = spans.stream()
                .filter(s -> s.getName().equals("invoke_agent")
                        && "billing-specialist".equals(s.getAttributes().get(AgenticAttributes.AGENT_NAME)))
                .findFirst().orElseThrow();
        assertThat(invBSpan.getParentSpanId()).isEqualTo(handoffSpan.getSpanId());
    }

    @Test
    void costRollup_fromGenAiToAgentInvocation() {
        try (AgentInvocation invocation = agentTracer.invoke("Analyze logs")) {
            // First LLM call
            Span genAi1 = genAiSpanBuilder.startSpan("chat", "gpt-4", "openai", null);
            try (Scope s1 = genAi1.makeCurrent()) {
                genAi1.setAttribute(AgentTelGenAiAttributes.GENAI_COST_USD, 0.03);
                genAi1.setAttribute(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS, 1000L);
                genAi1.setAttribute(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS, 500L);
                genAi1.end();
            }

            // Second LLM call
            Span genAi2 = genAiSpanBuilder.startSpan("chat", "gpt-4", "openai", null);
            try (Scope s2 = genAi2.makeCurrent()) {
                genAi2.setAttribute(AgentTelGenAiAttributes.GENAI_COST_USD, 0.05);
                genAi2.setAttribute(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS, 2000L);
                genAi2.setAttribute(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS, 800L);
                genAi2.end();
            }

            invocation.complete(true);
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData invSpan = findSpan(spans, "invoke_agent");

        // Verify GenAI spans are children of the invocation and carry cost attributes
        List<SpanData> genAiSpans = spans.stream()
                .filter(s -> s.getName().startsWith("chat "))
                .toList();
        assertThat(genAiSpans).hasSize(2);

        // Both GenAI spans are children of the invocation
        for (SpanData genAi : genAiSpans) {
            assertThat(genAi.getParentSpanId()).isEqualTo(invSpan.getSpanId());
            assertThat(genAi.getTraceId()).isEqualTo(invSpan.getTraceId());
        }

        // Individual GenAI spans carry their cost attributes
        SpanData first = genAiSpans.stream()
                .filter(s -> s.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS) == 1000L)
                .findFirst().orElseThrow();
        assertThat(first.getAttributes().get(AgentTelGenAiAttributes.GENAI_COST_USD)).isEqualTo(0.03);
        assertThat(first.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS)).isEqualTo(500L);

        SpanData second = genAiSpans.stream()
                .filter(s -> s.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS) == 2000L)
                .findFirst().orElseThrow();
        assertThat(second.getAttributes().get(AgentTelGenAiAttributes.GENAI_COST_USD)).isEqualTo(0.05);
        assertThat(second.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS)).isEqualTo(800L);
    }

    private SpanData findSpan(List<SpanData> spans, String name) {
        return spans.stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found: " + name));
    }
}
