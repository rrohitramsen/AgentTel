package io.agenttel.genai.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.agenttel.genai.conventions.AgentTelGenAiAttributes;
import io.agenttel.genai.conventions.GenAiAttributes;
import io.agenttel.genai.trace.GenAiSpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TracingChatLanguageModelTest {

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private TracingChatLanguageModel tracingModel;
    private ChatLanguageModel mockDelegate;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();

        GenAiSpanBuilder spanBuilder = new GenAiSpanBuilder(tracerProvider.get("test"));
        mockDelegate = mock(ChatLanguageModel.class);
        tracingModel = new TracingChatLanguageModel(mockDelegate, spanBuilder, "gpt-4o", "openai");
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void chatCreatesSpanWithCorrectAttributes() {
        // Set up mock response
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .modelName("gpt-4o-2024-11-20")
                .id("chatcmpl-abc123")
                .tokenUsage(new TokenUsage(100, 50))
                .finishReason(FinishReason.STOP)
                .build();
        ChatResponse mockResponse = ChatResponse.builder()
                .aiMessage(new AiMessage("Hello!"))
                .metadata(metadata)
                .build();
        when(mockDelegate.chat(any(ChatRequest.class))).thenReturn(mockResponse);

        // Execute
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hi")))
                .build();
        ChatResponse response = tracingModel.chat(request);

        // Verify span
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("chat gpt-4o");
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_OPERATION_NAME)).isEqualTo("chat");
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM)).isEqualTo("openai");
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_REQUEST_MODEL)).isEqualTo("gpt-4o");
        assertThat(span.getAttributes().get(AgentTelGenAiAttributes.GENAI_FRAMEWORK)).isEqualTo("langchain4j");
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_RESPONSE_MODEL)).isEqualTo("gpt-4o-2024-11-20");
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_RESPONSE_ID)).isEqualTo("chatcmpl-abc123");
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS)).isEqualTo(100L);
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_OUTPUT_TOKENS)).isEqualTo(50L);
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_RESPONSE_FINISH_REASONS))
                .isEqualTo(List.of("stop"));

        // Verify cost is calculated
        assertThat(span.getAttributes().get(AgentTelGenAiAttributes.GENAI_COST_USD)).isGreaterThan(0);
    }

    @Test
    void chatRecordsErrorOnException() {
        when(mockDelegate.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("API error"));

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hi")))
                .build();

        try {
            tracingModel.chat(request);
        } catch (RuntimeException ignored) {
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode())
                .isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
    }
}
