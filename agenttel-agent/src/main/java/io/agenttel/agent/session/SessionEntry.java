package io.agenttel.agent.session;

import java.time.Instant;
import java.util.Map;

/**
 * A single entry in an incident session, contributed by an agent.
 * Entries represent observations, diagnoses, actions, or recommendations.
 */
public record SessionEntry(
        String agentId,
        String role,
        Instant timestamp,
        EntryType type,
        String content,
        Map<String, String> metadata
) {
    public enum EntryType {
        OBSERVATION,
        DIAGNOSIS,
        ACTION,
        RECOMMENDATION;

        public static EntryType fromValue(String value) {
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return OBSERVATION;
            }
        }
    }

    public SessionEntry(String agentId, String role, EntryType type, String content) {
        this(agentId, role, Instant.now(), type, content, null);
    }

    public String toFormattedText() {
        var sb = new StringBuilder();
        sb.append("[").append(timestamp).append("] ");
        sb.append(type).append(" by ").append(agentId);
        if (role != null && !role.isEmpty()) {
            sb.append(" (").append(role).append(")");
        }
        sb.append(": ").append(content);
        return sb.toString();
    }

    public String toJson() {
        var sb = new StringBuilder();
        sb.append("{");
        sb.append("\"agentId\":\"").append(escapeJson(agentId)).append("\"");
        sb.append(",\"role\":\"").append(escapeJson(role != null ? role : "")).append("\"");
        sb.append(",\"timestamp\":\"").append(timestamp).append("\"");
        sb.append(",\"type\":\"").append(type).append("\"");
        sb.append(",\"content\":\"").append(escapeJson(content)).append("\"");
        if (metadata != null && !metadata.isEmpty()) {
            sb.append(",\"metadata\":{");
            var iter = metadata.entrySet().iterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
                        .append(escapeJson(entry.getValue())).append("\"");
                if (iter.hasNext()) sb.append(",");
            }
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
