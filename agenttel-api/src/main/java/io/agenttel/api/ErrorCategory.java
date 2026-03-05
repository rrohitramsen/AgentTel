package io.agenttel.api;

/**
 * Categories of errors for agent decision-making.
 * Enables agents to choose the correct remediation based on error type.
 */
public enum ErrorCategory {
    DEPENDENCY_TIMEOUT("dependency_timeout"),
    CONNECTION_ERROR("connection_error"),
    CODE_BUG("code_bug"),
    RATE_LIMITED("rate_limited"),
    AUTH_FAILURE("auth_failure"),
    RESOURCE_EXHAUSTION("resource_exhaustion"),
    DATA_VALIDATION("data_validation"),
    UNKNOWN("unknown");

    private final String value;

    ErrorCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ErrorCategory fromValue(String value) {
        for (ErrorCategory cat : values()) {
            if (cat.value.equals(value)) {
                return cat;
            }
        }
        return UNKNOWN;
    }
}
