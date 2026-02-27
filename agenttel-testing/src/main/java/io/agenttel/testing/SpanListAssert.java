package io.agenttel.testing;

import io.opentelemetry.sdk.trace.data.SpanData;
import org.assertj.core.api.AbstractAssert;

import java.util.List;

/**
 * Fluent assertions for a list of OTel spans.
 */
public class SpanListAssert extends AbstractAssert<SpanListAssert, List<SpanData>> {

    public SpanListAssert(List<SpanData> actual) {
        super(actual, SpanListAssert.class);
    }

    public SpanListAssert hasSpanWithName(String name) {
        isNotNull();
        boolean found = actual.stream().anyMatch(s -> s.getName().equals(name));
        if (!found) {
            failWithMessage("Expected to find span with name '%s' but none was found. Available spans: %s",
                    name, actual.stream().map(SpanData::getName).toList());
        }
        return this;
    }

    public SpanListAssert hasSize(int expectedSize) {
        isNotNull();
        if (actual.size() != expectedSize) {
            failWithMessage("Expected %d spans but found %d", expectedSize, actual.size());
        }
        return this;
    }

    /**
     * Returns a SpanAssert for the first span matching the given name.
     */
    public SpanAssert span(String name) {
        isNotNull();
        SpanData span = actual.stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElse(null);
        if (span == null) {
            failWithMessage("No span found with name '%s'", name);
        }
        return new SpanAssert(span);
    }
}
