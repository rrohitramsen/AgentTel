package io.agenttel.genai.langchain4j;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.agenttel.genai.conventions.GenAiOperationName;
import io.agenttel.genai.trace.GenAiSpanBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.util.List;

/**
 * Tracing decorator for LangChain4j's EmbeddingModel.
 * Creates OTel spans for embedding operations.
 */
public class TracingEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final GenAiSpanBuilder spanBuilder;
    private final String modelName;
    private final String providerName;

    public TracingEmbeddingModel(EmbeddingModel delegate, GenAiSpanBuilder spanBuilder,
                                  String modelName, String providerName) {
        this.delegate = delegate;
        this.spanBuilder = spanBuilder;
        this.modelName = modelName;
        this.providerName = providerName != null ? providerName : "unknown";
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        Span span = spanBuilder.startSpan(GenAiOperationName.EMBEDDINGS, modelName, providerName, "langchain4j");
        try (Scope scope = span.makeCurrent()) {
            Response<List<Embedding>> response = delegate.embedAll(textSegments);

            TokenUsage usage = response.tokenUsage();
            long inputTokens = usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;

            spanBuilder.endSpanSuccess(span, modelName, null, inputTokens, 0, null);
            return response;
        } catch (Throwable t) {
            spanBuilder.endSpanError(span, t);
            throw t;
        }
    }
}
