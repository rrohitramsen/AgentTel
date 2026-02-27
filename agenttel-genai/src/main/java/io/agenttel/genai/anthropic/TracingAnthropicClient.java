package io.agenttel.genai.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCountTokensParams;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageTokensCount;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.CompletionService;
import com.anthropic.services.blocking.MessageService;
import com.anthropic.services.blocking.ModelService;
import com.anthropic.services.blocking.BetaService;
import com.anthropic.core.RequestOptions;
import io.agenttel.genai.conventions.GenAiOperationName;
import io.agenttel.genai.trace.GenAiSpanBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Tracing decorator for the official Anthropic Java SDK client.
 * Intercepts message creation calls to produce OTel GenAI spans.
 */
public class TracingAnthropicClient implements AnthropicClient {

    private final AnthropicClient delegate;
    private final GenAiSpanBuilder spanBuilder;

    public TracingAnthropicClient(AnthropicClient delegate, GenAiSpanBuilder spanBuilder) {
        this.delegate = delegate;
        this.spanBuilder = spanBuilder;
    }

    @Override
    public com.anthropic.client.AnthropicClientAsync async() {
        return delegate.async();
    }

    @Override
    public WithRawResponse withRawResponse() {
        return delegate.withRawResponse();
    }

    @Override
    public CompletionService completions() {
        return delegate.completions();
    }

    @Override
    public MessageService messages() {
        MessageService delegateMessages = delegate.messages();
        return new TracingMessageService(delegateMessages);
    }

    @Override
    public ModelService models() {
        return delegate.models();
    }

    @Override
    public BetaService beta() {
        return delegate.beta();
    }

    @Override
    public void close() {
        delegate.close();
    }

    private class TracingMessageService implements MessageService {
        private final MessageService delegateService;

        TracingMessageService(MessageService delegateService) {
            this.delegateService = delegateService;
        }

        @Override
        public WithRawResponse withRawResponse() {
            return delegateService.withRawResponse();
        }

        @Override
        public com.anthropic.services.blocking.messages.BatchService batches() {
            return delegateService.batches();
        }

        @Override
        public MessageTokensCount countTokens(MessageCountTokensParams params, RequestOptions requestOptions) {
            return delegateService.countTokens(params, requestOptions);
        }

        @Override
        public StreamResponse<RawMessageStreamEvent> createStreaming(MessageCreateParams params, RequestOptions requestOptions) {
            return delegateService.createStreaming(params, requestOptions);
        }

        @Override
        public Message create(MessageCreateParams params, RequestOptions requestOptions) {
            String model = params.model().toString();
            Span span = spanBuilder.startSpan(GenAiOperationName.CHAT, model, "anthropic", null);

            try (Scope scope = span.makeCurrent()) {
                Message response = delegateService.create(params, requestOptions);

                Usage usage = response.usage();
                long inputTokens = usage.inputTokens();
                long outputTokens = usage.outputTokens();

                String responseModel = response.model().toString();
                String responseId = response.id();
                Optional<StopReason> stopReason = response.stopReason();
                List<String> finishReasons = stopReason
                        .map(sr -> Collections.singletonList(sr.toString().toLowerCase()))
                        .orElse(null);

                spanBuilder.endSpanSuccess(span, responseModel, responseId,
                        inputTokens, outputTokens, finishReasons);

                return response;
            } catch (Throwable t) {
                spanBuilder.endSpanError(span, t);
                throw t;
            }
        }
    }
}
