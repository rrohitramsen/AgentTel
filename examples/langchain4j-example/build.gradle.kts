plugins {
    java
    application
}

description = "AgentTel LangChain4j Example - GenAI tracing with cost tracking"

application {
    mainClass.set("io.agenttel.example.langchain4j.GenAiTracingExample")
}

dependencies {
    implementation(project(":agenttel-core"))
    implementation(project(":agenttel-genai"))

    implementation(platform("io.opentelemetry:opentelemetry-bom:${rootProject.extra["otelVersion"]}"))
    implementation("io.opentelemetry:opentelemetry-sdk")
    implementation("io.opentelemetry:opentelemetry-sdk-trace")
    implementation("io.opentelemetry:opentelemetry-exporter-logging")

    implementation("dev.langchain4j:langchain4j-core:${rootProject.extra["langchain4jVersion"]}")

    // SLF4J simple for console logging
    implementation("org.slf4j:slf4j-simple:${rootProject.extra["slf4jVersion"]}")
}
