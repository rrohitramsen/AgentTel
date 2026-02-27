plugins {
    `java-library`
}

description = "AgentTel JavaAgent Extension — zero-code agent-ready telemetry"

dependencies {
    implementation(project(":agenttel-core"))

    implementation(platform("io.opentelemetry:opentelemetry-bom:${rootProject.extra["otelVersion"]}"))
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")

    implementation("com.fasterxml.jackson.core:jackson-databind:${rootProject.extra["jacksonVersion"]}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${rootProject.extra["jacksonVersion"]}")

    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}

// Build fat jar so it's self-contained for -Dotel.javaagent.extensions
tasks.register<Jar>("extensionJar") {
    archiveClassifier.set("extension")
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("assemble") {
    dependsOn("extensionJar")
}
