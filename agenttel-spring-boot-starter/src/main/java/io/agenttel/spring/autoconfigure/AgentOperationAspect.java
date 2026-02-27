package io.agenttel.spring.autoconfigure;

import io.agenttel.core.engine.AgentTelEngine;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
/**
 * AOP aspect that wraps methods annotated with {@code @AgentOperation}.
 * If an OTel span already exists (from Spring MVC auto-instrumentation),
 * this aspect does nothing — the SpanProcessor handles enrichment.
 * If no span exists, it creates one.
 */
@Aspect
public class AgentOperationAspect {

    private final AgentTelEngine engine;

    public AgentOperationAspect(AgentTelEngine engine) {
        this.engine = engine;
    }

    @Around("@annotation(io.agenttel.api.annotations.AgentOperation)")
    public Object aroundAgentOperation(ProceedingJoinPoint pjp) throws Throwable {
        Span currentSpan = Span.current();

        if (currentSpan.getSpanContext().isValid()) {
            // Span already exists from OTel auto-instrumentation (e.g., Spring MVC).
            // The AgentTelSpanProcessor will have already enriched it in onStart.
            return pjp.proceed();
        }

        // If no span exists, create one
        String operationName = deriveOperationName(pjp);
        Tracer tracer = engine.openTelemetry().getTracer("io.agenttel");
        Span span = tracer.spanBuilder(operationName).startSpan();
        try (Scope scope = span.makeCurrent()) {
            return pjp.proceed();
        } catch (Throwable t) {
            span.setStatus(StatusCode.ERROR, t.getMessage());
            span.recordException(t);
            throw t;
        } finally {
            span.end();
        }
    }

    private String deriveOperationName(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        return AgentTelAnnotationBeanPostProcessor.deriveOperationName(sig.getMethod());
    }
}
