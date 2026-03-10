package io.agenttel.testing;

import io.agenttel.api.attributes.AgenticAttributes;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluent assertion helpers for agent observability spans.
 *
 * <p>Usage:
 * <pre>{@code
 * AgenticAssertions.assertAgentInvocation(collector)
 *     .hasAgentName("incident-responder")
 *     .hasGoal("Diagnose high latency")
 *     .wasSuccessful()
 *     .hasStepCount(4);
 * }</pre>
 */
public final class AgenticAssertions {

    private AgenticAssertions() {}

    /**
     * Starts assertion on the first invoke_agent span in the collector.
     */
    public static InvocationAssertion assertAgentInvocation(InMemoryAgentTelCollector collector) {
        List<SpanData> invocations = collector.getSpansByName("invoke_agent");
        assertThat(invocations).as("Expected at least one invoke_agent span").isNotEmpty();
        return new InvocationAssertion(invocations.get(0));
    }

    /**
     * Starts assertion on an invoke_agent span with a specific agent name.
     */
    public static InvocationAssertion assertAgentInvocation(InMemoryAgentTelCollector collector, String agentName) {
        List<SpanData> invocations = collector.getSpansByName("invoke_agent").stream()
                .filter(s -> agentName.equals(s.getAttributes().get(AgenticAttributes.AGENT_NAME)))
                .collect(Collectors.toList());
        assertThat(invocations).as("Expected invoke_agent span for agent '%s'", agentName).isNotEmpty();
        return new InvocationAssertion(invocations.get(0));
    }

    /**
     * Asserts that an orchestration session exists with the given pattern.
     */
    public static SessionAssertion assertOrchestrationPattern(InMemoryAgentTelCollector collector, String pattern) {
        List<SpanData> sessions = collector.getSpansByName("agenttel.agentic.session").stream()
                .filter(s -> pattern.equals(s.getAttributes().get(AgenticAttributes.ORCHESTRATION_PATTERN)))
                .collect(Collectors.toList());
        assertThat(sessions).as("Expected session span with pattern '%s'", pattern).isNotEmpty();
        return new SessionAssertion(sessions.get(0));
    }

    /**
     * Asserts that goal was achieved by any agent in the collector.
     */
    public static void assertGoalAchieved(InMemoryAgentTelCollector collector) {
        List<SpanData> invocations = collector.getSpansByName("invoke_agent");
        assertThat(invocations).as("Expected at least one invoke_agent span").isNotEmpty();
        boolean anyAchieved = invocations.stream()
                .anyMatch(s -> Boolean.TRUE.equals(s.getAttributes().get(AgenticAttributes.QUALITY_GOAL_ACHIEVED)));
        assertThat(anyAchieved).as("Expected at least one invocation with goal_achieved=true").isTrue();
    }

    /**
     * Returns all step spans from the collector.
     */
    public static List<SpanData> getStepSpans(InMemoryAgentTelCollector collector) {
        return collector.getSpansByName("agenttel.agentic.step");
    }

    /**
     * Returns all tool call spans from the collector.
     */
    public static List<SpanData> getToolCallSpans(InMemoryAgentTelCollector collector) {
        return collector.getSpansByName("agenttel.agentic.tool_call");
    }

    /**
     * Returns all guardrail spans from the collector.
     */
    public static List<SpanData> getGuardrailSpans(InMemoryAgentTelCollector collector) {
        return collector.getSpansByName("agenttel.agentic.guardrail");
    }

    // --- Assertion classes ---

    public static class InvocationAssertion {
        private final SpanData span;

        InvocationAssertion(SpanData span) {
            this.span = span;
        }

        public InvocationAssertion hasAgentName(String name) {
            assertThat(span.getAttributes().get(AgenticAttributes.AGENT_NAME))
                    .as("agent name").isEqualTo(name);
            return this;
        }

        public InvocationAssertion hasGoal(String goal) {
            assertThat(span.getAttributes().get(AgenticAttributes.INVOCATION_GOAL))
                    .as("invocation goal").isEqualTo(goal);
            return this;
        }

        public InvocationAssertion wasSuccessful() {
            assertThat(span.getAttributes().get(AgenticAttributes.INVOCATION_STATUS))
                    .as("invocation status").isEqualTo("success");
            assertThat(span.getAttributes().get(AgenticAttributes.QUALITY_GOAL_ACHIEVED))
                    .as("goal achieved").isTrue();
            return this;
        }

        public InvocationAssertion wasFailure() {
            assertThat(span.getAttributes().get(AgenticAttributes.INVOCATION_STATUS))
                    .as("invocation status").isEqualTo("failure");
            return this;
        }

        public InvocationAssertion hasStepCount(long expected) {
            assertThat(span.getAttributes().get(AgenticAttributes.INVOCATION_STEPS))
                    .as("step count").isEqualTo(expected);
            return this;
        }

        public InvocationAssertion hasAgentType(String type) {
            assertThat(span.getAttributes().get(AgenticAttributes.AGENT_TYPE))
                    .as("agent type").isEqualTo(type);
            return this;
        }

        public InvocationAssertion hasFramework(String framework) {
            assertThat(span.getAttributes().get(AgenticAttributes.AGENT_FRAMEWORK))
                    .as("agent framework").isEqualTo(framework);
            return this;
        }

        public InvocationAssertion hasHumanInterventions(long count) {
            assertThat(span.getAttributes().get(AgenticAttributes.QUALITY_HUMAN_INTERVENTIONS))
                    .as("human interventions").isEqualTo(count);
            return this;
        }

        public SpanData span() {
            return span;
        }
    }

    public static class SessionAssertion {
        private final SpanData span;

        SessionAssertion(SpanData span) {
            this.span = span;
        }

        public SessionAssertion hasPattern(String pattern) {
            assertThat(span.getAttributes().get(AgenticAttributes.ORCHESTRATION_PATTERN))
                    .as("orchestration pattern").isEqualTo(pattern);
            return this;
        }

        public SessionAssertion hasTotalStages(long stages) {
            assertThat(span.getAttributes().get(AgenticAttributes.ORCHESTRATION_TOTAL_STAGES))
                    .as("total stages").isEqualTo(stages);
            return this;
        }

        public SessionAssertion hasParallelBranches(long branches) {
            assertThat(span.getAttributes().get(AgenticAttributes.ORCHESTRATION_PARALLEL_BRANCHES))
                    .as("parallel branches").isEqualTo(branches);
            return this;
        }

        public SessionAssertion hasAggregation(String strategy) {
            assertThat(span.getAttributes().get(AgenticAttributes.ORCHESTRATION_AGGREGATION))
                    .as("aggregation strategy").isEqualTo(strategy);
            return this;
        }

        public SpanData span() {
            return span;
        }
    }
}
