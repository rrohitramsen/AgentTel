package io.agenttel.genai.langchain4j;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
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

class TracingContentRetrieverTest {

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private TracingContentRetriever tracingRetriever;
    private ContentRetriever mockDelegate;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();

        GenAiSpanBuilder spanBuilder = new GenAiSpanBuilder(tracerProvider.get("test"));
        mockDelegate = mock(ContentRetriever.class);
        tracingRetriever = new TracingContentRetriever(mockDelegate, spanBuilder, "vector_store");
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    void retrieveCreatesSpanWithSourceCount() {
        List<Content> mockResults = List.of(
                Content.from("result 1"),
                Content.from("result 2"),
                Content.from("result 3")
        );
        when(mockDelegate.retrieve(any(Query.class))).thenReturn(mockResults);

        Query query = Query.from("test query");
        List<Content> results = tracingRetriever.retrieve(query);

        assertThat(results).hasSize(3);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("retrieve vector_store");
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(GenAiAttributes.GEN_AI_OPERATION_NAME)).isEqualTo("retrieve");
        assertThat(span.getAttributes().get(AgentTelGenAiAttributes.GENAI_RAG_SOURCE_COUNT)).isEqualTo(3L);
    }

    @Test
    void retrieveRecordsErrorOnException() {
        when(mockDelegate.retrieve(any(Query.class))).thenThrow(new RuntimeException("Connection failed"));

        try {
            tracingRetriever.retrieve(Query.from("test"));
        } catch (RuntimeException ignored) {
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode())
                .isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
    }
}
