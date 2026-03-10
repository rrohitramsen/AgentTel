package io.agenttel.agentic.trace;

import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * AutoCloseable scope wrapping a retrieval span for RAG pipelines.
 * Tracks query, document count, relevance scores, and store type.
 */
public class RetrieverScope implements AutoCloseable {

    private final Span span;
    private final Scope scope;

    RetrieverScope(Span span, Context parentContext) {
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
     * Sets the number of documents retrieved.
     */
    public RetrieverScope documentCount(long count) {
        span.setAttribute(AgenticAttributes.RETRIEVAL_DOCUMENT_COUNT, count);
        return this;
    }

    /**
     * Sets the average relevance score of retrieved documents.
     */
    public RetrieverScope relevanceScoreAvg(double score) {
        span.setAttribute(AgenticAttributes.RETRIEVAL_RELEVANCE_SCORE_AVG, score);
        return this;
    }

    /**
     * Sets the minimum relevance score among retrieved documents.
     */
    public RetrieverScope relevanceScoreMin(double score) {
        span.setAttribute(AgenticAttributes.RETRIEVAL_RELEVANCE_SCORE_MIN, score);
        return this;
    }

    /**
     * Marks the retrieval as successful.
     */
    public RetrieverScope success() {
        span.setStatus(StatusCode.OK);
        return this;
    }

    /**
     * Marks the retrieval as failed.
     */
    public RetrieverScope error(Throwable t) {
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
