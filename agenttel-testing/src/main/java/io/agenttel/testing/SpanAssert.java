package io.agenttel.testing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.assertj.core.api.AbstractAssert;

/**
 * Fluent assertions for a single OTel span.
 */
public class SpanAssert extends AbstractAssert<SpanAssert, SpanData> {

    public SpanAssert(SpanData actual) {
        super(actual, SpanAssert.class);
    }

    public <T> SpanAssert hasAttribute(AttributeKey<T> key) {
        isNotNull();
        T value = actual.getAttributes().get(key);
        if (value == null) {
            failWithMessage("Expected span '%s' to have attribute '%s' but it was absent",
                    actual.getName(), key.getKey());
        }
        return this;
    }

    public <T> SpanAssert hasAttribute(AttributeKey<T> key, T expectedValue) {
        isNotNull();
        T value = actual.getAttributes().get(key);
        if (value == null) {
            failWithMessage("Expected span '%s' to have attribute '%s' but it was absent",
                    actual.getName(), key.getKey());
        } else if (!value.equals(expectedValue)) {
            failWithMessage("Expected span '%s' attribute '%s' to be <%s> but was <%s>",
                    actual.getName(), key.getKey(), expectedValue, value);
        }
        return this;
    }

    public SpanAssert hasName(String expectedName) {
        isNotNull();
        if (!actual.getName().equals(expectedName)) {
            failWithMessage("Expected span name to be '%s' but was '%s'",
                    expectedName, actual.getName());
        }
        return this;
    }
}
