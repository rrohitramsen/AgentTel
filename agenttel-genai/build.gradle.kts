plugins {
    `java-library`
}

description = "AgentTel GenAI - Instrumentation for AI/LLM frameworks and provider SDKs"

dependencies {
    api(project(":agenttel-core"))

    // OTel SDK (for SpanProcessor in Spring AI enricher)
    implementation(platform("io.opentelemetry:opentelemetry-bom:${rootProject.extra["otelVersion"]}"))
    implementation("io.opentelemetry:opentelemetry-sdk-trace")

    // OTel GenAI semantic conventions (incubating)
    implementation("io.opentelemetry.semconv:opentelemetry-semconv-incubating:${rootProject.extra["otelSemconvIncubatingVersion"]}")

    // Spring Boot auto-configuration support
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:${rootProject.extra["springBootVersion"]}")

    // GenAI frameworks - all compileOnly (users provide their own runtime versions)
    compileOnly("org.springframework.ai:spring-ai-core:${rootProject.extra["springAiVersion"]}")
    compileOnly("dev.langchain4j:langchain4j-core:${rootProject.extra["langchain4jVersion"]}")

    // Provider SDKs - compileOnly
    compileOnly("com.anthropic:anthropic-java:${rootProject.extra["anthropicSdkVersion"]}")
    compileOnly("com.openai:openai-java:${rootProject.extra["openaiSdkVersion"]}")
    compileOnly("software.amazon.awssdk:bedrockruntime:${rootProject.extra["bedrockSdkVersion"]}")

    // Test dependencies
    testImplementation(platform("io.opentelemetry:opentelemetry-bom:${rootProject.extra["otelVersion"]}"))
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    // Need LangChain4j on test classpath for mocking
    testImplementation("dev.langchain4j:langchain4j-core:${rootProject.extra["langchain4jVersion"]}")
}
