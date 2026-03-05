package io.agenttel.agent.playbook;

import io.agenttel.core.anomaly.IncidentPattern;

import java.util.List;
import java.util.StringJoiner;

/**
 * A machine-readable playbook that agents can follow step-by-step.
 * Replaces opaque runbook URLs with structured decision trees.
 */
public record Playbook(
        String name,
        String description,
        List<IncidentPattern> triggerPatterns,
        List<PlaybookStep> steps
) {

    public enum StepType {
        CHECK, ACTION, DECISION
    }

    public record PlaybookStep(
            String id,
            StepType type,
            String description,
            String condition,
            String actionName,
            String onSuccess,
            String onFailure,
            boolean requiresApproval
    ) {
        public static PlaybookStep check(String id, String description, String condition,
                                          String onSuccess, String onFailure) {
            return new PlaybookStep(id, StepType.CHECK, description, condition, null,
                    onSuccess, onFailure, false);
        }

        public static PlaybookStep action(String id, String description, String actionName,
                                           String onSuccess, String onFailure, boolean requiresApproval) {
            return new PlaybookStep(id, StepType.ACTION, description, null, actionName,
                    onSuccess, onFailure, requiresApproval);
        }

        public static PlaybookStep decision(String id, String description, String condition,
                                             String onSuccess, String onFailure) {
            return new PlaybookStep(id, StepType.DECISION, description, condition,
                    null, onSuccess, onFailure, false);
        }
    }

    /**
     * Returns a formatted text representation for LLM consumption.
     */
    public String toFormattedText() {
        StringBuilder sb = new StringBuilder();
        sb.append("PLAYBOOK: ").append(name).append("\n");
        sb.append("Description: ").append(description).append("\n");
        sb.append("Triggers: ");
        StringJoiner sj = new StringJoiner(", ");
        for (IncidentPattern p : triggerPatterns) {
            sj.add(p.getValue());
        }
        sb.append(sj).append("\n\nSTEPS:\n");

        for (PlaybookStep step : steps) {
            sb.append("  [").append(step.id()).append("] ")
                    .append(step.type()).append(": ")
                    .append(step.description()).append("\n");

            if (step.condition() != null) {
                sb.append("    Condition: ").append(step.condition()).append("\n");
            }
            if (step.actionName() != null) {
                sb.append("    Action: ").append(step.actionName()).append("\n");
            }
            if (step.requiresApproval()) {
                sb.append("    *** REQUIRES APPROVAL ***\n");
            }
            if (step.onSuccess() != null) {
                sb.append("    On success → ").append(step.onSuccess()).append("\n");
            }
            if (step.onFailure() != null) {
                sb.append("    On failure → ").append(step.onFailure()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Returns a JSON representation for structured tool results.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":\"").append(escapeJson(name)).append("\"");
        sb.append(",\"description\":\"").append(escapeJson(description)).append("\"");
        sb.append(",\"triggers\":[");
        StringJoiner tj = new StringJoiner(",");
        for (IncidentPattern p : triggerPatterns) {
            tj.add("\"" + p.getValue() + "\"");
        }
        sb.append(tj).append("]");
        sb.append(",\"steps\":[");
        StringJoiner stepsJson = new StringJoiner(",");
        for (PlaybookStep step : steps) {
            stepsJson.add(stepToJson(step));
        }
        sb.append(stepsJson).append("]}");
        return sb.toString();
    }

    private String stepToJson(PlaybookStep step) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":\"").append(step.id()).append("\"");
        sb.append(",\"type\":\"").append(step.type()).append("\"");
        sb.append(",\"description\":\"").append(escapeJson(step.description())).append("\"");
        if (step.condition() != null) {
            sb.append(",\"condition\":\"").append(escapeJson(step.condition())).append("\"");
        }
        if (step.actionName() != null) {
            sb.append(",\"action\":\"").append(escapeJson(step.actionName())).append("\"");
        }
        sb.append(",\"requires_approval\":").append(step.requiresApproval());
        if (step.onSuccess() != null) {
            sb.append(",\"on_success\":\"").append(step.onSuccess()).append("\"");
        }
        if (step.onFailure() != null) {
            sb.append(",\"on_failure\":\"").append(step.onFailure()).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
