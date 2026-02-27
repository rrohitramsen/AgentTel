package io.agenttel.spring.autoconfigure;

import io.agenttel.api.EscalationLevel;
import io.agenttel.api.annotations.AgentObservable;
import io.agenttel.api.annotations.AgentOperation;
import io.agenttel.api.annotations.DeclareConsumer;
import io.agenttel.api.annotations.DeclareDependency;
import io.agenttel.api.topology.ConsumerDescriptor;
import io.agenttel.api.topology.DependencyDescriptor;
import io.agenttel.core.baseline.StaticBaselineProvider;
import io.agenttel.core.enrichment.OperationContext;
import io.agenttel.core.enrichment.OperationContextRegistry;
import io.agenttel.core.topology.TopologyRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

/**
 * Scans Spring beans for AgentTel annotations at startup and populates
 * the TopologyRegistry and OperationContextRegistry.
 */
public class AgentTelAnnotationBeanPostProcessor implements BeanPostProcessor {

    private final TopologyRegistry topologyRegistry;
    private final StaticBaselineProvider baselineProvider;
    private final OperationContextRegistry operationContextRegistry;

    public AgentTelAnnotationBeanPostProcessor(TopologyRegistry topologyRegistry,
                                                StaticBaselineProvider baselineProvider,
                                                OperationContextRegistry operationContextRegistry) {
        this.topologyRegistry = topologyRegistry;
        this.baselineProvider = baselineProvider;
        this.operationContextRegistry = operationContextRegistry;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();

        // Handle CGLIB proxies — get the actual user class
        if (clazz.getName().contains("$$")) {
            clazz = clazz.getSuperclass();
        }

        scanClassAnnotations(clazz);
        scanMethodAnnotations(clazz);

        return bean;
    }

    private void scanClassAnnotations(Class<?> clazz) {
        AgentObservable observable = clazz.getAnnotation(AgentObservable.class);
        if (observable != null) {
            // Annotations fill gaps — don't override config-set values
            if (topologyRegistry.getTeam().isEmpty() && !observable.team().isEmpty()) {
                topologyRegistry.setTeam(observable.team());
            }
            if (!observable.domain().isEmpty() && topologyRegistry.getDomain().isEmpty()) {
                topologyRegistry.setDomain(observable.domain());
            }
            if (!observable.onCallChannel().isEmpty() && topologyRegistry.getOnCallChannel().isEmpty()) {
                topologyRegistry.setOnCallChannel(observable.onCallChannel());
            }
        }

        // Scan repeatable dependencies
        for (DeclareDependency dep : clazz.getAnnotationsByType(DeclareDependency.class)) {
            if (topologyRegistry.getDependency(dep.name()).isEmpty()) {
                topologyRegistry.registerDependency(new DependencyDescriptor(
                        dep.name(), dep.type(), dep.criticality(), dep.protocol(),
                        dep.timeoutMs(), dep.circuitBreaker(), dep.fallback(), dep.healthEndpoint()
                ));
            }
        }

        // Scan repeatable consumers
        for (DeclareConsumer consumer : clazz.getAnnotationsByType(DeclareConsumer.class)) {
            topologyRegistry.registerConsumer(new ConsumerDescriptor(
                    consumer.name(), consumer.pattern(), consumer.slaLatencyMs()
            ));
        }
    }

    private void scanMethodAnnotations(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            AgentOperation op = method.getAnnotation(AgentOperation.class);
            if (op != null) {
                String operationName = deriveOperationName(method);

                // Register baseline
                baselineProvider.registerFromAnnotation(operationName, op);

                // Register operation context
                operationContextRegistry.register(operationName, new OperationContext(
                        op.retryable(),
                        op.idempotent(),
                        op.runbookUrl(),
                        op.fallbackDescription(),
                        op.escalationLevel(),
                        op.safeToRestart()
                ));
            }
        }
    }

    /**
     * Derives the operation name from a method's Spring MVC annotations.
     * Convention: "HTTP_METHOD PATH" (e.g., "POST /api/payments").
     */
    static String deriveOperationName(Method method) {
        // Check specific HTTP method annotations first
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) {
            return "POST " + resolvePath(method, post.value());
        }
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) {
            return "GET " + resolvePath(method, get.value());
        }
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) {
            return "PUT " + resolvePath(method, put.value());
        }
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        if (delete != null) {
            return "DELETE " + resolvePath(method, delete.value());
        }
        PatchMapping patch = method.getAnnotation(PatchMapping.class);
        if (patch != null) {
            return "PATCH " + resolvePath(method, patch.value());
        }

        // Fall back to class-level @RequestMapping + method name
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    private static String resolvePath(Method method, String[] paths) {
        String classPath = "";
        RequestMapping classMapping = method.getDeclaringClass().getAnnotation(RequestMapping.class);
        if (classMapping != null && classMapping.value().length > 0) {
            classPath = classMapping.value()[0];
        }

        String methodPath = (paths.length > 0) ? paths[0] : "";
        return classPath + methodPath;
    }
}
