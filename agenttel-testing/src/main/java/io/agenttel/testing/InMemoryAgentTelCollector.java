package io.agenttel.testing;

import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Collects OTel spans and log records in memory for test assertions.
 */
public class InMemoryAgentTelCollector {

    private final InMemorySpanExporter spanExporter;
    private final InMemoryLogRecordExporter logExporter;

    public InMemoryAgentTelCollector(InMemorySpanExporter spanExporter,
                                      InMemoryLogRecordExporter logExporter) {
        this.spanExporter = spanExporter;
        this.logExporter = logExporter;
    }

    public List<SpanData> getSpans() {
        return spanExporter.getFinishedSpanItems();
    }

    public List<SpanData> getSpansByName(String name) {
        return spanExporter.getFinishedSpanItems().stream()
                .filter(span -> span.getName().equals(name))
                .collect(Collectors.toList());
    }

    public List<LogRecordData> getEvents() {
        return logExporter.getFinishedLogRecordItems();
    }

    public List<LogRecordData> getEventsByName(String eventName) {
        return logExporter.getFinishedLogRecordItems().stream()
                .filter(log -> {
                    var attr = log.getAttributes().get(
                            io.opentelemetry.api.common.AttributeKey.stringKey("event.name"));
                    return eventName.equals(attr);
                })
                .collect(Collectors.toList());
    }

    public void reset() {
        spanExporter.reset();
        logExporter.reset();
    }

    public InMemorySpanExporter getSpanExporter() {
        return spanExporter;
    }

    public InMemoryLogRecordExporter getLogExporter() {
        return logExporter;
    }
}
