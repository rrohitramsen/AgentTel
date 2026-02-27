plugins {
    `java-library`
}

description = "AgentTel Core - Runtime engine for agent-ready telemetry enrichment"

dependencies {
    api(project(":agenttel-api"))

    api(platform("io.opentelemetry:opentelemetry-bom:${rootProject.extra["otelVersion"]}"))
    implementation("io.opentelemetry:opentelemetry-sdk")
    implementation("io.opentelemetry:opentelemetry-sdk-trace")
    implementation("io.opentelemetry:opentelemetry-sdk-logs")
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
    implementation("com.fasterxml.jackson.core:jackson-databind:${rootProject.extra["jacksonVersion"]}")
    implementation("org.slf4j:slf4j-api:${rootProject.extra["slf4jVersion"]}")

    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}
