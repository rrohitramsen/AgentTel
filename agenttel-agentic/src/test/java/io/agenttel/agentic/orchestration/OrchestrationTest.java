package io.agenttel.agentic.orchestration;

import io.agenttel.agentic.AgentType;
import io.agenttel.agentic.OrchestrationPattern;
import io.agenttel.agentic.trace.AgentInvocation;
import io.agenttel.agentic.trace.AgentTracer;
import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestrationTest {

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private AgentTracer tracer;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        OpenTelemetry otel = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        tracer = AgentTracer.create(otel)
                .agentName("orchestrator")
                .agentType(AgentType.ORCHESTRATOR)
                .build();
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void sequential_tracksStages() {
        try (SequentialOrchestration orch = tracer.orchestrate(OrchestrationPattern.SEQUENTIAL, 3)) {
            try (AgentInvocation stage1 = orch.stage("data-collector", 1)) {
                stage1.complete(true);
            }
            try (AgentInvocation stage2 = orch.stage("analyzer", 2)) {
                stage2.complete(true);
            }
            try (AgentInvocation stage3 = orch.stage("reporter", 3)) {
                stage3.complete(true);
            }
            orch.complete();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        // 3 invocation spans + 1 session span
        assertThat(spans).hasSize(4);

        SpanData sessionSpan = spans.stream()
                .filter(s -> s.getName().equals("agenttel.agentic.session"))
                .findFirst().orElseThrow();
        assertThat(sessionSpan.getAttributes().get(AgenticAttributes.ORCHESTRATION_PATTERN)).isEqualTo("sequential");
        assertThat(sessionSpan.getAttributes().get(AgenticAttributes.ORCHESTRATION_TOTAL_STAGES)).isEqualTo(3L);

        // Verify stage numbers
        List<SpanData> stageSpans = spans.stream()
                .filter(s -> s.getName().equals("invoke_agent"))
                .toList();
        assertThat(stageSpans).hasSize(3);
        assertThat(stageSpans.stream()
                .map(s -> s.getAttributes().get(AgenticAttributes.ORCHESTRATION_STAGE))
                .toList()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void parallel_tracksBranchesAndAggregation() {
        try (Orchestration orch = tracer.orchestrate(OrchestrationPattern.PARALLEL)) {
            ParallelOrchestration parallel = (ParallelOrchestration) orch;
            try (AgentInvocation b1 = parallel.branch("analyst-1")) {
                b1.complete(true);
            }
            try (AgentInvocation b2 = parallel.branch("analyst-2")) {
                b2.complete(true);
            }
            try (AgentInvocation b3 = parallel.branch("analyst-3")) {
                b3.complete(true);
            }
            parallel.aggregate("voting");
            parallel.complete();
        }

        SpanData sessionSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.session"))
                .findFirst().orElseThrow();
        assertThat(sessionSpan.getAttributes().get(AgenticAttributes.ORCHESTRATION_PATTERN)).isEqualTo("parallel");
        assertThat(sessionSpan.getAttributes().get(AgenticAttributes.ORCHESTRATION_PARALLEL_BRANCHES)).isEqualTo(3L);
        assertThat(sessionSpan.getAttributes().get(AgenticAttributes.ORCHESTRATION_AGGREGATION)).isEqualTo("voting");
    }

    @Test
    void evaluatorOptimizer_tracksIterations() {
        try (Orchestration orch = tracer.orchestrate(OrchestrationPattern.EVALUATOR_OPTIMIZER)) {
            EvalLoopOrchestration evalLoop = (EvalLoopOrchestration) orch;

            // Iteration 0
            try (AgentInvocation gen = evalLoop.generate("code-writer", 0)) {
                gen.complete(true);
            }
            try (EvalLoopOrchestration.EvalInvocation eval = evalLoop.evaluate("code-reviewer", 0)) {
                eval.score(0.7);
                eval.feedback("Missing error handling");
                eval.complete(false);
            }

            // Iteration 1
            try (AgentInvocation gen = evalLoop.generate("code-writer", 1)) {
                gen.complete(true);
            }
            try (EvalLoopOrchestration.EvalInvocation eval = evalLoop.evaluate("code-reviewer", 1)) {
                eval.score(0.95);
                eval.complete(true);
            }

            evalLoop.complete();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();

        SpanData sessionSpan = spans.stream()
                .filter(s -> s.getName().equals("agenttel.agentic.session"))
                .findFirst().orElseThrow();
        assertThat(sessionSpan.getAttributes().get(AgenticAttributes.ORCHESTRATION_PATTERN)).isEqualTo("evaluator_optimizer");

        // Check eval scores
        List<SpanData> evalSpans = spans.stream()
                .filter(s -> s.getName().equals("agenttel.agentic.evaluate"))
                .toList();
        assertThat(evalSpans).hasSize(2);
        assertThat(evalSpans.stream()
                .map(s -> s.getAttributes().get(AgenticAttributes.QUALITY_EVAL_SCORE))
                .toList()).containsExactlyInAnyOrder(0.7, 0.95);
    }

    @Test
    void genericOrchestration_createsChildInvocations() {
        try (Orchestration orch = tracer.orchestrate(OrchestrationPattern.HANDOFF)) {
            try (AgentInvocation agent1 = orch.invoke("agent-1", "First agent task")) {
                agent1.complete(true);
            }
            try (AgentInvocation agent2 = orch.invoke("agent-2", "Second agent task")) {
                agent2.complete(true);
            }
            orch.complete();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(3); // 2 invocations + 1 session

        SpanData sessionSpan = spans.stream()
                .filter(s -> s.getName().equals("agenttel.agentic.session"))
                .findFirst().orElseThrow();
        assertThat(sessionSpan.getAttributes().get(AgenticAttributes.ORCHESTRATION_PATTERN)).isEqualTo("handoff");

        // Child invocations should be children of session span
        String sessionSpanId = sessionSpan.getSpanId();
        long childCount = spans.stream()
                .filter(s -> s.getParentSpanId().equals(sessionSpanId))
                .count();
        assertThat(childCount).isEqualTo(2);
    }
}
