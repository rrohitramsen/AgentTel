package io.agenttel.genai.langchain4j;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TracingEmbeddingModelTest {

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private TracingEmbeddingModel tracingModel;
    private EmbeddingModel mockDelegate;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();

        GenAiSpanBuilder spanBuilder = new GenAiSpanBuilder(tracerProvider.get("test"));
        mockDelegate = mock(EmbeddingModel.class);
        tracingModel = new TracingEmbeddingModel(mockDelegate, spanBuilder, "text-embedding-3-small", "openai");
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void embedAllCreatesSpanWithEmbeddingsOperation() {
        Embedding mockEmbedding = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});
        Response<List<Embedding>> mockResponse = new Response<>(
                List.of(mockEmbedding),
                new TokenUsage(50),
                null
        );
        when(mockDelegate.embedAll(anyList())).thenReturn(mockResponse);

        List<TextSegment> segments = List.of(TextSegment.from("test text"));
        tracingModel.embedAll(segments);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("embeddings text-embedding-3-small");
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_OPERATION_NAME)).isEqualTo("embeddings");
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_SYSTEM)).isEqualTo("openai");
        assertThat(span.getAttributes().get(AgentTelGenAiAttributes.GENAI_FRAMEWORK)).isEqualTo("langchain4j");
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_USAGE_INPUT_TOKENS)).isEqualTo(50L);
    }
}
