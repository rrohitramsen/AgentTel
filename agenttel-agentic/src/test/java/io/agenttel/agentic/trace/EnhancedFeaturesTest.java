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

class EnhancedFeaturesTest {

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
                .agentName("enhanced-agent")
                .agentType(AgentType.SINGLE)
                .build();
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    // --- Code Execution ---

    @Test
    void codeExecution_success() {
        try (AgentInvocation inv = tracer.invoke("Run code")) {
            try (CodeExecutionScope code = inv.codeExecution("python", true)) {
                code.output("Hello World");
                code.success();
            }
            inv.complete(true);
        }

        SpanData codeSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.code_execution"))
                .findFirst().orElseThrow();
        assertThat(codeSpan.getAttributes().get(AgenticAttributes.CODE_LANGUAGE)).isEqualTo("python");
        assertThat(codeSpan.getAttributes().get(AgenticAttributes.CODE_SANDBOXED)).isTrue();
        assertThat(codeSpan.getAttributes().get(AgenticAttributes.CODE_STATUS)).isEqualTo("success");
        assertThat(codeSpan.getAttributes().get(AgenticAttributes.CODE_EXIT_CODE)).isEqualTo(0L);
        assertThat(codeSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }

    @Test
    void codeExecution_error() {
        try (AgentInvocation inv = tracer.invoke("Run failing code")) {
            try (CodeExecutionScope code = inv.codeExecution("bash")) {
                code.error(new RuntimeException("Syntax error"), 1);
            }
            inv.complete(false);
        }

        SpanData codeSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.code_execution"))
                .findFirst().orElseThrow();
        assertThat(codeSpan.getAttributes().get(AgenticAttributes.CODE_STATUS)).isEqualTo("error");
        assertThat(codeSpan.getAttributes().get(AgenticAttributes.CODE_EXIT_CODE)).isEqualTo(1L);
        assertThat(codeSpan.getAttributes().get(AgenticAttributes.CODE_SANDBOXED)).isFalse();
    }

    @Test
    void codeExecution_timeout() {
        try (AgentInvocation inv = tracer.invoke("Run slow code")) {
            try (CodeExecutionScope code = inv.codeExecution("javascript", true)) {
                code.timeout();
            }
            inv.complete(false);
        }

        SpanData codeSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.code_execution"))
                .findFirst().orElseThrow();
        assertThat(codeSpan.getAttributes().get(AgenticAttributes.CODE_STATUS)).isEqualTo("timeout");
    }

    // --- Evaluation / Scoring ---

    @Test
    void evaluation_firstClassSpan() {
        try (AgentInvocation inv = tracer.invoke("Generate and evaluate")) {
            try (EvaluationScope eval = inv.evaluate("relevance-scorer", "Is the answer relevant?")) {
                eval.score(0.85).feedback("Good but could be more specific").pass();
            }
            inv.complete(true);
        }

        SpanData evalSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.evaluate"))
                .findFirst().orElseThrow();
        assertThat(evalSpan.getAttributes().get(AgenticAttributes.EVAL_SCORER_NAME)).isEqualTo("relevance-scorer");
        assertThat(evalSpan.getAttributes().get(AgenticAttributes.EVAL_CRITERIA)).isEqualTo("Is the answer relevant?");
        assertThat(evalSpan.getAttributes().get(AgenticAttributes.EVAL_SCORE)).isEqualTo(0.85);
        assertThat(evalSpan.getAttributes().get(AgenticAttributes.EVAL_FEEDBACK)).isEqualTo("Good but could be more specific");
        assertThat(evalSpan.getAttributes().get(AgenticAttributes.EVAL_TYPE)).isEqualTo("custom");
        assertThat(evalSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    }

    @Test
    void evaluation_withLlmJudge() {
        try (AgentInvocation inv = tracer.invoke("Evaluate output")) {
            try (EvaluationScope eval = inv.evaluate("gpt4-judge", "factual accuracy", EvalType.LLM_JUDGE)) {
                eval.score(0.4).feedback("Contains factual errors").fail("Below threshold");
            }
            inv.complete(false);
        }

        SpanData evalSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.evaluate"))
                .findFirst().orElseThrow();
        assertThat(evalSpan.getAttributes().get(AgenticAttributes.EVAL_TYPE)).isEqualTo("llm_judge");
        assertThat(evalSpan.getAttributes().get(AgenticAttributes.EVAL_SCORE)).isEqualTo(0.4);
        assertThat(evalSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    // --- Error Classification ---

    @Test
    void errorClassification_llmError() {
        try (AgentInvocation inv = tracer.invoke("Handle LLM error")) {
            inv.classifyError(ErrorSource.LLM, "rate_limit", true);
            inv.complete(false);
        }

        SpanData invSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("invoke_agent"))
                .findFirst().orElseThrow();
        assertThat(invSpan.getAttributes().get(AgenticAttributes.ERROR_SOURCE)).isEqualTo("llm");
        assertThat(invSpan.getAttributes().get(AgenticAttributes.ERROR_CATEGORY)).isEqualTo("rate_limit");
        assertThat(invSpan.getAttributes().get(AgenticAttributes.ERROR_RETRYABLE)).isTrue();
    }

    @Test
    void errorClassification_toolError() {
        try (AgentInvocation inv = tracer.invoke("Handle tool error")) {
            inv.classifyError("tool", "connection_refused", false);
            inv.complete(false);
        }

        SpanData invSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("invoke_agent"))
                .findFirst().orElseThrow();
        assertThat(invSpan.getAttributes().get(AgenticAttributes.ERROR_SOURCE)).isEqualTo("tool");
        assertThat(invSpan.getAttributes().get(AgenticAttributes.ERROR_RETRYABLE)).isFalse();
    }

    // --- Agent Capabilities ---

    @Test
    void capabilities_tracksToolsAndPrompt() {
        try (AgentInvocation inv = tracer.invoke("Agent with tools")) {
            inv.tools(List.of("get_weather", "search_docs", "run_query"))
               .systemPromptHash("sha256:abc123");
            inv.complete(true);
        }

        SpanData invSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("invoke_agent"))
                .findFirst().orElseThrow();
        assertThat(invSpan.getAttributes().get(AgenticAttributes.CAPABILITY_TOOLS))
                .containsExactly("get_weather", "search_docs", "run_query");
        assertThat(invSpan.getAttributes().get(AgenticAttributes.CAPABILITY_TOOL_COUNT)).isEqualTo(3L);
        assertThat(invSpan.getAttributes().get(AgenticAttributes.CAPABILITY_SYSTEM_PROMPT_HASH)).isEqualTo("sha256:abc123");
    }

    // --- Conversation Tracking ---

    @Test
    void conversation_tracksContext() {
        try (AgentInvocation inv = tracer.invoke("Chat turn 3")) {
            inv.conversation("conv-123", 3).messageCount(8);
            inv.complete(true);
        }

        SpanData invSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("invoke_agent"))
                .findFirst().orElseThrow();
        assertThat(invSpan.getAttributes().get(AgenticAttributes.CONVERSATION_ID)).isEqualTo("conv-123");
        assertThat(invSpan.getAttributes().get(AgenticAttributes.CONVERSATION_TURN)).isEqualTo(3L);
        assertThat(invSpan.getAttributes().get(AgenticAttributes.CONVERSATION_MESSAGE_COUNT)).isEqualTo(8L);
    }

