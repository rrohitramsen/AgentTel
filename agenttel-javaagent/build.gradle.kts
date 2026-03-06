plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

description = "AgentTel JavaAgent — zero-code agent-ready telemetry (bundles OTel javaagent)"

// Configuration for the upstream OTel javaagent
val otel: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    implementation(project(":agenttel-core"))

    implementation(platform("io.opentelemetry:opentelemetry-bom:${rootProject.extra["otelVersion"]}"))
    implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")

    implementation("com.fasterxml.jackson.core:jackson-databind:${rootProject.extra["jacksonVersion"]}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${rootProject.extra["jacksonVersion"]}")

    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")

    // Upstream OTel javaagent (downloaded, not on compile classpath)
    otel("io.opentelemetry.javaagent:opentelemetry-javaagent:${rootProject.extra["otelInstrumentationVersion"]}")
}

// Step 1: Shadow creates a self-contained extension JAR with all AgentTel dependencies
tasks.shadowJar {
    archiveClassifier.set("extension")
    mergeServiceFiles()
}

// Step 2: Bundle the extension inside the upstream OTel javaagent
tasks.register<Jar>("agentJar") {
    dependsOn(tasks.shadowJar, otel)
    group = "build"
    description = "Builds the AgentTel javaagent — OTel agent + AgentTel extension in a single JAR"

    // Use a fixed filename to avoid conflict with the default jar task
    archiveFileName.set("agenttel-javaagent.jar")

    // Extract the upstream OTel javaagent
    from(zipTree(otel.singleFile))

    // Add AgentTel extension into the extensions/ directory
    from(tasks.shadowJar.get().archiveFile) {
        into("extensions")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Preserve the upstream agent's manifest (includes Premain-Class)
    doFirst {
        manifest.from(
            zipTree(otel.singleFile).matching {
                include("META-INF/MANIFEST.MF")
            }.singleFile
        )
    }
}

tasks.named("assemble") {
    dependsOn("agentJar")
}
