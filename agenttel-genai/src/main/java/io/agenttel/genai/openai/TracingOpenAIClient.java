package io.agenttel.genai.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.ClientOptions;
import com.openai.core.RequestOptions;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionDeleteParams;
import com.openai.models.chat.completions.ChatCompletionDeleted;
import com.openai.models.chat.completions.ChatCompletionListPage;
import com.openai.models.chat.completions.ChatCompletionListParams;
import com.openai.models.chat.completions.ChatCompletionRetrieveParams;
import com.openai.models.chat.completions.ChatCompletionUpdateParams;
import com.openai.models.completions.CompletionUsage;
import com.openai.services.blocking.*;
import com.openai.services.blocking.chat.ChatCompletionService;
import io.agenttel.genai.conventions.GenAiOperationName;
import io.agenttel.genai.trace.GenAiSpanBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Tracing decorator for the official OpenAI Java SDK client.
 * Intercepts chat completion calls to produce OTel GenAI spans.
 */
public class TracingOpenAIClient implements OpenAIClient {

    private final OpenAIClient delegate;
    private final GenAiSpanBuilder spanBuilder;

    public TracingOpenAIClient(OpenAIClient delegate, GenAiSpanBuilder spanBuilder) {
        this.delegate = delegate;
        this.spanBuilder = spanBuilder;
    }

    @Override
    public OpenAIClientAsync async() {
        return delegate.async();
    }

    @Override
    public WithRawResponse withRawResponse() {
        return delegate.withRawResponse();
    }

    @Override
    public OpenAIClient withOptions(Consumer<ClientOptions.Builder> modifier) {
        return new TracingOpenAIClient(delegate.withOptions(modifier), spanBuilder);
    }

    @Override
    public CompletionService completions() {
        return delegate.completions();
    }

    @Override
    public ChatService chat() {
        return new TracingChatService(delegate.chat());
    }

    @Override
    public EmbeddingService embeddings() {
        return delegate.embeddings();
    }

    @Override
    public FileService files() {
        return delegate.files();
    }

    @Override
    public ImageService images() {
        return delegate.images();
    }

    @Override
    public AudioService audio() {
        return delegate.audio();
    }

    @Override
    public ModerationService moderations() {
        return delegate.moderations();
    }

    @Override
    public ModelService models() {
        return delegate.models();
    }

    @Override
    public FineTuningService fineTuning() {
        return delegate.fineTuning();
    }

    @Override
    public GraderService graders() {
        return delegate.graders();
    }

    @Override
    public VectorStoreService vectorStores() {
        return delegate.vectorStores();
    }

    @Override
    public WebhookService webhooks() {
        return delegate.webhooks();
    }

    @Override
    public BetaService beta() {
        return delegate.beta();
    }

    @Override
    public BatchService batches() {
        return delegate.batches();
    }

    @Override
    public UploadService uploads() {
        return delegate.uploads();
    }

    @Override
    public ResponseService responses() {
        return delegate.responses();
    }

    @Override
    public RealtimeService realtime() {
        return delegate.realtime();
    }

    @Override
    public ConversationService conversations() {
        return delegate.conversations();
    }

    @Override
    public EvalService evals() {
        return delegate.evals();
    }

    @Override
    public ContainerService containers() {
        return delegate.containers();
    }

    @Override
    public void close() {
        delegate.close();
    }

    private class TracingChatService implements ChatService {
        private final ChatService delegateChat;

        TracingChatService(ChatService delegateChat) {
            this.delegateChat = delegateChat;
        }

        @Override
        public WithRawResponse withRawResponse() {
            return delegateChat.withRawResponse();
        }

        @Override
        public ChatService withOptions(Consumer<ClientOptions.Builder> modifier) {
            return new TracingChatService(delegateChat.withOptions(modifier));
        }

        @Override
        public ChatCompletionService completions() {
            return new TracingChatCompletionService(delegateChat.completions());
        }
    }

    private class TracingChatCompletionService implements ChatCompletionService {
        private final ChatCompletionService delegateCompletions;

        TracingChatCompletionService(ChatCompletionService delegateCompletions) {
            this.delegateCompletions = delegateCompletions;
        }

        @Override
        public WithRawResponse withRawResponse() {
            return delegateCompletions.withRawResponse();
        }

        @Override
        public ChatCompletionService withOptions(Consumer<ClientOptions.Builder> modifier) {
            return new TracingChatCompletionService(delegateCompletions.withOptions(modifier));
        }

        @Override
        public com.openai.services.blocking.chat.completions.MessageService messages() {
            return delegateCompletions.messages();
        }

        @Override
        public ChatCompletion retrieve(ChatCompletionRetrieveParams params, RequestOptions requestOptions) {
            return delegateCompletions.retrieve(params, requestOptions);
        }

        @Override
        public ChatCompletion update(ChatCompletionUpdateParams params, RequestOptions requestOptions) {
            return delegateCompletions.update(params, requestOptions);
        }

        @Override
        public ChatCompletionListPage list(ChatCompletionListParams params, RequestOptions requestOptions) {
            return delegateCompletions.list(params, requestOptions);
        }

        @Override
        public ChatCompletionDeleted delete(ChatCompletionDeleteParams params, RequestOptions requestOptions) {
            return delegateCompletions.delete(params, requestOptions);
        }

        @Override
        public StreamResponse<ChatCompletionChunk> createStreaming(ChatCompletionCreateParams params, RequestOptions requestOptions) {
            return delegateCompletions.createStreaming(params, requestOptions);
        }

        @Override
        public ChatCompletion create(ChatCompletionCreateParams params, RequestOptions requestOptions) {
            String model = params.model().toString();
            Span span = spanBuilder.startSpan(GenAiOperationName.CHAT, model, "openai", null);

            try (Scope scope = span.makeCurrent()) {
                ChatCompletion completion = delegateCompletions.create(params, requestOptions);

                String responseModel = completion.model();
                String responseId = completion.id();

                long promptTokens = 0;
                long completionTokens = 0;
                Optional<CompletionUsage> usage = completion.usage();
                if (usage.isPresent()) {
                    promptTokens = usage.get().promptTokens();
                    completionTokens = usage.get().completionTokens();
                }

                List<String> finishReasons = new ArrayList<>();
                for (ChatCompletion.Choice choice : completion.choices()) {
                    finishReasons.add(choice.finishReason().toString().toLowerCase());
                }

                spanBuilder.endSpanSuccess(span, responseModel, responseId,
                        promptTokens, completionTokens,
                        finishReasons.isEmpty() ? null : finishReasons);

                return completion;
            } catch (Throwable t) {
                spanBuilder.endSpanError(span, t);
                throw t;
            }
        }
    }
}
