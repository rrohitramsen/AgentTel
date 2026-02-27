package io.agenttel.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for AgentTel.
 * Bind to the {@code agenttel.*} namespace in application.yml.
 */
@ConfigurationProperties(prefix = "agenttel")
public class AgentTelProperties {

    private boolean enabled = true;
    private TopologyProperties topology = new TopologyProperties();
    private List<DependencyProperties> dependencies = new ArrayList<>();
    private List<ConsumerProperties> consumers = new ArrayList<>();
    private BaselineProperties baselines = new BaselineProperties();
    private AnomalyDetectionProperties anomalyDetection = new AnomalyDetectionProperties();
    private DeploymentProperties deployment = new DeploymentProperties();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public TopologyProperties getTopology() { return topology; }
    public void setTopology(TopologyProperties topology) { this.topology = topology; }
    public List<DependencyProperties> getDependencies() { return dependencies; }
    public void setDependencies(List<DependencyProperties> dependencies) { this.dependencies = dependencies; }
    public List<ConsumerProperties> getConsumers() { return consumers; }
    public void setConsumers(List<ConsumerProperties> consumers) { this.consumers = consumers; }
    public BaselineProperties getBaselines() { return baselines; }
    public void setBaselines(BaselineProperties baselines) { this.baselines = baselines; }
    public AnomalyDetectionProperties getAnomalyDetection() { return anomalyDetection; }
    public void setAnomalyDetection(AnomalyDetectionProperties anomalyDetection) { this.anomalyDetection = anomalyDetection; }
    public DeploymentProperties getDeployment() { return deployment; }
    public void setDeployment(DeploymentProperties deployment) { this.deployment = deployment; }

    public static class TopologyProperties {
        private String team = "";
        private String tier = "standard";
        private String domain = "";
        private String onCallChannel = "";
        private String repoUrl = "";

        public String getTeam() { return team; }
        public void setTeam(String team) { this.team = team; }
        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
        public String getOnCallChannel() { return onCallChannel; }
        public void setOnCallChannel(String onCallChannel) { this.onCallChannel = onCallChannel; }
        public String getRepoUrl() { return repoUrl; }
        public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    }

    public static class DependencyProperties {
        private String name;
        private String type;
        private String criticality = "required";
        private String protocol = "";
        private int timeoutMs = 0;
        private boolean circuitBreaker = false;
        private String fallback = "";
        private String healthEndpoint = "";

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getCriticality() { return criticality; }
        public void setCriticality(String criticality) { this.criticality = criticality; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        public boolean isCircuitBreaker() { return circuitBreaker; }
        public void setCircuitBreaker(boolean circuitBreaker) { this.circuitBreaker = circuitBreaker; }
        public String getFallback() { return fallback; }
        public void setFallback(String fallback) { this.fallback = fallback; }
        public String getHealthEndpoint() { return healthEndpoint; }
        public void setHealthEndpoint(String healthEndpoint) { this.healthEndpoint = healthEndpoint; }
    }

    public static class ConsumerProperties {
        private String name;
        private String pattern = "sync";
        private int slaLatencyMs = 0;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public int getSlaLatencyMs() { return slaLatencyMs; }
        public void setSlaLatencyMs(int slaLatencyMs) { this.slaLatencyMs = slaLatencyMs; }
    }

    public static class BaselineProperties {
        private String source = "static";
        private int rollingWindowSize = 1000;
        private int rollingMinSamples = 10;

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public int getRollingWindowSize() { return rollingWindowSize; }
        public void setRollingWindowSize(int rollingWindowSize) { this.rollingWindowSize = rollingWindowSize; }
        public int getRollingMinSamples() { return rollingMinSamples; }
        public void setRollingMinSamples(int rollingMinSamples) { this.rollingMinSamples = rollingMinSamples; }
    }

    public static class AnomalyDetectionProperties {
        private boolean enabled = true;
        private double zScoreThreshold = 3.0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getZScoreThreshold() { return zScoreThreshold; }
        public void setZScoreThreshold(double zScoreThreshold) { this.zScoreThreshold = zScoreThreshold; }
    }

    public static class DeploymentProperties {
        private boolean emitOnStartup = true;
        private String version = "";
        private String commitSha = "";

        public boolean isEmitOnStartup() { return emitOnStartup; }
        public void setEmitOnStartup(boolean emitOnStartup) { this.emitOnStartup = emitOnStartup; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getCommitSha() { return commitSha; }
        public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
    }
}
