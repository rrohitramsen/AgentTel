package io.agenttel.genai.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.agenttel.genai.conventions.GenAiOperationName;
import io.agenttel.genai.trace.GenAiSpanBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.util.Collections;
import java.util.List;

/**
 * Tracing decorator for LangChain4j's StreamingChatLanguageModel.
 * Creates a span on invocation and ends it when streaming completes or errors.
 */
public class TracingStreamingChatLanguageModel implements StreamingChatLanguageModel {

    private final StreamingChatLanguageModel delegate;
    private final GenAiSpanBuilder spanBuilder;
    private final String modelName;
    private final String providerName;

    public TracingStreamingChatLanguageModel(StreamingChatLanguageModel delegate,
                                              GenAiSpanBuilder spanBuilder,
                                              String modelName, String providerName) {
        this.delegate = delegate;
        this.spanBuilder = spanBuilder;
        this.modelName = modelName;
        this.providerName = providerName != null ? providerName : "unknown";
    }

    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        Span span = spanBuilder.startSpan(GenAiOperationName.CHAT, modelName, providerName, "langchain4j");
        Scope scope = span.makeCurrent();

        delegate.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onPartialResponse(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                try {
                    endSpanWithResponse(span, completeResponse);
                } finally {
                    scope.close();
                }
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                try {
                    spanBuilder.endSpanError(span, error);
                } finally {
                    scope.close();
                }
                handler.onError(error);
            }
        });
    }

    @SuppressWarnings("removal")
    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        delegate.generate(messages, handler);
    }

    private void endSpanWithResponse(Span span, ChatResponse response) {
        ChatResponseMetadata metadata = response.metadata();
        String responseModel = metadata != null ? metadata.modelName() : null;
        String responseId = metadata != null ? metadata.id() : null;
        TokenUsage usage = response.tokenUsage();
        long inputTokens = usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
        long outputTokens = usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;
        FinishReason finishReason = response.finishReason();
        List<String> finishReasons = finishReason != null
                ? Collections.singletonList(finishReason.name().toLowerCase())
                : null;

        spanBuilder.endSpanSuccess(span, responseModel != null ? responseModel : modelName,
                responseId, inputTokens, outputTokens, finishReasons);
    }
}
