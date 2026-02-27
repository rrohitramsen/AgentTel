package io.agenttel.genai.anthropic;

import com.anthropic.client.AnthropicClient;
import io.agenttel.genai.trace.GenAiSpanBuilder;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that wraps AnthropicClient beans with tracing.
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.anthropic.client.AnthropicClient")
@ConditionalOnBean(OpenTelemetry.class)
public class AnthropicGenAiAutoConfiguration {

    private static final String INSTRUMENTATION_NAME = "io.agenttel.genai.anthropic";

    @Bean
    public BeanPostProcessor anthropicTracingBeanPostProcessor(OpenTelemetry openTelemetry) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof AnthropicClient client && !(bean instanceof TracingAnthropicClient)) {
                    GenAiSpanBuilder spanBuilder = new GenAiSpanBuilder(
                            openTelemetry.getTracer(INSTRUMENTATION_NAME));
                    return new TracingAnthropicClient(client, spanBuilder);
                }
                return bean;
            }
        };
    }
}
