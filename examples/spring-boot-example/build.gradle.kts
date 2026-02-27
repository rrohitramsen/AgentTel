plugins {
    java
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
}

description = "AgentTel Spring Boot Example - Demo payment service"

// Override Spring Boot's managed OTel versions — Spring Boot 3.4.x ships with OTel 1.43.0
// but we need 1.59.0+ for the instrumentation starter 2.25.0
dependencyManagement {
    imports {
        mavenBom("io.opentelemetry:opentelemetry-bom:${rootProject.extra["otelVersion"]}")
        mavenBom("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:${rootProject.extra["otelInstrumentationVersion"]}")
    }
}

dependencies {
    implementation(project(":agenttel-spring-boot-starter"))
    implementation(project(":agenttel-agent"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    testImplementation(project(":agenttel-testing"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}
