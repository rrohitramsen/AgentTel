plugins {
    java
    `java-library`
    `maven-publish`
    signing
}

// Centralized version properties
extra["otelVersion"] = "1.59.0"
extra["otelSemconvVersion"] = "1.40.0"
extra["otelInstrumentationVersion"] = "2.25.0"
extra["springBootVersion"] = "3.4.3"
extra["jacksonVersion"] = "2.17.2"
extra["junitVersion"] = "5.11.4"
extra["assertjVersion"] = "3.27.3"
extra["mockitoVersion"] = "5.14.2"
extra["slf4jVersion"] = "2.0.16"

// GenAI dependencies (Phase 2)
extra["otelSemconvIncubatingVersion"] = "1.40.0-alpha"
extra["springAiVersion"] = "1.0.0-M6"
extra["langchain4jVersion"] = "1.0.0-beta1"
extra["anthropicSdkVersion"] = "2.0.0"
extra["openaiSdkVersion"] = "4.0.0"
extra["bedrockSdkVersion"] = "2.30.0"

allprojects {
    group = "io.agenttel"
    version = "0.1.0-alpha"

    repositories {
        mavenCentral()
    }
}

// Published library modules (not examples)
val publishedModules = setOf("agenttel-api", "agenttel-core", "agenttel-genai",
    "agenttel-agent", "agenttel-javaagent-extension", "agenttel-spring-boot-starter", "agenttel-testing")

subprojects {
    apply(plugin = "java-library")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    dependencies {
        testImplementation(platform("org.junit:junit-bom:${rootProject.extra["junitVersion"]}"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.assertj:assertj-core:${rootProject.extra["assertjVersion"]}")
        testImplementation("org.mockito:mockito-core:${rootProject.extra["mockitoVersion"]}")
        testImplementation("org.mockito:mockito-junit-jupiter:${rootProject.extra["mockitoVersion"]}")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    // Javadoc configuration for all subprojects
    tasks.withType<Javadoc> {
        options {
            this as StandardJavadocDocletOptions
            addStringOption("Xdoclint:none", "-quiet")
            encoding = "UTF-8"
            charSet = "UTF-8"
        }
        isFailOnError = false
    }

    // Publishing configuration for library modules only
    if (name in publishedModules) {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        java {
            withSourcesJar()
            withJavadocJar()
        }

        publishing {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])

                    pom {
                        name.set(project.name)
                        description.set(project.description ?: "AgentTel - Agent-ready telemetry for Java")
                        url.set("https://github.com/rrohitramsen/AgentTel")

                        licenses {
                            license {
                                name.set("Apache License 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            }
                        }

                        developers {
                            developer {
                                id.set("rrohitramsen")
                                name.set("Rohit")
                            }
                        }

                        scm {
                            connection.set("scm:git:git://github.com/rrohitramsen/AgentTel.git")
                            developerConnection.set("scm:git:ssh://github.com/rrohitramsen/AgentTel.git")
                            url.set("https://github.com/rrohitramsen/AgentTel")
                        }
                    }
                }
            }

            repositories {
                maven {
                    name = "OSSRH"
                    val releasesUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                    val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                    url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl

                    credentials {
                        username = findProperty("mavenCentralUsername") as String? ?: System.getenv("ORG_GRADLE_PROJECT_mavenCentralUsername")
                        password = findProperty("mavenCentralPassword") as String? ?: System.getenv("ORG_GRADLE_PROJECT_mavenCentralPassword")
                    }
                }
            }
        }

        signing {
            val signingKey = findProperty("signingInMemoryKey") as String? ?: System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey")
            val signingPassword = findProperty("signingInMemoryKeyPassword") as String? ?: System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKeyPassword")
            if (signingKey != null && signingPassword != null) {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(publishing.publications["mavenJava"])
            }
        }
    }
}
