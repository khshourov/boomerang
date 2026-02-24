plugins {
    id("java")
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("jvm-test-suite")
}

dependencies {
    implementation(project(":boomerang-client-java"))
    implementation(project(":boomerang-proto"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.google.protobuf:protobuf-java-util:3.25.3")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

    // Configuration processor for custom properties
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Unit test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }

        val intTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()

            dependencies {
                implementation(project())
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("org.springframework.boot:spring-boot-webmvc-test")
                implementation("org.springframework.security:spring-security-test")
                implementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
                implementation("org.testcontainers:testcontainers:2.0.2")
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                        // Ensure core is built before running tests that depend on its Docker image
                        dependsOn(project(":boomerang-core").tasks.jar)
                    }
                }
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
