package io.agenttel.core.topology;

import io.agenttel.api.ConsumptionPattern;
import io.agenttel.api.DependencyCriticality;
import io.agenttel.api.DependencyType;
import io.agenttel.api.ServiceTier;
import io.agenttel.api.topology.ConsumerDescriptor;
import io.agenttel.api.topology.DependencyDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyRegistryTest {

    private TopologyRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new TopologyRegistry();
    }

    @Test
    void storesAndRetrievesMetadata() {
        registry.setTeam("payments-platform");
        registry.setTier(ServiceTier.CRITICAL);
        registry.setDomain("commerce");
        registry.setOnCallChannel("#payments-oncall");

        assertThat(registry.getTeam()).isEqualTo("payments-platform");
        assertThat(registry.getTier()).isEqualTo(ServiceTier.CRITICAL);
        assertThat(registry.getDomain()).isEqualTo("commerce");
        assertThat(registry.getOnCallChannel()).isEqualTo("#payments-oncall");
    }

    @Test
    void registerAndRetrieveDependencies() {
        var dep = DependencyDescriptor.builder("payment-gateway", DependencyType.EXTERNAL_API)
                .criticality(DependencyCriticality.REQUIRED)
                .timeoutMs(5000)
                .circuitBreaker(true)
                .build();

        registry.registerDependency(dep);

        assertThat(registry.getDependencies()).hasSize(1);
        assertThat(registry.getDependency("payment-gateway")).isPresent();
        assertThat(registry.getDependency("nonexistent")).isEmpty();
    }

    @Test
    void registerAndRetrieveConsumers() {
        var consumer = ConsumerDescriptor.of("notification-service", ConsumptionPattern.ASYNC, 500);
        registry.registerConsumer(consumer);

        assertThat(registry.getConsumers()).hasSize(1);
        assertThat(registry.getConsumers().get(0).name()).isEqualTo("notification-service");
    }

    @Test
    void serializesDependenciesToJson() {
        registry.registerDependency(DependencyDescriptor.builder("pg", DependencyType.DATABASE)
                .protocol("jdbc")
                .build());

        String json = registry.serializeDependenciesToJson();
        assertThat(json).contains("\"name\":\"pg\"");
        assertThat(json).contains("\"type\":\"database\"");
        assertThat(json).contains("\"protocol\":\"jdbc\"");
    }

    @Test
    void serializesConsumersToJson() {
        registry.registerConsumer(ConsumerDescriptor.of("notify", ConsumptionPattern.ASYNC));

        String json = registry.serializeConsumersToJson();
        assertThat(json).contains("\"name\":\"notify\"");
        assertThat(json).contains("\"consumption_pattern\":\"async\"");
    }

    @Test
    void emptyRegistrySerializesToEmptyArrays() {
        assertThat(registry.serializeDependenciesToJson()).isEqualTo("[]");
        assertThat(registry.serializeConsumersToJson()).isEqualTo("[]");
    }
}
