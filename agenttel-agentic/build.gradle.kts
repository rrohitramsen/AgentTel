plugins {
    `java-library`
}

description = "AgentTel Agentic - Observability for AI agent lifecycle, orchestration, and reasoning"

dependencies {
    api(project(":agenttel-api"))

    api(platform("io.opentelemetry:opentelemetry-bom:${rootProject.extra["otelVersion"]}"))
    api("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry:opentelemetry-sdk")
    implementation("io.opentelemetry:opentelemetry-sdk-trace")
    implementation("org.slf4j:slf4j-api:${rootProject.extra["slf4jVersion"]}")

    testImplementation(project(":agenttel-genai"))
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}
