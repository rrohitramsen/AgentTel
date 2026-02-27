package io.agenttel.genai.bedrock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * Auto-configuration for AWS Bedrock instrumentation.
 * Activates when BedrockRuntimeClient is on the classpath.
 *
 * Unlike other provider auto-configurations, Bedrock uses a utility approach
 * (BedrockTracing.tracedConverse) rather than client wrapping, since
 * BedrockRuntimeClient is a complex AWS SDK interface.
 *
 * This configuration serves as a marker for classpath detection.
 */
@AutoConfiguration
@ConditionalOnClass(name = "software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient")
public class BedrockGenAiAutoConfiguration {
    // Bedrock instrumentation is done via the BedrockTracing utility class.
    // This auto-configuration exists to register in the Spring Boot auto-config chain.
}
