plugins {
    `java-library`
}

description = "AgentTel Agent - AI agent interface layer with MCP server, health aggregation, and incident context"

dependencies {
    api(project(":agenttel-core"))

    api(platform("io.opentelemetry:opentelemetry-bom:${rootProject.extra["otelVersion"]}"))
    implementation("io.opentelemetry:opentelemetry-sdk")
    implementation("io.opentelemetry:opentelemetry-sdk-trace")
    implementation("com.fasterxml.jackson.core:jackson-databind:${rootProject.extra["jacksonVersion"]}")
    implementation("org.slf4j:slf4j-api:${rootProject.extra["slf4jVersion"]}")

    // MCP server - uses simple HTTP/SSE via built-in JDK HttpServer (no extra deps)

    testImplementation(platform("io.opentelemetry:opentelemetry-bom:${rootProject.extra["otelVersion"]}"))
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}
