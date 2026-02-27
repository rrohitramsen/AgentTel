package io.agenttel.core.enrichment;

import io.agenttel.api.EscalationLevel;

/**
 * Decision metadata for an operation, extracted from {@code @AgentOperation} annotations.
 */
public class OperationContext {
    private final boolean retryable;
    private final boolean idempotent;
    private final String runbookUrl;
    private final String fallbackDescription;
    private final EscalationLevel escalationLevel;
    private final boolean safeToRestart;

    public OperationContext(boolean retryable, boolean idempotent, String runbookUrl,
                           String fallbackDescription, EscalationLevel escalationLevel,
                           boolean safeToRestart) {
        this.retryable = retryable;
        this.idempotent = idempotent;
        this.runbookUrl = runbookUrl;
        this.fallbackDescription = fallbackDescription;
        this.escalationLevel = escalationLevel;
        this.safeToRestart = safeToRestart;
    }

    public boolean isRetryable() { return retryable; }
    public boolean isIdempotent() { return idempotent; }
    public String getRunbookUrl() { return runbookUrl; }
    public String getFallbackDescription() { return fallbackDescription; }
    public EscalationLevel getEscalationLevel() { return escalationLevel; }
    public boolean isSafeToRestart() { return safeToRestart; }
}
