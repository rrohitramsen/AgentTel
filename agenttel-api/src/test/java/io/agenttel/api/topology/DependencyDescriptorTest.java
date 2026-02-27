package io.agenttel.api.topology;

import io.agenttel.api.DependencyCriticality;
import io.agenttel.api.DependencyType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyDescriptorTest {

    @Test
    void recordConstructionWorks() {
        var descriptor = new DependencyDescriptor(
                "payment-gateway",
                DependencyType.EXTERNAL_API,
                DependencyCriticality.REQUIRED,
                "https",
                5000,
                true,
                "cached_response",
                "https://gateway.pay.com/health"
        );

        assertThat(descriptor.name()).isEqualTo("payment-gateway");
        assertThat(descriptor.type()).isEqualTo(DependencyType.EXTERNAL_API);
        assertThat(descriptor.criticality()).isEqualTo(DependencyCriticality.REQUIRED);
        assertThat(descriptor.protocol()).isEqualTo("https");
        assertThat(descriptor.timeoutMs()).isEqualTo(5000);
        assertThat(descriptor.circuitBreaker()).isTrue();
        assertThat(descriptor.fallback()).isEqualTo("cached_response");
        assertThat(descriptor.healthEndpoint()).isEqualTo("https://gateway.pay.com/health");
    }

    @Test
    void builderCreatesCorrectDescriptor() {
        var descriptor = DependencyDescriptor.builder("postgres-db", DependencyType.DATABASE)
                .criticality(DependencyCriticality.REQUIRED)
                .protocol("jdbc")
                .timeoutMs(3000)
                .build();

        assertThat(descriptor.name()).isEqualTo("postgres-db");
        assertThat(descriptor.type()).isEqualTo(DependencyType.DATABASE);
        assertThat(descriptor.criticality()).isEqualTo(DependencyCriticality.REQUIRED);
        assertThat(descriptor.protocol()).isEqualTo("jdbc");
        assertThat(descriptor.timeoutMs()).isEqualTo(3000);
        assertThat(descriptor.circuitBreaker()).isFalse();
    }

    @Test
    void builderDefaultsAreSensible() {
        var descriptor = DependencyDescriptor.builder("redis", DependencyType.CACHE).build();

        assertThat(descriptor.criticality()).isEqualTo(DependencyCriticality.REQUIRED);
        assertThat(descriptor.protocol()).isEmpty();
        assertThat(descriptor.timeoutMs()).isZero();
        assertThat(descriptor.circuitBreaker()).isFalse();
        assertThat(descriptor.fallback()).isEmpty();
        assertThat(descriptor.healthEndpoint()).isEmpty();
    }
}
