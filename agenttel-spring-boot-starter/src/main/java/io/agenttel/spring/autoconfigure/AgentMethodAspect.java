package io.agenttel.spring.autoconfigure;

import io.agenttel.agentic.config.AgentConfigRegistry;
import io.agenttel.agentic.trace.AgentInvocation;
import io.agenttel.agentic.trace.AgentTracer;
import io.agenttel.api.annotations.AgentMethod;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * AOP aspect that wraps methods annotated with {@code @AgentMethod}
 * in an {@link AgentInvocation} scope.
 *
 * <p>Config priority: YAML config (via {@link AgentConfigRegistry}) takes
 * precedence over annotation values. Annotation values serve as fallback
 * defaults when no YAML config is provided for the agent.
 */
@Aspect
public class AgentMethodAspect {

    private final AgentTracer tracer;
    private final AgentConfigRegistry configRegistry;

    public AgentMethodAspect(AgentTracer tracer, AgentConfigRegistry configRegistry) {
        this.tracer = tracer;
        this.configRegistry = configRegistry;
    }

    @Around("@annotation(io.agenttel.api.annotations.AgentMethod)")
    public Object aroundAgentMethod(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();

        // Handle CGLIB proxies — get annotation from actual user class
        if (!method.isAnnotationPresent(AgentMethod.class)) {
            Method superMethod = pjp.getTarget().getClass().getSuperclass()
                    .getMethod(method.getName(), method.getParameterTypes());
            if (superMethod.isAnnotationPresent(AgentMethod.class)) {
                method = superMethod;
            }
        }

        AgentMethod ann = method.getAnnotation(AgentMethod.class);

        // Resolve agent name: annotation > className.methodName
        String agentName = !ann.name().isEmpty() ? ann.name()
                : method.getDeclaringClass().getSimpleName() + "." + method.getName();

        String goal = method.getName();

        // tracer.invoke() already applies config from the registry (YAML > builder defaults)
        try (AgentInvocation inv = tracer.invoke(agentName, goal)) {
            // Apply annotation-level config as fallback (only if no YAML config exists)
            applyAnnotationDefaults(inv, ann, agentName);

            Object result = pjp.proceed();
            inv.complete(true);
            return result;
        } catch (Throwable t) {
            // Invocation span is closed by try-with-resources;
            // complete() was not called so status remains unset (error implied by exception).
            throw t;
        }
    }

    /**
     * Applies annotation values as fallback when no YAML config exists for this agent.
     */
    private void applyAnnotationDefaults(AgentInvocation inv, AgentMethod ann, String agentName) {
        // YAML config (via configRegistry) takes priority — skip if already configured
        if (configRegistry.hasConfig(agentName)) {
            return;
        }

        // Apply annotation values as fallback defaults
        if (ann.maxSteps() > 0) {
            inv.maxSteps(ann.maxSteps());
        }
        if (!ann.type().isEmpty()) {
            inv.span().setAttribute(
                    io.agenttel.api.attributes.AgenticAttributes.AGENT_TYPE, ann.type());
        }
        if (!ann.framework().isEmpty()) {
            inv.span().setAttribute(
                    io.agenttel.api.attributes.AgenticAttributes.AGENT_FRAMEWORK, ann.framework());
        }
        if (!ann.version().isEmpty()) {
            inv.span().setAttribute(
                    io.agenttel.api.attributes.AgenticAttributes.AGENT_VERSION, ann.version());
        }
    }
}
