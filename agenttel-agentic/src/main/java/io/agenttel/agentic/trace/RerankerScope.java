package io.agenttel.agentic.trace;

import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * AutoCloseable scope wrapping a reranker span for RAG pipelines.
 * Tracks input/output document counts, model, and top relevance score.
 */
public class RerankerScope implements AutoCloseable {

    private final Span span;
    private final Scope scope;

    RerankerScope(Span span, Context parentContext) {
        this.span = span;
        this.scope = parentContext != null
                ? parentContext.with(span).makeCurrent()
                : Context.current().with(span).makeCurrent();
    }

    /**
     * Returns the underlying OTel span.
     */
    public Span span() {
        return span;
    }

    /**
     * Sets the number of documents output after reranking.
     */
    public RerankerScope outputDocuments(long count) {
        span.setAttribute(AgenticAttributes.RERANKER_OUTPUT_DOCUMENTS, count);
        return this;
    }

    /**
     * Sets the top relevance score after reranking.
     */
    public RerankerScope topScore(double score) {
        span.setAttribute(AgenticAttributes.RERANKER_TOP_SCORE, score);
        return this;
    }

    /**
     * Marks the reranking as successful.
     */
    public RerankerScope success() {
        span.setStatus(StatusCode.OK);
        return this;
    }

    /**
     * Marks the reranking as failed.
     */
    public RerankerScope error(Throwable t) {
        span.setStatus(StatusCode.ERROR, t.getMessage());
        span.recordException(t);
        return this;
    }

    @Override
    public void close() {
        scope.close();
        span.end();
    }
}
