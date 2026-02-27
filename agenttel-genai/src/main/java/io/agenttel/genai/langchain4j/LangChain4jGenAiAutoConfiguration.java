package io.agenttel.genai.langchain4j;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import io.agenttel.genai.trace.GenAiSpanBuilder;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that wraps LangChain4j model beans with tracing decorators.
 * Only activates when LangChain4j and OpenTelemetry are on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(name = "dev.langchain4j.model.chat.ChatLanguageModel")
@ConditionalOnBean(OpenTelemetry.class)
public class LangChain4jGenAiAutoConfiguration {

    private static final String INSTRUMENTATION_NAME = "io.agenttel.genai.langchain4j";

    @Bean
    public BeanPostProcessor langChain4jTracingBeanPostProcessor(OpenTelemetry openTelemetry) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                GenAiSpanBuilder spanBuilder = new GenAiSpanBuilder(
                        openTelemetry.getTracer(INSTRUMENTATION_NAME));

                if (bean instanceof TracingChatLanguageModel
                        || bean instanceof TracingStreamingChatLanguageModel
                        || bean instanceof TracingEmbeddingModel
                        || bean instanceof TracingContentRetriever) {
                    return bean;
                }

                if (bean instanceof ChatLanguageModel model) {
                    return new TracingChatLanguageModel(model, spanBuilder, "unknown", "unknown");
                }
                if (bean instanceof StreamingChatLanguageModel model) {
                    return new TracingStreamingChatLanguageModel(model, spanBuilder, "unknown", "unknown");
                }
                if (bean instanceof EmbeddingModel model) {
                    return new TracingEmbeddingModel(model, spanBuilder, "unknown", "unknown");
                }
                if (bean instanceof ContentRetriever retriever) {
                    return new TracingContentRetriever(retriever, spanBuilder, beanName);
                }

                return bean;
            }
        };
    }
}
