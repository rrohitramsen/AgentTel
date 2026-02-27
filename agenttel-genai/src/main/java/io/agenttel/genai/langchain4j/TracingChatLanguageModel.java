package io.agenttel.genai.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.agenttel.genai.conventions.GenAiOperationName;
import io.agenttel.genai.trace.GenAiSpanBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.util.Collections;
import java.util.List;

/**
 * Tracing decorator for LangChain4j's ChatLanguageModel.
 * Wraps the delegate model and creates OTel spans for each chat invocation.
 */
public class TracingChatLanguageModel implements ChatLanguageModel {

    private final ChatLanguageModel delegate;
    private final GenAiSpanBuilder spanBuilder;
    private final String modelName;
    private final String providerName;

    public TracingChatLanguageModel(ChatLanguageModel delegate, GenAiSpanBuilder spanBuilder,
                                    String modelName, String providerName) {
        this.delegate = delegate;
        this.spanBuilder = spanBuilder;
        this.modelName = modelName;
        this.providerName = providerName != null ? providerName : "unknown";
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        Span span = spanBuilder.startSpan(GenAiOperationName.CHAT, modelName, providerName, "langchain4j");
        try (Scope scope = span.makeCurrent()) {
            ChatResponse response = delegate.chat(chatRequest);
            endSpanWithResponse(span, response);
            return response;
        } catch (Throwable t) {
            spanBuilder.endSpanError(span, t);
            throw t;
        }
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        Span span = spanBuilder.startSpan(GenAiOperationName.CHAT, modelName, providerName, "langchain4j");
        try (Scope scope = span.makeCurrent()) {
            Response<AiMessage> response = delegate.generate(messages);
            endSpanWithLegacyResponse(span, response);
            return response;
        } catch (Throwable t) {
            spanBuilder.endSpanError(span, t);
            throw t;
        }
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

    private void endSpanWithLegacyResponse(Span span, Response<AiMessage> response) {
        TokenUsage usage = response.tokenUsage();
        long inputTokens = usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
        long outputTokens = usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;
        FinishReason finishReason = response.finishReason();
        List<String> finishReasons = finishReason != null
                ? Collections.singletonList(finishReason.name().toLowerCase())
                : null;

        spanBuilder.endSpanSuccess(span, modelName, null, inputTokens, outputTokens, finishReasons);
    }
}
