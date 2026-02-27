plugins {
    `java-library`
}

description = "AgentTel API - Annotations, enums, and attribute constants for agent-ready telemetry"

dependencies {
    api(platform("io.opentelemetry:opentelemetry-bom:${rootProject.extra["otelVersion"]}"))
    api("io.opentelemetry:opentelemetry-api")
}
