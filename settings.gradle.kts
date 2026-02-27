rootProject.name = "agenttel"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    "agenttel-api",
    "agenttel-core",
    "agenttel-genai",
    "agenttel-spring-boot-starter",
    "agenttel-testing",
    "examples:spring-boot-example",
    "examples:langchain4j-example"
)
