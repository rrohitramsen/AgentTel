plugins {
    `java-library`
}

description = "AgentTel Spring Boot Starter - Auto-configuration for agent-ready telemetry"

val springBootVersion = rootProject.extra["springBootVersion"]

dependencies {
    api(project(":agenttel-core"))

    api(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))
    api(platform("io.opentelemetry:opentelemetry-bom:${rootProject.extra["otelVersion"]}"))
    api(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:${rootProject.extra["otelInstrumentationVersion"]}"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework:spring-web")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")

    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:${springBootVersion}")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${springBootVersion}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}
