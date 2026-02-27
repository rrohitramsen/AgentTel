package io.agenttel.example;

import io.agenttel.api.attributes.AgentTelAttributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that boots the full Spring Boot application and verifies
 * that AgentTel enriches spans with topology, baseline, and decision attributes.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "otel.traces.exporter=none",
                "otel.metrics.exporter=none",
                "otel.logs.exporter=none"
        }
)
class PaymentServiceIntegrationTest {

    @TestConfiguration
    static class TestOTelConfig {
        @Bean
        public InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }

        @Bean
        @Primary
        public AutoConfigurationCustomizerProvider testOtelCustomizer(
                InMemorySpanExporter exporter,
                io.agenttel.core.enrichment.AgentTelSpanProcessor agentTelProcessor) {
            return customizer -> customizer.addTracerProviderCustomizer(
                    (builder, config) -> builder
                            .addSpanProcessor(agentTelProcessor)
                            .addSpanProcessor(SimpleSpanProcessor.create(exporter)));
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InMemorySpanExporter spanExporter;

    @BeforeEach
    void resetSpans() {
        spanExporter.reset();
    }

    @Test
    void topologyAttributesOnResource() {
        postPayment(100.0);
        waitForSpans();

        SpanData serverSpan = findServerSpan("POST /api/payments");
        assertThat(serverSpan).isNotNull();
        // Topology is on the OTel Resource (set once per service), not duplicated per span
        assertThat(serverSpan.getResource().getAttributes().get(AgentTelAttributes.TOPOLOGY_TEAM))
                .isEqualTo("payments-platform");
        assertThat(serverSpan.getResource().getAttributes().get(AgentTelAttributes.TOPOLOGY_TIER))
                .isEqualTo("critical");
        assertThat(serverSpan.getResource().getAttributes().get(AgentTelAttributes.TOPOLOGY_DOMAIN))
                .isEqualTo("commerce");
        assertThat(serverSpan.getResource().getAttributes().get(AgentTelAttributes.TOPOLOGY_ON_CALL_CHANNEL))
                .isEqualTo("#payments-oncall");
    }

    @Test
    void baselineAttributesPresent() {
        postPayment(50.0);
        waitForSpans();

        SpanData serverSpan = findServerSpan("POST /api/payments");
        assertThat(serverSpan).isNotNull();
        // Baselines come from YAML config (agenttel.operations) or @AgentOperation annotation
        assertThat(serverSpan.getAttributes().get(AgentTelAttributes.BASELINE_LATENCY_P50_MS))
                .isEqualTo(45.0);
        assertThat(serverSpan.getAttributes().get(AgentTelAttributes.BASELINE_LATENCY_P99_MS))
                .isEqualTo(200.0);
        assertThat(serverSpan.getAttributes().get(AgentTelAttributes.BASELINE_ERROR_RATE))
                .isEqualTo(0.001);
    }

    @Test
    void decisionAttributesPresent() {
        postPayment(75.0);
        waitForSpans();

        SpanData serverSpan = findServerSpan("POST /api/payments");
        assertThat(serverSpan).isNotNull();
        // Decision metadata from YAML config (agenttel.operations) or @AgentOperation annotation
        assertThat(serverSpan.getAttributes().get(AgentTelAttributes.DECISION_RETRYABLE))
                .isTrue();
        assertThat(serverSpan.getAttributes().get(AgentTelAttributes.DECISION_IDEMPOTENT))
                .isTrue();
        assertThat(serverSpan.getAttributes().get(AgentTelAttributes.DECISION_RUNBOOK_URL))
                .isEqualTo("https://wiki/runbooks/process-payment");
        assertThat(serverSpan.getAttributes().get(AgentTelAttributes.DECISION_ESCALATION_LEVEL))
                .isEqualTo("page_oncall");
        assertThat(serverSpan.getAttributes().get(AgentTelAttributes.DECISION_SAFE_TO_RESTART))
                .isFalse();
    }

    @Test
    void mcpHealthEndpointResponds() throws Exception {
        // Give the MCP server time to start
        Thread.sleep(500);

        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:8081/health", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"ok\"");
    }

    private void postPayment(double amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"amount\":" + amount + ",\"currency\":\"USD\",\"recipient\":\"test\"}";
        restTemplate.postForEntity("/api/payments", new HttpEntity<>(body, headers), String.class);
    }

    private void waitForSpans() {
        // Allow some time for span processing
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private SpanData findServerSpan(String nameContains) {
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        return spans.stream()
                .filter(s -> s.getName().contains(nameContains))
                .findFirst()
                .orElse(null);
    }
}
