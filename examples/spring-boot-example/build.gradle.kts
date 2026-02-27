plugins {
    java
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
}

description = "AgentTel Spring Boot Example - Demo payment service"

dependencies {
    implementation(project(":agenttel-spring-boot-starter"))

    implementation(platform("io.opentelemetry:opentelemetry-bom:${rootProject.extra["otelVersion"]}"))
    implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:${rootProject.extra["otelInstrumentationVersion"]}"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")

    testImplementation(project(":agenttel-testing"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
