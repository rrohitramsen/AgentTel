package io.agenttel.testing;

import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit 5 extension that sets up an in-memory OTel SDK with an
 * {@link InMemoryAgentTelCollector} available for parameter injection.
 */
public class AgentTelTestExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(AgentTelTestExtension.class);

    @Override
    public void beforeEach(ExtensionContext context) {
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        InMemoryLogRecordExporter logExporter = InMemoryLogRecordExporter.create();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
                .build();

        InMemoryAgentTelCollector collector = new InMemoryAgentTelCollector(spanExporter, logExporter);

        var store = context.getStore(NAMESPACE);
        store.put("collector", collector);
        store.put("tracerProvider", tracerProvider);
        store.put("loggerProvider", loggerProvider);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        var store = context.getStore(NAMESPACE);
        SdkTracerProvider tracerProvider = store.get("tracerProvider", SdkTracerProvider.class);
        SdkLoggerProvider loggerProvider = store.get("loggerProvider", SdkLoggerProvider.class);
        if (tracerProvider != null) tracerProvider.close();
        if (loggerProvider != null) loggerProvider.close();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == InMemoryAgentTelCollector.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return extensionContext.getStore(NAMESPACE).get("collector", InMemoryAgentTelCollector.class);
    }
}
