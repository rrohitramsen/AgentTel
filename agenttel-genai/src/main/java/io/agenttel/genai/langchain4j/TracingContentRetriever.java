package io.agenttel.genai.langchain4j;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.agenttel.genai.conventions.AgentTelGenAiAttributes;
import io.agenttel.genai.conventions.GenAiOperationName;
import io.agenttel.genai.trace.GenAiSpanBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.util.List;

/**
 * Tracing decorator for LangChain4j's ContentRetriever.
 * Creates OTel spans for RAG retrieval operations with source count tracking.
 */
public class TracingContentRetriever implements ContentRetriever {

    private final ContentRetriever delegate;
    private final GenAiSpanBuilder spanBuilder;
    private final String retrieverName;

    public TracingContentRetriever(ContentRetriever delegate, GenAiSpanBuilder spanBuilder,
                                    String retrieverName) {
        this.delegate = delegate;
        this.spanBuilder = spanBuilder;
        this.retrieverName = retrieverName != null ? retrieverName : "content_retriever";
    }

    @Override
    public List<Content> retrieve(Query query) {
        Span span = spanBuilder.startSpan(GenAiOperationName.RETRIEVE, retrieverName, "langchain4j", "langchain4j");
        try (Scope scope = span.makeCurrent()) {
            List<Content> results = delegate.retrieve(query);

            span.setAttribute(AgentTelGenAiAttributes.GENAI_RAG_SOURCE_COUNT, (long) results.size());

            span.end();
            return results;
        } catch (Throwable t) {
            spanBuilder.endSpanError(span, t);
            throw t;
        }
    }
}
