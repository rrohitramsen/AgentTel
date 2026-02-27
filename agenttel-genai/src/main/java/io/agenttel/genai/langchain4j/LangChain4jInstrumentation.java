package io.agenttel.genai.langchain4j;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import io.agenttel.genai.trace.GenAiSpanBuilder;
import io.opentelemetry.api.OpenTelemetry;

/**
 * Static factory for creating tracing-instrumented LangChain4j model wrappers.
 */
public final class LangChain4jInstrumentation {
    private LangChain4jInstrumentation() {}

    private static final String INSTRUMENTATION_NAME = "io.agenttel.genai.langchain4j";

    public static TracingChatLanguageModel instrument(ChatLanguageModel model,
                                                       OpenTelemetry openTelemetry,
                                                       String modelName,
                                                       String providerName) {
        GenAiSpanBuilder spanBuilder = new GenAiSpanBuilder(
                openTelemetry.getTracer(INSTRUMENTATION_NAME));
        return new TracingChatLanguageModel(model, spanBuilder, modelName, providerName);
    }

    public static TracingStreamingChatLanguageModel instrument(StreamingChatLanguageModel model,
                                                                OpenTelemetry openTelemetry,
                                                                String modelName,
                                                                String providerName) {
        GenAiSpanBuilder spanBuilder = new GenAiSpanBuilder(
                openTelemetry.getTracer(INSTRUMENTATION_NAME));
        return new TracingStreamingChatLanguageModel(model, spanBuilder, modelName, providerName);
    }

    public static TracingEmbeddingModel instrument(EmbeddingModel model,
                                                    OpenTelemetry openTelemetry,
                                                    String modelName,
                                                    String providerName) {
        GenAiSpanBuilder spanBuilder = new GenAiSpanBuilder(
                openTelemetry.getTracer(INSTRUMENTATION_NAME));
        return new TracingEmbeddingModel(model, spanBuilder, modelName, providerName);
    }

    public static TracingContentRetriever instrument(ContentRetriever retriever,
                                                      OpenTelemetry openTelemetry,
                                                      String retrieverName) {
        GenAiSpanBuilder spanBuilder = new GenAiSpanBuilder(
                openTelemetry.getTracer(INSTRUMENTATION_NAME));
        return new TracingContentRetriever(retriever, spanBuilder, retrieverName);
    }
}
