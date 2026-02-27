package io.agenttel.testing;

import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.util.List;

/**
 * Entry point for AgentTel test assertions.
 */
public final class AgentTelAssertions {
    private AgentTelAssertions() {}

    public static SpanListAssert assertThat(List<SpanData> spans) {
        return new SpanListAssert(spans);
    }

    public static EventListAssert assertThatEvents(List<LogRecordData> events) {
        return new EventListAssert(events);
    }
}
