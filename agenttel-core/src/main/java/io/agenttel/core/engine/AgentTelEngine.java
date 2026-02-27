package io.agenttel.core.engine;

import io.agenttel.core.anomaly.AnomalyDetector;
import io.agenttel.core.anomaly.PatternMatcher;
import io.agenttel.core.baseline.BaselineProvider;
import io.agenttel.core.baseline.CompositeBaselineProvider;
import io.agenttel.core.baseline.RollingBaselineProvider;
import io.agenttel.core.baseline.StaticBaselineProvider;
import io.agenttel.core.causality.CausalityTracker;
import io.agenttel.core.enrichment.AgentTelSpanProcessor;
import io.agenttel.core.enrichment.OperationContextRegistry;
import io.agenttel.core.events.AgentTelEventEmitter;
import io.agenttel.core.events.DeploymentEventEmitter;
import io.agenttel.core.slo.SloTracker;
import io.agenttel.core.topology.TopologyRegistry;
import io.opentelemetry.api.OpenTelemetry;

/**
 * Main orchestrator for AgentTel. Ties together topology, baselines,
 * enrichment, events, anomaly detection, SLO tracking, and causality tracking.
 */
public class AgentTelEngine {

    private final TopologyRegistry topology;
    private final BaselineProvider baselineProvider;
    private final OperationContextRegistry operationContexts;
    private final AnomalyDetector anomalyDetector;
    private final PatternMatcher patternMatcher;
    private final RollingBaselineProvider rollingBaselines;
    private final SloTracker sloTracker;
    private final CausalityTracker causalityTracker;
    private final AgentTelEventEmitter eventEmitter;
    private final DeploymentEventEmitter deploymentEventEmitter;
    private final OpenTelemetry openTelemetry;

    private AgentTelEngine(Builder builder) {
        this.topology = builder.topologyRegistry;
        this.operationContexts = builder.operationContexts;
        this.anomalyDetector = builder.anomalyDetector;
        this.patternMatcher = builder.patternMatcher;
        this.rollingBaselines = builder.rollingBaselines;
        this.sloTracker = builder.sloTracker;
        this.causalityTracker = builder.causalityTracker;
        this.openTelemetry = builder.openTelemetry;
        this.eventEmitter = new AgentTelEventEmitter(openTelemetry);
        this.deploymentEventEmitter = new DeploymentEventEmitter(eventEmitter);

        // Build composite baseline: static takes precedence, then rolling
        if (builder.baselineProvider != null && builder.rollingBaselines != null) {
            this.baselineProvider = new CompositeBaselineProvider(
                    builder.baselineProvider, builder.rollingBaselines);
        } else if (builder.baselineProvider != null) {
            this.baselineProvider = builder.baselineProvider;
        } else if (builder.rollingBaselines != null) {
            this.baselineProvider = builder.rollingBaselines;
        } else {
            this.baselineProvider = new StaticBaselineProvider();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public TopologyRegistry topology() { return topology; }
    public BaselineProvider baselines() { return baselineProvider; }
    public OperationContextRegistry operationContexts() { return operationContexts; }
    public AnomalyDetector anomalyDetector() { return anomalyDetector; }
    public PatternMatcher patternMatcher() { return patternMatcher; }
    public RollingBaselineProvider rollingBaselines() { return rollingBaselines; }
    public SloTracker sloTracker() { return sloTracker; }
    public CausalityTracker causalityTracker() { return causalityTracker; }
    public AgentTelEventEmitter events() { return eventEmitter; }
    public DeploymentEventEmitter deploymentEvents() { return deploymentEventEmitter; }
    public OpenTelemetry openTelemetry() { return openTelemetry; }

    /**
     * Creates the SpanProcessor that enriches spans with AgentTel attributes.
     */
    public AgentTelSpanProcessor createSpanProcessor() {
        return new AgentTelSpanProcessor(
                baselineProvider, operationContexts,
                anomalyDetector, patternMatcher, rollingBaselines, sloTracker, eventEmitter);
    }

    public static class Builder {
        private TopologyRegistry topologyRegistry = new TopologyRegistry();
        private BaselineProvider baselineProvider = new StaticBaselineProvider();
        private OperationContextRegistry operationContexts = new OperationContextRegistry();
        private AnomalyDetector anomalyDetector = new AnomalyDetector(3.0);
        private PatternMatcher patternMatcher = new PatternMatcher();
        private RollingBaselineProvider rollingBaselines = new RollingBaselineProvider();
        private SloTracker sloTracker = new SloTracker();
        private CausalityTracker causalityTracker = new CausalityTracker();
        private OpenTelemetry openTelemetry = OpenTelemetry.noop();

        public Builder topologyRegistry(TopologyRegistry topologyRegistry) {
            this.topologyRegistry = topologyRegistry;
            return this;
        }

        public Builder baselineProvider(BaselineProvider baselineProvider) {
            this.baselineProvider = baselineProvider;
            return this;
        }

        public Builder operationContexts(OperationContextRegistry operationContexts) {
            this.operationContexts = operationContexts;
            return this;
        }

        public Builder anomalyDetector(AnomalyDetector anomalyDetector) {
            this.anomalyDetector = anomalyDetector;
            return this;
        }

        public Builder patternMatcher(PatternMatcher patternMatcher) {
            this.patternMatcher = patternMatcher;
            return this;
        }

        public Builder rollingBaselines(RollingBaselineProvider rollingBaselines) {
            this.rollingBaselines = rollingBaselines;
            return this;
        }

        public Builder sloTracker(SloTracker sloTracker) {
            this.sloTracker = sloTracker;
            return this;
        }

        public Builder causalityTracker(CausalityTracker causalityTracker) {
            this.causalityTracker = causalityTracker;
            return this;
        }

        public Builder openTelemetry(OpenTelemetry openTelemetry) {
            this.openTelemetry = openTelemetry;
            return this;
        }

        public AgentTelEngine build() {
            return new AgentTelEngine(this);
        }
    }
}
