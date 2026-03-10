package io.agenttel.spring.autoconfigure;

import io.agenttel.agentic.cost.AgentCostAggregator;
import io.agenttel.agentic.trace.AgentTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTelAgenticAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AgentTelAutoConfiguration.class,
                    AgentTelAgenticAutoConfiguration.class))
            .withUserConfiguration(OTelTestConfig.class);

    @Configuration
    static class OTelTestConfig {
        @Bean
        OpenTelemetry openTelemetry() {
            return OpenTelemetry.noop();
        }
    }

    @Configuration
    static class CustomAgentTracerConfig {
        @Bean
        AgentTracer agentTracer(OpenTelemetry otel) {
            return AgentTracer.create(otel)
                    .agentName("custom-agent")
                    .build();
        }
    }

    @Test
    void createsAgentTracerAndCostAggregatorBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AgentTracer.class);
            assertThat(context).hasSingleBean(AgentCostAggregator.class);
        });
    }

    @Test
    void doesNotCreateBeansWhenAgenticNotOnClasspath() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AgentTelAutoConfiguration.class))
                .withUserConfiguration(OTelTestConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AgentTracer.class);
                    assertThat(context).doesNotHaveBean(AgentCostAggregator.class);
                });
    }

    @Test
    void customAgentTracerBeanTakesPrecedence() {
        contextRunner
                .withUserConfiguration(CustomAgentTracerConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentTracer.class);
                    AgentTracer tracer = context.getBean(AgentTracer.class);
                    assertThat(tracer).isNotNull();
                });
    }

    @Test
    void agentTracerIsFunctional() {
        contextRunner.run(context -> {
            AgentTracer tracer = context.getBean(AgentTracer.class);
            assertThat(tracer).isNotNull();
            // Verify the tracer can create invocations without error
            try (var invocation = tracer.invoke("test goal")) {
                invocation.complete(true);
            }
        });
    }

    @Test
    void costAggregatorIsSpanProcessor() {
        contextRunner.run(context -> {
            AgentCostAggregator aggregator = context.getBean(AgentCostAggregator.class);
            assertThat(aggregator).isInstanceOf(SpanProcessor.class);
        });
    }
}
