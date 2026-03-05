package io.agenttel.agent.playbook;

import io.agenttel.core.anomaly.IncidentPattern;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of machine-readable playbooks indexed by name and trigger pattern.
 * Pre-registers default playbooks for common incident patterns.
 */
public class PlaybookRegistry {

    private final ConcurrentHashMap<String, Playbook> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<IncidentPattern, Playbook> byPattern = new ConcurrentHashMap<>();

    public PlaybookRegistry() {
        registerDefaults();
    }

    public void register(Playbook playbook) {
        byName.put(playbook.name(), playbook);
        for (IncidentPattern pattern : playbook.triggerPatterns()) {
            byPattern.put(pattern, playbook);
        }
    }

    public Optional<Playbook> findByName(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public Optional<Playbook> findForPattern(IncidentPattern pattern) {
        return Optional.ofNullable(byPattern.get(pattern));
    }

    /**
     * Finds the best matching playbook for a set of detected patterns.
     */
    public Optional<Playbook> findForPatterns(List<IncidentPattern> patterns) {
        for (IncidentPattern p : patterns) {
            Playbook pb = byPattern.get(p);
            if (pb != null) return Optional.of(pb);
        }
        return Optional.empty();
    }

    public List<Playbook> getAll() {
        return new ArrayList<>(byName.values());
    }

    private void registerDefaults() {
        register(new Playbook(
                "cascade-failure-response",
                "Response playbook for cascade failures across multiple dependencies",
                List.of(IncidentPattern.CASCADE_FAILURE),
                List.of(
                        Playbook.PlaybookStep.check("1", "Identify failing dependencies",
                                "dependency_error_rate > 50%", "2", "6"),
                        Playbook.PlaybookStep.action("2", "Enable circuit breakers on failing dependencies",
                                "enable_circuit_breakers", "3", "5", false),
                        Playbook.PlaybookStep.check("3", "Verify circuit breakers are reducing error rate",
                                "error_rate decreasing after 30s", "4", "5"),
                        Playbook.PlaybookStep.decision("4", "Check if fallbacks are handling traffic",
                                "fallback_success_rate > 90%", "done", "5"),
                        Playbook.PlaybookStep.action("5", "Escalate to on-call for manual intervention",
                                "escalate", "done", "done", false),
                        Playbook.PlaybookStep.action("6", "No dependency failures found, investigate application logs",
                                "investigate", "done", "done", false)
                )
        ));

        register(new Playbook(
                "error-rate-spike-response",
                "Response playbook for sudden error rate increases",
                List.of(IncidentPattern.ERROR_RATE_SPIKE),
                List.of(
                        Playbook.PlaybookStep.check("1", "Check if recent deployment occurred",
                                "deployment within last 30 minutes", "2", "3"),
                        Playbook.PlaybookStep.action("2", "Rollback to previous version",
                                "rollback_deployment", "verify", "escalate", true),
                        Playbook.PlaybookStep.check("3", "Check error classification breakdown",
                                "error_category is dependency_timeout or connection_error", "4", "5"),
                        Playbook.PlaybookStep.action("4", "Enable circuit breakers on failing dependency",
                                "enable_circuit_breakers", "verify", "escalate", false),
                        Playbook.PlaybookStep.action("5", "Restart instances for potential resource exhaustion",
                                "restart_instances", "verify", "escalate", true),
                        Playbook.PlaybookStep.check("verify", "Verify error rate is decreasing",
                                "error_rate < baseline * 2 after 60s", "done", "escalate"),
                        Playbook.PlaybookStep.action("escalate", "Escalate to incident commander",
                                "escalate", "done", "done", false)
                )
        ));

        register(new Playbook(
                "latency-degradation-response",
                "Response playbook for latency degradation",
                List.of(IncidentPattern.LATENCY_DEGRADATION),
                List.of(
                        Playbook.PlaybookStep.check("1", "Check if dependency latency is elevated",
                                "any dependency latency > 2x baseline", "2", "3"),
                        Playbook.PlaybookStep.action("2", "Rate limit requests to degraded dependency",
                                "rate_limit", "verify", "escalate", false),
                        Playbook.PlaybookStep.check("3", "Check if resource utilization is high",
                                "cpu > 80% or memory > 85%", "4", "5"),
                        Playbook.PlaybookStep.action("4", "Scale horizontally",
                                "scale_horizontally", "verify", "escalate", true),
                        Playbook.PlaybookStep.action("5", "Flush caches to reduce memory pressure",
                                "cache_flush", "verify", "escalate", false),
                        Playbook.PlaybookStep.check("verify", "Verify latency is improving",
                                "latency_p50 < baseline * 1.5 after 60s", "done", "escalate"),
                        Playbook.PlaybookStep.action("escalate", "Escalate for investigation",
                                "escalate", "done", "done", false)
                )
        ));

        register(new Playbook(
                "memory-leak-response",
                "Response playbook for suspected memory leaks",
                List.of(IncidentPattern.MEMORY_LEAK),
                List.of(
                        Playbook.PlaybookStep.check("1", "Confirm monotonic latency increase",
                                "latency_trend is consistently increasing", "2", "done"),
                        Playbook.PlaybookStep.action("2", "Perform rolling restart of service instances",
                                "restart_instances", "3", "escalate", true),
                        Playbook.PlaybookStep.check("3", "Verify latency returned to baseline after restart",
                                "latency_p50 within 1.2x baseline after 120s", "4", "escalate"),
                        Playbook.PlaybookStep.action("4", "Flag for engineering review of memory usage",
                                "investigate", "done", "done", false),
                        Playbook.PlaybookStep.action("escalate", "Escalate — restarts did not resolve the leak",
                                "escalate", "done", "done", false)
                )
        ));
    }
}
