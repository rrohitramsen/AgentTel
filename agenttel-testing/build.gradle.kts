plugins {
    `java-library`
}

description = "AgentTel Testing - Test utilities for asserting agent-ready telemetry"

dependencies {
    api(project(":agenttel-api"))

    api(platform("io.opentelemetry:opentelemetry-bom:${rootProject.extra["otelVersion"]}"))
    api("io.opentelemetry:opentelemetry-sdk-testing")
    api("org.junit.jupiter:junit-jupiter-api:${rootProject.extra["junitVersion"]}")
    api("org.assertj:assertj-core:${rootProject.extra["assertjVersion"]}")

    implementation("io.opentelemetry:opentelemetry-sdk")
    implementation("io.opentelemetry:opentelemetry-sdk-trace")
    implementation("io.opentelemetry:opentelemetry-sdk-logs")
}
