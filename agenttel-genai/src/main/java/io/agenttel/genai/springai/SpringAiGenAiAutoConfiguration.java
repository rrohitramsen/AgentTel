package io.agenttel.genai.springai;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Spring AI span enrichment.
 * Only activates when Spring AI is on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.ai.chat.model.ChatModel")
public class SpringAiGenAiAutoConfiguration {

    @Bean
    public SpringAiSpanEnricher springAiSpanEnricher() {
        return new SpringAiSpanEnricher();
    }
}
