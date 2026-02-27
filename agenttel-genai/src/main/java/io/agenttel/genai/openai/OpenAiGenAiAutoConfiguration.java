package io.agenttel.genai.openai;

import com.openai.client.OpenAIClient;
import io.agenttel.genai.trace.GenAiSpanBuilder;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that wraps OpenAIClient beans with tracing.
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.openai.client.OpenAIClient")
@ConditionalOnBean(OpenTelemetry.class)
public class OpenAiGenAiAutoConfiguration {

    private static final String INSTRUMENTATION_NAME = "io.agenttel.genai.openai";

    @Bean
    public BeanPostProcessor openAiTracingBeanPostProcessor(OpenTelemetry openTelemetry) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof OpenAIClient client && !(bean instanceof TracingOpenAIClient)) {
                    GenAiSpanBuilder spanBuilder = new GenAiSpanBuilder(
                            openTelemetry.getTracer(INSTRUMENTATION_NAME));
                    return new TracingOpenAIClient(client, spanBuilder);
                }
                return bean;
            }
        };
    }
}
