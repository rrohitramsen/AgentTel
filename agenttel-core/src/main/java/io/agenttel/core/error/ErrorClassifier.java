package io.agenttel.core.error;

import io.agenttel.api.ErrorCategory;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.util.List;

/**
 * Classifies span errors into categories that agents can act on.
 * Stateless and thread-safe.
 */
public class ErrorClassifier {

    /**
     * Classifies the error in a span based on exception type, HTTP status, and attributes.
     */
    public ErrorClassification classify(SpanData span) {
        if (span.getStatus().getStatusCode() != StatusCode.ERROR) {
            return null;
        }

        // Extract exception info from span events
        String exceptionType = null;
        String exceptionMessage = null;
        for (EventData event : span.getEvents()) {
            if ("exception".equals(event.getName())) {
                Attributes eventAttrs = event.getAttributes();
                String type = eventAttrs.get(io.opentelemetry.api.common.AttributeKey.stringKey("exception.type"));
                if (type != null) {
                    exceptionType = type;
                }
                String msg = eventAttrs.get(io.opentelemetry.api.common.AttributeKey.stringKey("exception.message"));
                if (msg != null) {
                    exceptionMessage = msg;
                }
                break;
            }
        }

        // Extract HTTP status code
        Attributes attrs = span.getAttributes();
        Long httpStatusCode = attrs.get(io.opentelemetry.api.common.AttributeKey.longKey("http.response.status_code"));
        if (httpStatusCode == null) {
            httpStatusCode = attrs.get(io.opentelemetry.api.common.AttributeKey.longKey("http.status_code"));
        }

        // Extract dependency info
        String dependency = extractDependency(attrs);

        // Classify by exception type first
        ErrorCategory category = classifyByException(exceptionType, exceptionMessage);
        if (category == null && httpStatusCode != null) {
            category = classifyByHttpStatus(httpStatusCode.intValue());
        }
        if (category == null) {
            category = ErrorCategory.UNKNOWN;
        }

        // If it's a dependency-related error, try to identify which dependency
        if (dependency == null && isDependencyError(category)) {
            dependency = inferDependencyFromSpan(span);
        }

        return new ErrorClassification(
                category,
                exceptionType != null ? exceptionType : "",
                dependency
        );
    }

    private ErrorCategory classifyByException(String exceptionType, String exceptionMessage) {
        if (exceptionType == null) {
            return null;
        }

        String type = exceptionType.toLowerCase();

        // Timeout errors
        if (type.contains("timeout") || type.contains("sockettimeout")
                || type.contains("connecttimeout") || type.contains("readtimeout")) {
            return ErrorCategory.DEPENDENCY_TIMEOUT;
        }

        // Connection errors
        if (type.contains("connectexception") || type.contains("connectionrefused")
                || type.contains("unknownhost") || type.contains("nohttpresponse")
                || type.contains("connectionreset") || type.contains("brokenpipe")) {
            return ErrorCategory.CONNECTION_ERROR;
        }

        // Resource exhaustion
        if (type.contains("outofmemory") || type.contains("stackoverflow")
                || type.contains("threadpool") || type.contains("toomanyrequests")) {
            return ErrorCategory.RESOURCE_EXHAUSTION;
        }

        // Data validation
        if (type.contains("validation") || type.contains("illegalargument")
                || type.contains("constraintviolation") || type.contains("methodargumentnotvalid")
                || type.contains("bindexception") || type.contains("jsonparse")
                || type.contains("httpmessagenot")) {
            return ErrorCategory.DATA_VALIDATION;
        }

        // Authentication/authorization
        if (type.contains("accessdenied") || type.contains("authentication")
                || type.contains("authorization") || type.contains("forbidden")
                || type.contains("unauthorized") || type.contains("securityexception")) {
            return ErrorCategory.AUTH_FAILURE;
        }

        // Code bugs
        if (type.contains("nullpointer") || type.contains("classcast")
                || type.contains("indexoutofbounds") || type.contains("illegalstate")
                || type.contains("unsupportedoperation") || type.contains("arithmeticexception")
                || type.contains("numberformat") || type.contains("concurrentmodification")) {
            return ErrorCategory.CODE_BUG;
        }

        // Check message for additional hints
        if (exceptionMessage != null) {
            String msg = exceptionMessage.toLowerCase();
            if (msg.contains("timed out") || msg.contains("timeout")) {
                return ErrorCategory.DEPENDENCY_TIMEOUT;
            }
            if (msg.contains("connection refused") || msg.contains("connection reset")) {
                return ErrorCategory.CONNECTION_ERROR;
            }
            if (msg.contains("rate limit") || msg.contains("too many requests") || msg.contains("throttl")) {
                return ErrorCategory.RATE_LIMITED;
            }
        }

        return null;
    }

    private ErrorCategory classifyByHttpStatus(int statusCode) {
        return switch (statusCode) {
            case 400, 422 -> ErrorCategory.DATA_VALIDATION;
            case 401, 403 -> ErrorCategory.AUTH_FAILURE;
            case 429 -> ErrorCategory.RATE_LIMITED;
            case 408, 504 -> ErrorCategory.DEPENDENCY_TIMEOUT;
            case 502, 503 -> ErrorCategory.CONNECTION_ERROR;
            default -> {
                if (statusCode >= 500) {
                    yield ErrorCategory.UNKNOWN;
                }
                yield null;
            }
        };
    }

    private String extractDependency(Attributes attrs) {
        // Try standard OTel semantic conventions for dependency identification
        String dbSystem = attrs.get(io.opentelemetry.api.common.AttributeKey.stringKey("db.system"));
        if (dbSystem != null) return dbSystem;

        String rpcService = attrs.get(io.opentelemetry.api.common.AttributeKey.stringKey("rpc.service"));
        if (rpcService != null) return rpcService;

        String serverAddress = attrs.get(io.opentelemetry.api.common.AttributeKey.stringKey("server.address"));
        if (serverAddress != null) return serverAddress;

        String peerService = attrs.get(io.opentelemetry.api.common.AttributeKey.stringKey("peer.service"));
        if (peerService != null) return peerService;

        return null;
    }

    private String inferDependencyFromSpan(SpanData span) {
        // For client spans, try to infer the dependency from span name or attributes
        if (span.getKind() == io.opentelemetry.api.trace.SpanKind.CLIENT) {
            return extractDependency(span.getAttributes());
        }
        return null;
    }

    private boolean isDependencyError(ErrorCategory category) {
        return category == ErrorCategory.DEPENDENCY_TIMEOUT
                || category == ErrorCategory.CONNECTION_ERROR
                || category == ErrorCategory.RATE_LIMITED;
    }

    public record ErrorClassification(
            ErrorCategory category,
            String rootException,
            String dependency
    ) {}
}
