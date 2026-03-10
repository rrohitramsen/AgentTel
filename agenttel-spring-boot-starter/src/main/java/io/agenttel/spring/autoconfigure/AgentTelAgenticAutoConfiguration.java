package io.agenttel.spring.autoconfigure;

import io.agenttel.agentic.config.AgentConfig;
import io.agenttel.agentic.config.AgentConfigRegistry;
import io.agenttel.agentic.cost.AgentCostAggregator;
import io.agenttel.agentic.trace.AgentTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the AgentTel agentic layer (agent tracing, config registry,
 * cost aggregation, and {@code @AgentMethod} AOP support).
 * Only activates when agenttel-agentic is on the classpath.
 */
@AutoConfiguration(after = AgentTelAutoConfiguration.class)
@ConditionalOnClass(name = "io.agenttel.agentic.trace.AgentTracer")
public class AgentTelAgenticAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentConfigRegistry agentConfigRegistry(AgentTelProperties props) {
        AgentConfigRegistry registry = new AgentConfigRegistry();
        var agenticProps = props.getAgentic();

        for (var entry : agenticProps.getAgents().entrySet()) {
            String name = entry.getKey();
            var agent = entry.getValue();
            registry.register(name, AgentConfig.builder(name)
                    .type(agent.getType())
                    .framework(agent.getFramework())
                    .version(agent.getVersion())
                    .maxSteps(agent.getMaxSteps() > 0 ? agent.getMaxSteps() : agenticProps.getDefaultMaxSteps())
                    .loopThreshold(agent.getLoopThreshold() > 0 ? agent.getLoopThreshold() : agenticProps.getLoopThreshold())
                    .costBudgetUsd(agent.getCostBudgetUsd())
                    .build());
        }
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentTracer agentTelAgentTracer(OpenTelemetry otel, AgentConfigRegistry configRegistry) {
        return AgentTracer.create(otel)
                .configRegistry(configRegistry)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentMethodAspect agentMethodAspect(AgentTracer tracer, AgentConfigRegistry configRegistry) {
        return new AgentMethodAspect(tracer, configRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentCostAggregator agentTelCostAggregator() {
        return new AgentCostAggregator();
    }

    @Bean
    public AutoConfigurationCustomizerProvider agentTelAgenticOtelCustomizer(
            AgentCostAggregator costAggregator) {
        return customizer -> customizer.addTracerProviderCustomizer(
                (builder, config) -> builder.addSpanProcessor(costAggregator));
    }
}
