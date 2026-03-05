package io.agenttel.agent.session;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages incident sessions for multi-agent collaboration.
 * Thread-safe with bounded capacity and LRU eviction.
 */
public class SessionManager {

    private static final int MAX_SESSIONS = 50;

    private final Map<String, IncidentSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, String> incidentToSession = new ConcurrentHashMap<>();
    private final LinkedList<String> accessOrder = new LinkedList<>();

    /**
     * Create a new session for an incident. Returns the session.
     * If a session already exists for this incident, returns the existing one.
     */
    public synchronized IncidentSession createSession(String incidentId) {
        String existingSessionId = incidentToSession.get(incidentId);
        if (existingSessionId != null) {
            IncidentSession existing = sessionsById.get(existingSessionId);
            if (existing != null) {
                touchSession(existingSessionId);
                return existing;
            }
        }

        evictIfNeeded();

        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        IncidentSession session = new IncidentSession(sessionId, incidentId);
        sessionsById.put(sessionId, session);
        incidentToSession.put(incidentId, sessionId);
        accessOrder.addLast(sessionId);
        return session;
    }

    /**
     * Get a session by its ID.
     */
    public Optional<IncidentSession> getSession(String sessionId) {
        IncidentSession session = sessionsById.get(sessionId);
        if (session != null) {
            synchronized (this) {
                touchSession(sessionId);
            }
        }
        return Optional.ofNullable(session);
    }

    /**
     * Get a session by incident ID.
     */
    public Optional<IncidentSession> getSessionByIncident(String incidentId) {
        String sessionId = incidentToSession.get(incidentId);
        if (sessionId == null) return Optional.empty();
        return getSession(sessionId);
    }

    /**
     * Add an entry to a session. Returns false if session not found.
     */
    public boolean addEntry(String sessionId, SessionEntry entry) {
        IncidentSession session = sessionsById.get(sessionId);
        if (session == null) return false;
        session.addEntry(entry);
        synchronized (this) {
            touchSession(sessionId);
        }
        return true;
    }

    /**
     * Get all active sessions.
     */
    public List<IncidentSession> getActiveSessions() {
        return List.copyOf(sessionsById.values());
    }

    /**
     * Get the number of active sessions.
     */
    public int getActiveSessionCount() {
        return sessionsById.size();
    }

    private void touchSession(String sessionId) {
        accessOrder.remove(sessionId);
        accessOrder.addLast(sessionId);
    }

    private void evictIfNeeded() {
        while (sessionsById.size() >= MAX_SESSIONS && !accessOrder.isEmpty()) {
            String oldest = accessOrder.removeFirst();
            IncidentSession evicted = sessionsById.remove(oldest);
            if (evicted != null) {
                incidentToSession.remove(evicted.getIncidentId());
            }
        }
    }
}
