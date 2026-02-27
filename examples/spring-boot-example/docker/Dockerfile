# Multi-stage build for AgentTel payment-service example
# Build context must be the repository root (the example depends on all library modules)

# --- Stage 1: Build ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && \
    ./gradlew :examples:spring-boot-example:bootJar -x test --no-daemon

# --- Stage 2: Runtime ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/examples/spring-boot-example/build/libs/*.jar app.jar
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
