package io.agenttel.agent.session;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * A shared session for multi-agent collaboration on an incident.
 * Implements the blackboard pattern — agents post observations, diagnoses,
 * and actions that other agents can read and build upon.
 */
public class IncidentSession {

    private static final int MAX_ENTRIES = 500;

    private final String sessionId;
    private final String incidentId;
    private final Instant createdAt;
    private final CopyOnWriteArrayList<SessionEntry> entries = new CopyOnWriteArrayList<>();

    public IncidentSession(String sessionId, String incidentId) {
        this.sessionId = sessionId;
        this.incidentId = incidentId;
        this.createdAt = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void addEntry(SessionEntry entry) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.remove(0);
        }
        entries.add(entry);
    }

    public List<SessionEntry> getEntries() {
        return List.copyOf(entries);
    }

    public List<SessionEntry> getEntriesByAgent(String agentId) {
        return entries.stream()
                .filter(e -> agentId.equals(e.agentId()))
                .collect(Collectors.toList());
    }

    public List<SessionEntry> getEntriesByType(SessionEntry.EntryType type) {
        return entries.stream()
                .filter(e -> type.equals(e.type()))
                .collect(Collectors.toList());
    }

    public int getEntryCount() {
        return entries.size();
    }

    public String toFormattedText() {
        var sb = new StringBuilder();
        sb.append("=== SESSION ").append(sessionId).append(" ===\n");
        sb.append("Incident: ").append(incidentId).append("\n");
        sb.append("Created: ").append(createdAt).append("\n");
        sb.append("Entries: ").append(entries.size()).append("\n\n");

        for (SessionEntry entry : entries) {
            sb.append(entry.toFormattedText()).append("\n");
        }
        return sb.toString();
    }

    public String toJson() {
        var sb = new StringBuilder();
        sb.append("{");
        sb.append("\"sessionId\":\"").append(sessionId).append("\"");
        sb.append(",\"incidentId\":\"").append(incidentId).append("\"");
        sb.append(",\"createdAt\":\"").append(createdAt).append("\"");
        sb.append(",\"entryCount\":").append(entries.size());
        sb.append(",\"entries\":[");
        var iter = entries.iterator();
        while (iter.hasNext()) {
            sb.append(iter.next().toJson());
            if (iter.hasNext()) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }
}
