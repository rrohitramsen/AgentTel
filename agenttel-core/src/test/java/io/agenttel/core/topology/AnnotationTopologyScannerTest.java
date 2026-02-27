package io.agenttel.core.topology;

import io.agenttel.api.ConsumptionPattern;
import io.agenttel.api.DependencyCriticality;
import io.agenttel.api.DependencyType;
import io.agenttel.api.ServiceTier;
import io.agenttel.api.annotations.AgentObservable;
import io.agenttel.api.annotations.DeclareConsumer;
import io.agenttel.api.annotations.DeclareDependency;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationTopologyScannerTest {

    @AgentObservable(
            service = "test-service",
            team = "test-team",
            tier = ServiceTier.CRITICAL,
            domain = "testing",
            onCallChannel = "#test-oncall"
    )
    @DeclareDependency(name = "gateway", type = DependencyType.EXTERNAL_API, timeoutMs = 5000)
    @DeclareDependency(name = "db", type = DependencyType.DATABASE, criticality = DependencyCriticality.REQUIRED)
    @DeclareConsumer(name = "notifier", pattern = ConsumptionPattern.ASYNC, slaLatencyMs = 500)
    private static class AnnotatedApp {}

    @Test
    void scansAllAnnotationsCorrectly() {
        var scanner = new AnnotationTopologyScanner();
        var registry = new TopologyRegistry();

        scanner.scan(AnnotatedApp.class, registry);

        assertThat(registry.getTeam()).isEqualTo("test-team");
        assertThat(registry.getTier()).isEqualTo(ServiceTier.CRITICAL);
        assertThat(registry.getDomain()).isEqualTo("testing");
        assertThat(registry.getOnCallChannel()).isEqualTo("#test-oncall");

        assertThat(registry.getDependencies()).hasSize(2);
        assertThat(registry.getDependency("gateway")).isPresent();
        assertThat(registry.getDependency("gateway").get().timeoutMs()).isEqualTo(5000);
        assertThat(registry.getDependency("db")).isPresent();

        assertThat(registry.getConsumers()).hasSize(1);
        assertThat(registry.getConsumers().get(0).name()).isEqualTo("notifier");
        assertThat(registry.getConsumers().get(0).slaLatencyMs()).isEqualTo(500);
    }

    @Test
    void handlesClassWithNoAnnotations() {
        var scanner = new AnnotationTopologyScanner();
        var registry = new TopologyRegistry();

        scanner.scan(String.class, registry);

        assertThat(registry.getTeam()).isEmpty();
        assertThat(registry.getDependencies()).isEmpty();
        assertThat(registry.getConsumers()).isEmpty();
    }
}
