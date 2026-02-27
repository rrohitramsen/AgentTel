package io.agenttel.testing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import org.assertj.core.api.AbstractAssert;

import java.util.List;

/**
 * Fluent assertions for a list of OTel log records (events).
 */
public class EventListAssert extends AbstractAssert<EventListAssert, List<LogRecordData>> {

    public EventListAssert(List<LogRecordData> actual) {
        super(actual, EventListAssert.class);
    }

    public EventListAssert hasEventWithName(String eventName) {
        isNotNull();
        boolean found = actual.stream().anyMatch(log -> {
            var attr = log.getAttributes().get(AttributeKey.stringKey("event.name"));
            return eventName.equals(attr);
        });
        if (!found) {
            failWithMessage("Expected to find event with name '%s' but none was found", eventName);
        }
        return this;
    }

    public EventListAssert hasSize(int expectedSize) {
        isNotNull();
        if (actual.size() != expectedSize) {
            failWithMessage("Expected %d events but found %d", expectedSize, actual.size());
        }
        return this;
    }
}
