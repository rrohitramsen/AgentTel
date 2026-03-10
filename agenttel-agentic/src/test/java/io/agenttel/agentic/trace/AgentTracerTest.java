package io.agenttel.agentic.trace;

import io.agenttel.agentic.*;
import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
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

class AgentTracerTest {

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
                .agentName("test-agent")
                .agentType(AgentType.SINGLE)
                .framework("custom")
                .agentVersion("1.0.0")
                .build();
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void invoke_createsInvocationSpan() {
        try (AgentInvocation inv = tracer.invoke("Test goal")) {
            inv.complete(true);
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("invoke_agent");
        assertThat(span.getAttributes().get(AgenticAttributes.AGENT_NAME)).isEqualTo("test-agent");
        assertThat(span.getAttributes().get(AgenticAttributes.AGENT_TYPE)).isEqualTo("single");
        assertThat(span.getAttributes().get(AgenticAttributes.AGENT_FRAMEWORK)).isEqualTo("custom");
        assertThat(span.getAttributes().get(AgenticAttributes.AGENT_VERSION)).isEqualTo("1.0.0");
        assertThat(span.getAttributes().get(AgenticAttributes.INVOCATION_GOAL)).isEqualTo("Test goal");
        assertThat(span.getAttributes().get(AgenticAttributes.INVOCATION_ID)).isNotNull();
        assertThat(span.getAttributes().get(AgenticAttributes.INVOCATION_STATUS)).isEqualTo("success");
        assertThat(span.getAttributes().get(AgenticAttributes.QUALITY_GOAL_ACHIEVED)).isTrue();
    }

    @Test
    void invoke_withNamedAgent() {
        try (AgentInvocation inv = tracer.invoke("custom-agent", "Do something")) {
            inv.complete(true);
        }

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AgenticAttributes.AGENT_NAME)).isEqualTo("custom-agent");
    }

    @Test
    void invoke_failure() {
        try (AgentInvocation inv = tracer.invoke("Test failure")) {
            inv.complete(false);
        }

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AgenticAttributes.INVOCATION_STATUS)).isEqualTo("failure");
        assertThat(span.getAttributes().get(AgenticAttributes.QUALITY_GOAL_ACHIEVED)).isFalse();
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void invoke_withSpecificStatus() {
        try (AgentInvocation inv = tracer.invoke("Test escalation")) {
            inv.complete(InvocationStatus.ESCALATED);
        }

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AgenticAttributes.INVOCATION_STATUS)).isEqualTo("escalated");
    }

    @Test
    void invoke_maxSteps() {
        try (AgentInvocation inv = tracer.invoke("Test max steps")) {
            inv.maxSteps(10);
            inv.complete(true);
        }

        SpanData span = spanExporter.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(AgenticAttributes.INVOCATION_MAX_STEPS)).isEqualTo(10L);
    }

    @Test
    void step_createsStepSpan() {
        try (AgentInvocation inv = tracer.invoke("Test steps")) {
            inv.step(StepType.THOUGHT, "I need to check something");
            inv.step(StepType.ACTION, "Calling a tool");
            inv.step(StepType.OBSERVATION, "Got the result");
            inv.complete(true);
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        // 3 step spans + 1 invocation span
        assertThat(spans).hasSize(4);

        // Step spans end first (LIFO)
        SpanData step1 = spans.stream()
                .filter(s -> s.getAttributes().get(AgenticAttributes.STEP_NUMBER) != null
                        && s.getAttributes().get(AgenticAttributes.STEP_NUMBER) == 1L)
                .findFirst().orElseThrow();
        assertThat(step1.getName()).isEqualTo("agenttel.agentic.step");
        assertThat(step1.getAttributes().get(AgenticAttributes.STEP_TYPE)).isEqualTo("thought");
        assertThat(step1.getAttributes().get(AgenticAttributes.STEP_NUMBER)).isEqualTo(1L);

        SpanData invocation = spans.stream()
                .filter(s -> s.getName().equals("invoke_agent"))
                .findFirst().orElseThrow();
        assertThat(invocation.getAttributes().get(AgenticAttributes.INVOCATION_STEPS)).isEqualTo(3L);
    }

    @Test
    void beginStep_createsClosableStepSpan() {
        try (AgentInvocation inv = tracer.invoke("Test scoped step")) {
            try (StepScope step = inv.beginStep(StepType.ACTION)) {
                step.toolName("my_tool").toolStatus("success");
            }
            inv.complete(true);
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData step = spans.stream()
                .filter(s -> s.getName().equals("agenttel.agentic.step"))
                .findFirst().orElseThrow();
        assertThat(step.getAttributes().get(AgenticAttributes.STEP_TOOL_NAME)).isEqualTo("my_tool");
        assertThat(step.getAttributes().get(AgenticAttributes.STEP_TOOL_STATUS)).isEqualTo("success");
    }

    @Test
    void beginStep_withIteration() {
        try (AgentInvocation inv = tracer.invoke("Test iteration step")) {
            try (StepScope step = inv.beginStep(StepType.EVALUATION, 3)) {
                // Evaluation step in iteration 3
            }
            inv.complete(true);
        }

        SpanData step = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.step"))
                .findFirst().orElseThrow();
        assertThat(step.getAttributes().get(AgenticAttributes.STEP_ITERATION)).isEqualTo(3L);
    }

    @Test
    void toolCall_createsToolCallSpan() {
        try (AgentInvocation inv = tracer.invoke("Test tool call")) {
            try (ToolCallScope tool = inv.toolCall("get_service_health")) {
                tool.success();
            }
            inv.complete(true);
        }

        SpanData toolSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.tool_call"))
                .findFirst().orElseThrow();
        assertThat(toolSpan.getAttributes().get(AgenticAttributes.STEP_TOOL_NAME)).isEqualTo("get_service_health");
        assertThat(toolSpan.getAttributes().get(AgenticAttributes.STEP_TOOL_STATUS)).isEqualTo("success");
    }

    @Test
    void toolCall_error() {
        try (AgentInvocation inv = tracer.invoke("Test tool error")) {
            try (ToolCallScope tool = inv.toolCall("failing_tool")) {
                tool.error(new RuntimeException("Connection refused"));
            }
            inv.complete(false);
        }

        SpanData toolSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.tool_call"))
                .findFirst().orElseThrow();
        assertThat(toolSpan.getAttributes().get(AgenticAttributes.STEP_TOOL_STATUS)).isEqualTo("error");
        assertThat(toolSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void toolCall_timeout() {
        try (AgentInvocation inv = tracer.invoke("Test tool timeout")) {
            try (ToolCallScope tool = inv.toolCall("slow_tool")) {
                tool.timeout();
            }
            inv.complete(false);
        }

        SpanData toolSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.tool_call"))
                .findFirst().orElseThrow();
        assertThat(toolSpan.getAttributes().get(AgenticAttributes.STEP_TOOL_STATUS)).isEqualTo("timeout");
    }

    @Test
    void task_createsTaskSpanWithNesting() {
        try (AgentInvocation inv = tracer.invoke("Test task decomposition")) {
            try (TaskScope task = inv.task("main-task")) {
                try (TaskScope subtask = task.subtask("sub-task-1")) {
                    subtask.complete();
                }
                task.complete();
            }
            inv.complete(true);
        }

        List<SpanData> taskSpans = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.task"))
                .toList();
        assertThat(taskSpans).hasSize(2);

        SpanData mainTask = taskSpans.stream()
                .filter(s -> s.getAttributes().get(AgenticAttributes.TASK_DEPTH) == 0L)
                .findFirst().orElseThrow();
        assertThat(mainTask.getAttributes().get(AgenticAttributes.TASK_NAME)).isEqualTo("main-task");
        assertThat(mainTask.getAttributes().get(AgenticAttributes.TASK_STATUS)).isEqualTo("completed");

        SpanData subtask = taskSpans.stream()
                .filter(s -> s.getAttributes().get(AgenticAttributes.TASK_DEPTH) == 1L)
                .findFirst().orElseThrow();
        assertThat(subtask.getAttributes().get(AgenticAttributes.TASK_NAME)).isEqualTo("sub-task-1");
        assertThat(subtask.getAttributes().get(AgenticAttributes.TASK_PARENT_ID))
                .isEqualTo(mainTask.getAttributes().get(AgenticAttributes.TASK_ID));
    }

    @Test
    void task_failure() {
        try (AgentInvocation inv = tracer.invoke("Test task failure")) {
            try (TaskScope task = inv.task("failing-task")) {
                task.fail("Resource not found");
            }
            inv.complete(false);
        }

        SpanData taskSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.task"))
                .findFirst().orElseThrow();
        assertThat(taskSpan.getAttributes().get(AgenticAttributes.TASK_STATUS)).isEqualTo("failed");
        assertThat(taskSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void task_delegated() {
        try (AgentInvocation inv = tracer.invoke("Test task delegation")) {
            try (TaskScope task = inv.task("delegated-task")) {
                task.delegated();
            }
            inv.complete(true);
        }

        SpanData taskSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.task"))
                .findFirst().orElseThrow();
        assertThat(taskSpan.getAttributes().get(AgenticAttributes.TASK_STATUS)).isEqualTo("delegated");
    }

    @Test
    void handoff_createsHandoffSpan() {
        try (AgentInvocation inv = tracer.invoke("triage", "Handle request")) {
            try (HandoffScope handoff = inv.handoff("billing-specialist", "Billing dispute detected")) {
                handoff.success();
            }
        }

        SpanData handoffSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.handoff"))
                .findFirst().orElseThrow();
        assertThat(handoffSpan.getAttributes().get(AgenticAttributes.HANDOFF_FROM_AGENT)).isEqualTo("triage");
        assertThat(handoffSpan.getAttributes().get(AgenticAttributes.HANDOFF_TO_AGENT)).isEqualTo("billing-specialist");
        assertThat(handoffSpan.getAttributes().get(AgenticAttributes.HANDOFF_REASON)).isEqualTo("Billing dispute detected");
    }

    @Test
    void handoff_withChainDepth() {
        try (AgentInvocation inv = tracer.invoke("agent-a", "Handle")) {
            try (HandoffScope handoff = inv.handoff("agent-b", "Escalation", 2)) {
                // Deep handoff chain
            }
        }

        SpanData handoffSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.handoff"))
                .findFirst().orElseThrow();
        assertThat(handoffSpan.getAttributes().get(AgenticAttributes.HANDOFF_CHAIN_DEPTH)).isEqualTo(2L);
    }

    @Test
    void humanCheckpoint_tracksWaitAndDecision() {
        try (AgentInvocation inv = tracer.invoke("deployer", "Deploy v2.1.0")) {
            try (HumanCheckpointScope checkpoint = inv.humanCheckpoint("approval", "Deploy to prod?")) {
                checkpoint.decision("approved");
            }
            inv.complete(true);
        }

        SpanData checkpointSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.human_input"))
                .findFirst().orElseThrow();
        assertThat(checkpointSpan.getAttributes().get(AgenticAttributes.HUMAN_CHECKPOINT_TYPE)).isEqualTo("approval");
        assertThat(checkpointSpan.getAttributes().get(AgenticAttributes.HUMAN_DECISION)).isEqualTo("approved");
        assertThat(checkpointSpan.getAttributes().get(AgenticAttributes.HUMAN_WAIT_MS)).isNotNull();

        SpanData invocationSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("invoke_agent"))
                .findFirst().orElseThrow();
        assertThat(invocationSpan.getAttributes().get(AgenticAttributes.QUALITY_HUMAN_INTERVENTIONS)).isEqualTo(1L);
    }

    @Test
    void guardrail_recordsActivation() {
        try (AgentInvocation inv = tracer.invoke("assistant", "Answer question")) {
            inv.guardrail("pii-filter", "block", "Response contained PII");
            inv.complete(true);
        }

        SpanData guardrailSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.guardrail"))
                .findFirst().orElseThrow();
        assertThat(guardrailSpan.getAttributes().get(AgenticAttributes.GUARDRAIL_TRIGGERED)).isTrue();
        assertThat(guardrailSpan.getAttributes().get(AgenticAttributes.GUARDRAIL_NAME)).isEqualTo("pii-filter");
        assertThat(guardrailSpan.getAttributes().get(AgenticAttributes.GUARDRAIL_ACTION)).isEqualTo("block");
        assertThat(guardrailSpan.getAttributes().get(AgenticAttributes.GUARDRAIL_REASON)).isEqualTo("Response contained PII");
    }

    @Test
    void memory_createsMemorySpan() {
        tracer.memory(MemoryOperation.SEARCH, "vector_store", 5);

        SpanData memSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.memory"))
                .findFirst().orElseThrow();
        assertThat(memSpan.getAttributes().get(AgenticAttributes.MEMORY_OPERATION)).isEqualTo("search");
        assertThat(memSpan.getAttributes().get(AgenticAttributes.MEMORY_STORE_TYPE)).isEqualTo("vector_store");
        assertThat(memSpan.getAttributes().get(AgenticAttributes.MEMORY_ITEMS)).isEqualTo(5L);
    }

    @Test
    void fullReactLoop_createsCorrectSpanHierarchy() {
        try (AgentInvocation inv = tracer.invoke("Diagnose high latency on payment-service")) {
            inv.step(StepType.THOUGHT, "Need to check service health metrics");

            try (ToolCallScope tool = inv.toolCall("get_service_health")) {
                tool.success();
            }

            inv.step(StepType.OBSERVATION, "P99 latency is 5x baseline");

            try (ToolCallScope tool = inv.toolCall("execute_remediation")) {
                tool.success();
            }

            inv.complete(true);
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        // 2 step spans + 2 tool call spans + 1 invocation span = 5
        assertThat(spans).hasSize(5);

        SpanData invocation = spans.stream()
                .filter(s -> s.getName().equals("invoke_agent"))
                .findFirst().orElseThrow();
        // 2 steps + 2 tool calls = 4 step increments
        assertThat(invocation.getAttributes().get(AgenticAttributes.INVOCATION_STEPS)).isEqualTo(4L);

        // All child spans should be children of the invocation span
        String invSpanId = invocation.getSpanId();
        long childCount = spans.stream()
                .filter(s -> s.getParentSpanId().equals(invSpanId))
                .count();
        assertThat(childCount).isEqualTo(4);
    }
}