    // --- Retriever ---

    @Test
    void retriever_tracksQuery() {
        try (AgentInvocation inv = tracer.invoke("RAG pipeline")) {
            try (RetrieverScope ret = inv.retrieve("How to deploy?", "pinecone", 5)) {
                ret.documentCount(5).relevanceScoreAvg(0.82).relevanceScoreMin(0.61).success();
            }
            inv.complete(true);
        }

        SpanData retSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.retriever"))
                .findFirst().orElseThrow();
        assertThat(retSpan.getAttributes().get(AgenticAttributes.RETRIEVAL_QUERY)).isEqualTo("How to deploy?");
        assertThat(retSpan.getAttributes().get(AgenticAttributes.RETRIEVAL_STORE_TYPE)).isEqualTo("pinecone");
        assertThat(retSpan.getAttributes().get(AgenticAttributes.RETRIEVAL_TOP_K)).isEqualTo(5L);
        assertThat(retSpan.getAttributes().get(AgenticAttributes.RETRIEVAL_DOCUMENT_COUNT)).isEqualTo(5L);
        assertThat(retSpan.getAttributes().get(AgenticAttributes.RETRIEVAL_RELEVANCE_SCORE_AVG)).isEqualTo(0.82);
        assertThat(retSpan.getAttributes().get(AgenticAttributes.RETRIEVAL_RELEVANCE_SCORE_MIN)).isEqualTo(0.61);
    }

