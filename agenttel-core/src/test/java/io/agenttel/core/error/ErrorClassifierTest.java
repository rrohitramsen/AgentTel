package io.agenttel.core.error;

import io.agenttel.api.ErrorCategory;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorClassifierTest {

    private ErrorClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ErrorClassifier();
    }

    @Test
    void timeoutExceptionClassifiedAsDependencyTimeout() {
        SpanData span = errorSpanWithException("java.net.SocketTimeoutException", "Read timed out");

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.DEPENDENCY_TIMEOUT, result.category());
        assertEquals("java.net.SocketTimeoutException", result.rootException());
    }

    @Test
    void connectionExceptionClassifiedAsConnectionError() {
        SpanData span = errorSpanWithException("java.net.ConnectException", "Connection refused");

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.CONNECTION_ERROR, result.category());
    }

    @Test
    void nullPointerExceptionClassifiedAsCodeBug() {
        SpanData span = errorSpanWithException("java.lang.NullPointerException", "Cannot invoke method on null");

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.CODE_BUG, result.category());
    }

    @Test
    void http429ClassifiedAsRateLimited() {
        SpanData span = errorSpanWithHttpStatus(429);

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.RATE_LIMITED, result.category());
    }

    @Test
    void http401ClassifiedAsAuthFailure() {
        SpanData span = errorSpanWithHttpStatus(401);

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.AUTH_FAILURE, result.category());
    }

    @Test
    void http403ClassifiedAsAuthFailure() {
        SpanData span = errorSpanWithHttpStatus(403);

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.AUTH_FAILURE, result.category());
    }

    @Test
    void http400ClassifiedAsDataValidation() {
        SpanData span = errorSpanWithHttpStatus(400);

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.DATA_VALIDATION, result.category());
    }

    @Test
    void outOfMemoryErrorClassifiedAsResourceExhaustion() {
        SpanData span = errorSpanWithException("java.lang.OutOfMemoryError", "Java heap space");

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.RESOURCE_EXHAUSTION, result.category());
    }

    @Test
    void unknownExceptionClassifiedAsUnknown() {
        SpanData span = errorSpanWithException("com.example.CustomException", "Something went wrong");

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.UNKNOWN, result.category());
    }

    @Test
    void nonErrorSpanReturnsNull() {
        SpanData span = new TestSpanDataBuilder()
                .name("GET /api/health")
                .status(StatusData.ok())
                .attributes(Attributes.empty())
                .build();

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNull(result);
    }

    @Test
    void unsetStatusReturnsNull() {
        SpanData span = new TestSpanDataBuilder()
                .name("GET /api/health")
                .status(StatusData.unset())
                .attributes(Attributes.empty())
                .build();

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNull(result);
    }

    @Test
    void dependencyExtractedFromDbSystemAttribute() {
        Attributes attrs = Attributes.of(
                AttributeKey.stringKey("db.system"), "postgresql",
                AttributeKey.longKey("http.response.status_code"), 500L
        );
        SpanData span = new TestSpanDataBuilder()
                .name("SELECT users")
                .status(StatusData.error())
                .attributes(attrs)
                .kind(SpanKind.CLIENT)
                .build();

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals("postgresql", result.dependency());
    }

    @Test
    void dependencyExtractedFromRpcServiceAttribute() {
        Attributes attrs = Attributes.of(
                AttributeKey.stringKey("rpc.service"), "UserService"
        );
        SpanData span = new TestSpanDataBuilder()
                .name("grpc.UserService/GetUser")
                .status(StatusData.error())
                .attributes(attrs)
                .exceptionEvent("java.net.SocketTimeoutException", "timeout")
                .build();

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.DEPENDENCY_TIMEOUT, result.category());
        assertEquals("UserService", result.dependency());
    }

    @Test
    void exceptionMessageUsedForTimeoutClassification() {
        // Exception type is generic but message indicates timeout
        SpanData span = errorSpanWithException("java.io.IOException", "Request timed out after 30s");

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.DEPENDENCY_TIMEOUT, result.category());
    }

    @Test
    void exceptionMessageUsedForRateLimitClassification() {
        SpanData span = errorSpanWithException("java.io.IOException", "rate limit exceeded");

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.RATE_LIMITED, result.category());
    }

    @Test
    void exceptionMessageUsedForConnectionRefusedClassification() {
        SpanData span = errorSpanWithException("java.io.IOException", "connection refused by host");

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.CONNECTION_ERROR, result.category());
    }

    @Test
    void exceptionClassificationTakesPriorityOverHttpStatus() {
        // Exception says timeout, HTTP says 500; exception should win
        Attributes attrs = Attributes.of(
                AttributeKey.longKey("http.response.status_code"), 500L
        );
        EventData exceptionEvent = EventData.create(
                0L, "exception",
                Attributes.of(
                        AttributeKey.stringKey("exception.type"), "java.net.SocketTimeoutException",
                        AttributeKey.stringKey("exception.message"), "Read timed out"
                )
        );

        SpanData span = new TestSpanDataBuilder()
                .name("POST /api/orders")
                .status(StatusData.error())
                .attributes(attrs)
                .events(List.of(exceptionEvent))
                .build();

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.DEPENDENCY_TIMEOUT, result.category());
    }

    @Test
    void errorSpanWithNoExceptionAndNoHttpStatusReturnsUnknown() {
        SpanData span = new TestSpanDataBuilder()
                .name("internal-task")
                .status(StatusData.error())
                .attributes(Attributes.empty())
                .build();

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.UNKNOWN, result.category());
    }

    @Test
    void http504ClassifiedAsDependencyTimeout() {
        SpanData span = errorSpanWithHttpStatus(504);

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.DEPENDENCY_TIMEOUT, result.category());
    }

    @Test
    void http503ClassifiedAsConnectionError() {
        SpanData span = errorSpanWithHttpStatus(503);

        ErrorClassifier.ErrorClassification result = classifier.classify(span);

        assertNotNull(result);
        assertEquals(ErrorCategory.CONNECTION_ERROR, result.category());
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private SpanData errorSpanWithException(String exceptionType, String exceptionMessage) {
        EventData exceptionEvent = EventData.create(
                0L, "exception",
                Attributes.of(
                        AttributeKey.stringKey("exception.type"), exceptionType,
                        AttributeKey.stringKey("exception.message"), exceptionMessage
                )
        );

        return new TestSpanDataBuilder()
                .name("test-span")
                .status(StatusData.error())
                .attributes(Attributes.empty())
                .events(List.of(exceptionEvent))
                .build();
    }

    private SpanData errorSpanWithHttpStatus(int statusCode) {
        Attributes attrs = Attributes.of(
                AttributeKey.longKey("http.response.status_code"), (long) statusCode
        );
        return new TestSpanDataBuilder()
                .name("HTTP request")
                .status(StatusData.error())
                .attributes(attrs)
                .build();
    }

    // ── TestSpanData builder ────────────────────────────────────────────

    private static class TestSpanDataBuilder {
        private String name = "test-span";
        private StatusData status = StatusData.ok();
        private Attributes attributes = Attributes.empty();
        private List<EventData> events = Collections.emptyList();
        private SpanKind kind = SpanKind.SERVER;

        TestSpanDataBuilder name(String name) { this.name = name; return this; }
        TestSpanDataBuilder status(StatusData status) { this.status = status; return this; }
        TestSpanDataBuilder attributes(Attributes attributes) { this.attributes = attributes; return this; }
        TestSpanDataBuilder events(List<EventData> events) { this.events = events; return this; }
        TestSpanDataBuilder kind(SpanKind kind) { this.kind = kind; return this; }

        TestSpanDataBuilder exceptionEvent(String type, String message) {
            this.events = List.of(EventData.create(
                    0L, "exception",
                    Attributes.of(
                            AttributeKey.stringKey("exception.type"), type,
                            AttributeKey.stringKey("exception.message"), message
                    )
            ));
            return this;
        }

        SpanData build() {
            return new TestSpanDataImpl(name, status, attributes, events, kind);
        }
    }

    private static class TestSpanDataImpl implements SpanData {
        private final String name;
        private final StatusData status;
        private final Attributes attributes;
        private final List<EventData> events;
        private final SpanKind kind;

        TestSpanDataImpl(String name, StatusData status, Attributes attributes,
                         List<EventData> events, SpanKind kind) {
            this.name = name;
            this.status = status;
            this.attributes = attributes;
            this.events = events;
            this.kind = kind;
        }

        @Override public SpanContext getSpanContext() {
            return SpanContext.create(
                    "00000000000000000000000000000001",
                    "0000000000000001",
                    TraceFlags.getSampled(), TraceState.getDefault());
        }
        @Override public SpanContext getParentSpanContext() { return SpanContext.getInvalid(); }
        @Override public Resource getResource() { return Resource.getDefault(); }
        @Override public InstrumentationScopeInfo getInstrumentationScopeInfo() { return InstrumentationScopeInfo.empty(); }
        @SuppressWarnings("deprecation")
        @Override public io.opentelemetry.sdk.common.InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
            return io.opentelemetry.sdk.common.InstrumentationLibraryInfo.empty();
        }
        @Override public String getName() { return name; }
        @Override public SpanKind getKind() { return kind; }
        @Override public long getStartEpochNanos() { return 0; }
        @Override public Attributes getAttributes() { return attributes; }
        @Override public List<EventData> getEvents() { return events; }
        @Override public List<LinkData> getLinks() { return Collections.emptyList(); }
        @Override public StatusData getStatus() { return status; }
        @Override public long getEndEpochNanos() { return 1_000_000; }
        @Override public boolean hasEnded() { return true; }
        @Override public int getTotalRecordedEvents() { return events.size(); }
        @Override public int getTotalRecordedLinks() { return 0; }
        @Override public int getTotalAttributeCount() { return attributes.size(); }
    }
}
