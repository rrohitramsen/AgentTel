package io.agenttel.api.annotations;

import io.agenttel.api.ConsumptionPattern;
import io.agenttel.api.DependencyCriticality;
import io.agenttel.api.DependencyType;
import io.agenttel.api.ServiceTier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationRetentionTest {

    @AgentObservable(
            service = "test-service",
            team = "test-team",
            tier = ServiceTier.CRITICAL,
            domain = "testing",
            onCallChannel = "#test-oncall"
    )
    @DeclareDependency(
            name = "dep-1",
            type = DependencyType.EXTERNAL_API,
            criticality = DependencyCriticality.REQUIRED,
            timeoutMs = 5000,
            circuitBreaker = true
    )
    @DeclareDependency(
            name = "dep-2",
            type = DependencyType.DATABASE
    )
    @DeclareConsumer(
            name = "consumer-1",
            pattern = ConsumptionPattern.ASYNC,
            slaLatencyMs = 500
    )
    private static class AnnotatedTestClass {}

    @Test
    void agentObservableIsRetainedAtRuntime() {
        AgentObservable annotation = AnnotatedTestClass.class.getAnnotation(AgentObservable.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.service()).isEqualTo("test-service");
        assertThat(annotation.team()).isEqualTo("test-team");
        assertThat(annotation.tier()).isEqualTo(ServiceTier.CRITICAL);
        assertThat(annotation.domain()).isEqualTo("testing");
        assertThat(annotation.onCallChannel()).isEqualTo("#test-oncall");
    }

    @Test
    void declareDependencyIsRepeatableAndRetained() {
        DeclareDependency[] deps = AnnotatedTestClass.class.getAnnotationsByType(DeclareDependency.class);
        assertThat(deps).hasSize(2);

        assertThat(deps[0].name()).isEqualTo("dep-1");
        assertThat(deps[0].type()).isEqualTo(DependencyType.EXTERNAL_API);
        assertThat(deps[0].criticality()).isEqualTo(DependencyCriticality.REQUIRED);
        assertThat(deps[0].timeoutMs()).isEqualTo(5000);
        assertThat(deps[0].circuitBreaker()).isTrue();

        assertThat(deps[1].name()).isEqualTo("dep-2");
        assertThat(deps[1].type()).isEqualTo(DependencyType.DATABASE);
        assertThat(deps[1].criticality()).isEqualTo(DependencyCriticality.REQUIRED); // default
    }

    @Test
    void declareConsumerIsRetained() {
        DeclareConsumer[] consumers = AnnotatedTestClass.class.getAnnotationsByType(DeclareConsumer.class);
        assertThat(consumers).hasSize(1);
        assertThat(consumers[0].name()).isEqualTo("consumer-1");
        assertThat(consumers[0].pattern()).isEqualTo(ConsumptionPattern.ASYNC);
        assertThat(consumers[0].slaLatencyMs()).isEqualTo(500);
    }
}