    // --- Reranker ---

    @Test
    void reranker_tracksDocumentCounts() {
        try (AgentInvocation inv = tracer.invoke("Rerank documents")) {
            try (RerankerScope rr = inv.rerank("cohere-rerank-v3", 10)) {
                rr.outputDocuments(3).topScore(0.95).success();
            }
            inv.complete(true);
        }

        SpanData rrSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("agenttel.agentic.reranker"))
                .findFirst().orElseThrow();
        assertThat(rrSpan.getAttributes().get(AgenticAttributes.RERANKER_MODEL)).isEqualTo("cohere-rerank-v3");
        assertThat(rrSpan.getAttributes().get(AgenticAttributes.RERANKER_INPUT_DOCUMENTS)).isEqualTo(10L);
        assertThat(rrSpan.getAttributes().get(AgenticAttributes.RERANKER_OUTPUT_DOCUMENTS)).isEqualTo(3L);
        assertThat(rrSpan.getAttributes().get(AgenticAttributes.RERANKER_TOP_SCORE)).isEqualTo(0.95);
    }

    // --- Full RAG Pipeline ---

    @Test
    void fullRagPipeline_retrieverThenRerankerThenLlm() {
        try (AgentInvocation inv = tracer.invoke("Full RAG answer")) {
            // Step 1: Retrieve
            try (RetrieverScope ret = inv.retrieve("What is AgentTel?", "chromadb", 10)) {
                ret.documentCount(10).relevanceScoreAvg(0.7).success();
            }

            // Step 2: Rerank
            try (RerankerScope rr = inv.rerank("cross-encoder", 10)) {
                rr.outputDocuments(3).topScore(0.92).success();
            }

            // Step 3: LLM generates answer (would be a GenAI span in real usage)
            inv.step(StepType.ACTION, "Generate answer from top 3 docs");

            inv.complete(true);
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans.stream().filter(s -> s.getName().equals("agenttel.agentic.retriever")).count()).isEqualTo(1);
        assertThat(spans.stream().filter(s -> s.getName().equals("agenttel.agentic.reranker")).count()).isEqualTo(1);
        assertThat(spans.stream().filter(s -> s.getName().equals("agenttel.agentic.step")).count()).isEqualTo(1);
        assertThat(spans.stream().filter(s -> s.getName().equals("invoke_agent")).count()).isEqualTo(1);
    }

    // --- Combined: Code + Eval + Error Classification ---

    @Test
    void combinedScenario_codeGenWithEvalAndErrorClassification() {
        try (AgentInvocation inv = tracer.invoke("Generate and test code")) {
            inv.tools(List.of("code_interpreter", "test_runner"));

            // Generate code
            try (CodeExecutionScope code = inv.codeExecution("python", true)) {
                code.success();
            }

            // Evaluate the generated code
            try (EvaluationScope eval = inv.evaluate("test-coverage", "Does code pass tests?", EvalType.HEURISTIC)) {
                eval.score(0.6).feedback("Only 60% test coverage").fail("Below 80% threshold");
            }

            // Classify the failure
            inv.classifyError(ErrorSource.AGENT, "quality_below_threshold", true);
            inv.complete(false);
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        // code_execution + evaluate + invoke_agent = 3
        assertThat(spans).hasSize(3);

        SpanData invSpan = spans.stream()
                .filter(s -> s.getName().equals("invoke_agent"))
                .findFirst().orElseThrow();
        assertThat(invSpan.getAttributes().get(AgenticAttributes.ERROR_SOURCE)).isEqualTo("agent");
        assertThat(invSpan.getAttributes().get(AgenticAttributes.CAPABILITY_TOOL_COUNT)).isEqualTo(2L);
    }
}
