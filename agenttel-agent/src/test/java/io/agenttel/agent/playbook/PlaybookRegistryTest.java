package io.agenttel.agent.playbook;

import io.agenttel.core.anomaly.IncidentPattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PlaybookRegistryTest {

    private PlaybookRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PlaybookRegistry();
    }

    @Test
    void defaultPlaybooks_registeredOnConstruction() {
        List<Playbook> all = registry.getAll();
        assertEquals(4, all.size(), "Should have 4 default playbooks");

        // Verify all expected names are present
        assertTrue(registry.findByName("cascade-failure-response").isPresent(),
                "cascade-failure-response should be registered");
        assertTrue(registry.findByName("error-rate-spike-response").isPresent(),
                "error-rate-spike-response should be registered");
        assertTrue(registry.findByName("latency-degradation-response").isPresent(),
                "latency-degradation-response should be registered");
        assertTrue(registry.findByName("memory-leak-response").isPresent(),
                "memory-leak-response should be registered");
    }

    @Test
    void findForPattern_returnsCascadeFailurePlaybook() {
        Optional<Playbook> result = registry.findForPattern(IncidentPattern.CASCADE_FAILURE);

        assertTrue(result.isPresent());
        assertEquals("cascade-failure-response", result.get().name());
        assertEquals(List.of(IncidentPattern.CASCADE_FAILURE), result.get().triggerPatterns());
    }

    @Test
    void findForPattern_returnsErrorRateSpikePlaybook() {
        Optional<Playbook> result = registry.findForPattern(IncidentPattern.ERROR_RATE_SPIKE);

        assertTrue(result.isPresent());
        assertEquals("error-rate-spike-response", result.get().name());
    }

    @Test
    void findForPattern_returnsLatencyDegradationPlaybook() {
        Optional<Playbook> result = registry.findForPattern(IncidentPattern.LATENCY_DEGRADATION);

        assertTrue(result.isPresent());
        assertEquals("latency-degradation-response", result.get().name());
    }

    @Test
    void findForPattern_returnsMemoryLeakPlaybook() {
        Optional<Playbook> result = registry.findForPattern(IncidentPattern.MEMORY_LEAK);

        assertTrue(result.isPresent());
        assertEquals("memory-leak-response", result.get().name());
    }

    @Test
    void findForPattern_returnsEmptyForUnregisteredPattern() {
        Optional<Playbook> result = registry.findForPattern(IncidentPattern.THUNDERING_HERD);

        assertTrue(result.isEmpty(), "THUNDERING_HERD has no default playbook");
    }

    @Test
    void findByName_returnsCorrectPlaybook() {
        Optional<Playbook> result = registry.findByName("cascade-failure-response");

        assertTrue(result.isPresent());
        Playbook playbook = result.get();
        assertEquals("cascade-failure-response", playbook.name());
        assertNotNull(playbook.description());
        assertFalse(playbook.description().isEmpty());
        assertFalse(playbook.steps().isEmpty());
    }

    @Test
    void findByName_returnsEmptyForUnknownName() {
        Optional<Playbook> result = registry.findByName("nonexistent-playbook");

        assertTrue(result.isEmpty());
    }

    @Test
    void findForPatterns_picksFirstMatchingPattern() {
        // CASCADE_FAILURE is first in the list, so its playbook should be returned
        List<IncidentPattern> patterns = List.of(
                IncidentPattern.CASCADE_FAILURE,
                IncidentPattern.ERROR_RATE_SPIKE
        );

        Optional<Playbook> result = registry.findForPatterns(patterns);

        assertTrue(result.isPresent());
        assertEquals("cascade-failure-response", result.get().name());
    }

    @Test
    void findForPatterns_returnsSecondWhenFirstNotRegistered() {
        // THUNDERING_HERD has no playbook, so ERROR_RATE_SPIKE should be picked
        List<IncidentPattern> patterns = List.of(
                IncidentPattern.THUNDERING_HERD,
                IncidentPattern.ERROR_RATE_SPIKE
        );

        Optional<Playbook> result = registry.findForPatterns(patterns);

        assertTrue(result.isPresent());
        assertEquals("error-rate-spike-response", result.get().name());
    }

    @Test
    void findForPatterns_returnsEmptyWhenNoneMatch() {
        List<IncidentPattern> patterns = List.of(
                IncidentPattern.THUNDERING_HERD,
                IncidentPattern.COLD_START
        );

        Optional<Playbook> result = registry.findForPatterns(patterns);

        assertTrue(result.isEmpty());
    }

    @Test
    void findForPatterns_returnsEmptyForEmptyList() {
        Optional<Playbook> result = registry.findForPatterns(List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void register_customPlaybook() {
        Playbook custom = new Playbook(
                "custom-playbook",
                "Custom playbook for testing",
                List.of(IncidentPattern.THUNDERING_HERD),
                List.of(
                        Playbook.PlaybookStep.check("1", "Check request rate",
                                "request_rate > 10x baseline", "2", "done"),
                        Playbook.PlaybookStep.action("2", "Enable rate limiting",
                                "rate_limit", "done", "done", true)
                )
        );

        registry.register(custom);

        // Should be findable by name
        Optional<Playbook> byName = registry.findByName("custom-playbook");
        assertTrue(byName.isPresent());
        assertEquals("custom-playbook", byName.get().name());
        assertEquals(2, byName.get().steps().size());

        // Should be findable by pattern
        Optional<Playbook> byPattern = registry.findForPattern(IncidentPattern.THUNDERING_HERD);
        assertTrue(byPattern.isPresent());
        assertEquals("custom-playbook", byPattern.get().name());

        // Total playbook count should be 5 (4 defaults + 1 custom)
        assertEquals(5, registry.getAll().size());
    }

    @Test
    void register_overridesExistingByPattern() {
        Playbook override = new Playbook(
                "custom-cascade-response",
                "Override for cascade failure",
                List.of(IncidentPattern.CASCADE_FAILURE),
                List.of(
                        Playbook.PlaybookStep.action("1", "Immediate escalation",
                                "escalate", "done", "done", false)
                )
        );

        registry.register(override);

        // Pattern lookup should return the override
        Optional<Playbook> byPattern = registry.findForPattern(IncidentPattern.CASCADE_FAILURE);
        assertTrue(byPattern.isPresent());
        assertEquals("custom-cascade-response", byPattern.get().name());

        // Both the original and override should be findable by their respective names
        assertTrue(registry.findByName("cascade-failure-response").isPresent());
        assertTrue(registry.findByName("custom-cascade-response").isPresent());
    }

    @Test
    void toFormattedText_producesReadableOutput() {
        Optional<Playbook> opt = registry.findByName("cascade-failure-response");
        assertTrue(opt.isPresent());

        String formatted = opt.get().toFormattedText();

        // Verify header
        assertTrue(formatted.contains("PLAYBOOK: cascade-failure-response"),
                "Should contain playbook name");
        assertTrue(formatted.contains("Description:"),
                "Should contain description label");
        assertTrue(formatted.contains("Triggers:"),
                "Should contain triggers label");
        assertTrue(formatted.contains("cascade_failure"),
                "Should contain the trigger pattern value");
        assertTrue(formatted.contains("STEPS:"),
                "Should contain steps header");

        // Verify step formatting
        assertTrue(formatted.contains("[1]"),
                "Should contain step ID in brackets");
        assertTrue(formatted.contains("CHECK:"),
                "Should contain step type for check steps");
        assertTrue(formatted.contains("ACTION:"),
                "Should contain step type for action steps");
        assertTrue(formatted.contains("Condition:"),
                "Should contain condition label for check steps");
        assertTrue(formatted.contains("On success"),
                "Should contain on success label");
        assertTrue(formatted.contains("On failure"),
                "Should contain on failure label");
    }

    @Test
    void toFormattedText_showsApprovalRequired() {
        // The error-rate-spike-response playbook has steps that require approval
        Optional<Playbook> opt = registry.findByName("error-rate-spike-response");
        assertTrue(opt.isPresent());

        String formatted = opt.get().toFormattedText();

        assertTrue(formatted.contains("REQUIRES APPROVAL"),
                "Should indicate steps requiring approval");
    }

    @Test
    void toFormattedText_containsDecisionStepType() {
        // The cascade-failure-response has a DECISION step (step 4)
        Optional<Playbook> opt = registry.findByName("cascade-failure-response");
        assertTrue(opt.isPresent());

        String formatted = opt.get().toFormattedText();

        assertTrue(formatted.contains("DECISION:"),
                "Should contain DECISION step type");
    }

    @Test
    void defaultPlaybooks_haveNonEmptySteps() {
        for (Playbook playbook : registry.getAll()) {
            assertFalse(playbook.steps().isEmpty(),
                    "Playbook " + playbook.name() + " should have steps");
            assertNotNull(playbook.description(),
                    "Playbook " + playbook.name() + " should have a description");
            assertFalse(playbook.triggerPatterns().isEmpty(),
                    "Playbook " + playbook.name() + " should have trigger patterns");
        }
    }
}
