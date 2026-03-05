package io.agenttel.agent.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class IncidentSessionTest {

    @Test
    void addEntry_andGetEntries() {
        var session = new IncidentSession("sess-1", "inc-abc");

        session.addEntry(new SessionEntry("agent-1", "observer",
                SessionEntry.EntryType.OBSERVATION, "Error rate is 5%"));
        session.addEntry(new SessionEntry("agent-2", "diagnostician",
                SessionEntry.EntryType.DIAGNOSIS, "Root cause: stripe-api timeout"));

        assertThat(session.getEntries()).hasSize(2);
        assertThat(session.getSessionId()).isEqualTo("sess-1");
        assertThat(session.getIncidentId()).isEqualTo("inc-abc");
    }

    @Test
    void getEntriesByAgent_filtersCorrectly() {
        var session = new IncidentSession("sess-1", "inc-abc");
        session.addEntry(new SessionEntry("agent-1", "observer",
                SessionEntry.EntryType.OBSERVATION, "entry-1"));
        session.addEntry(new SessionEntry("agent-2", "diagnostician",
                SessionEntry.EntryType.DIAGNOSIS, "entry-2"));
        session.addEntry(new SessionEntry("agent-1", "observer",
                SessionEntry.EntryType.RECOMMENDATION, "entry-3"));

        assertThat(session.getEntriesByAgent("agent-1")).hasSize(2);
        assertThat(session.getEntriesByAgent("agent-2")).hasSize(1);
        assertThat(session.getEntriesByAgent("unknown")).isEmpty();
    }

    @Test
    void getEntriesByType_filtersCorrectly() {
        var session = new IncidentSession("sess-1", "inc-abc");
        session.addEntry(new SessionEntry("a1", "obs",
                SessionEntry.EntryType.OBSERVATION, "obs-1"));
        session.addEntry(new SessionEntry("a2", "diag",
                SessionEntry.EntryType.DIAGNOSIS, "diag-1"));
        session.addEntry(new SessionEntry("a1", "obs",
                SessionEntry.EntryType.OBSERVATION, "obs-2"));

        assertThat(session.getEntriesByType(SessionEntry.EntryType.OBSERVATION)).hasSize(2);
        assertThat(session.getEntriesByType(SessionEntry.EntryType.ACTION)).isEmpty();
    }

    @Test
    void boundedAt500Entries() {
        var session = new IncidentSession("sess-1", "inc-abc");

        for (int i = 0; i < 510; i++) {
            session.addEntry(new SessionEntry("agent", "observer",
                    SessionEntry.EntryType.OBSERVATION, "entry-" + i));
        }

        assertThat(session.getEntryCount()).isEqualTo(500);
        // First entry should have been evicted
        assertThat(session.getEntries().get(0).content()).isEqualTo("entry-10");
    }

    @Test
    void toFormattedText_containsAllSections() {
        var session = new IncidentSession("sess-1", "inc-abc");
        session.addEntry(new SessionEntry("agent-1", "observer",
                SessionEntry.EntryType.OBSERVATION, "Something is wrong"));

        String text = session.toFormattedText();

        assertThat(text).contains("SESSION sess-1");
        assertThat(text).contains("inc-abc");
        assertThat(text).contains("Entries: 1");
        assertThat(text).contains("OBSERVATION by agent-1");
        assertThat(text).contains("Something is wrong");
    }

    @Test
    void toJson_producesValidStructure() {
        var session = new IncidentSession("sess-1", "inc-abc");
        session.addEntry(new SessionEntry("agent-1", "observer",
                SessionEntry.EntryType.OBSERVATION, "test content"));

        String json = session.toJson();

        assertThat(json).startsWith("{");
        assertThat(json).endsWith("}");
        assertThat(json).contains("\"sessionId\":\"sess-1\"");
        assertThat(json).contains("\"incidentId\":\"inc-abc\"");
        assertThat(json).contains("\"entryCount\":1");
        assertThat(json).contains("\"agentId\":\"agent-1\"");
        assertThat(json).contains("\"content\":\"test content\"");
    }

    @Test
    void entryType_fromValue_caseInsensitive() {
        assertThat(SessionEntry.EntryType.fromValue("observation"))
                .isEqualTo(SessionEntry.EntryType.OBSERVATION);
        assertThat(SessionEntry.EntryType.fromValue("DIAGNOSIS"))
                .isEqualTo(SessionEntry.EntryType.DIAGNOSIS);
        assertThat(SessionEntry.EntryType.fromValue("invalid"))
                .isEqualTo(SessionEntry.EntryType.OBSERVATION);
    }
}
