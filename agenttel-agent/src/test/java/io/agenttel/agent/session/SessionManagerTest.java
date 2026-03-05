package io.agenttel.agent.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SessionManagerTest {

    private SessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new SessionManager();
    }

    @Test
    void createSession_returnsNewSession() {
        IncidentSession session = manager.createSession("inc-123");

        assertThat(session).isNotNull();
        assertThat(session.getSessionId()).isNotEmpty();
        assertThat(session.getIncidentId()).isEqualTo("inc-123");
        assertThat(manager.getActiveSessionCount()).isEqualTo(1);
    }

    @Test
    void createSession_sameIncident_returnsExisting() {
        IncidentSession first = manager.createSession("inc-123");
        IncidentSession second = manager.createSession("inc-123");

        assertThat(first.getSessionId()).isEqualTo(second.getSessionId());
        assertThat(manager.getActiveSessionCount()).isEqualTo(1);
    }

    @Test
    void getSession_returnsCreatedSession() {
        IncidentSession created = manager.createSession("inc-123");

        var found = manager.getSession(created.getSessionId());

        assertThat(found).isPresent();
        assertThat(found.get().getIncidentId()).isEqualTo("inc-123");
    }

    @Test
    void getSession_unknownId_returnsEmpty() {
        assertThat(manager.getSession("nonexistent")).isEmpty();
    }

    @Test
    void getSessionByIncident_works() {
        manager.createSession("inc-456");

        var found = manager.getSessionByIncident("inc-456");

        assertThat(found).isPresent();
        assertThat(found.get().getIncidentId()).isEqualTo("inc-456");
    }

    @Test
    void addEntry_toExistingSession() {
        IncidentSession session = manager.createSession("inc-123");
        SessionEntry entry = new SessionEntry("agent-1", "observer",
                SessionEntry.EntryType.OBSERVATION, "Error rate increasing");

        boolean added = manager.addEntry(session.getSessionId(), entry);

        assertThat(added).isTrue();
        assertThat(session.getEntries()).hasSize(1);
    }

    @Test
    void addEntry_toUnknownSession_returnsFalse() {
        boolean added = manager.addEntry("nonexistent",
                new SessionEntry("a", "r", SessionEntry.EntryType.OBSERVATION, "c"));

        assertThat(added).isFalse();
    }

    @Test
    void getActiveSessions_returnsAll() {
        manager.createSession("inc-1");
        manager.createSession("inc-2");
        manager.createSession("inc-3");

        assertThat(manager.getActiveSessions()).hasSize(3);
    }

    @Test
    void lruEviction_removesOldestWhenFull() {
        // Create 50 sessions (max capacity)
        for (int i = 0; i < 50; i++) {
            manager.createSession("inc-" + i);
        }
        assertThat(manager.getActiveSessionCount()).isEqualTo(50);

        // Creating one more should evict the oldest
        manager.createSession("inc-overflow");

        assertThat(manager.getActiveSessionCount()).isEqualTo(50);
        // First session should be evicted
        assertThat(manager.getSessionByIncident("inc-0")).isEmpty();
        // New session should exist
        assertThat(manager.getSessionByIncident("inc-overflow")).isPresent();
    }

    @Test
    void lruEviction_accessedSessionsSurvive() {
        // Create 50 sessions
        for (int i = 0; i < 50; i++) {
            manager.createSession("inc-" + i);
        }

        // Access session 0 to make it recently used
        manager.getSessionByIncident("inc-0");

        // Add a new session, should evict inc-1 (the least recently used)
        manager.createSession("inc-overflow");

        assertThat(manager.getSessionByIncident("inc-0")).isPresent();
        assertThat(manager.getSessionByIncident("inc-1")).isEmpty();
    }
}
