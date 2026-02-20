plugins {
    id("com.diffplug.spotless") version "6.25.0"
    id("java")
    id("jacoco")
    id("info.solidsoft.pitest") version "1.19.0-rc.3" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "jacoco")

    group = "io.boomerang"
    version = "0.1.0-SNAPSHOT"

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    spotless {
        java {
            googleJavaFormat()
            target("src/**/*.java")
        }
    }

    jacoco {
        toolVersion = "0.8.11"
    }

    tasks.jacocoTestReport {
        dependsOn(tasks.test)
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.jacocoTestCoverageVerification {
        violationRules {
            rule {
                limit {
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
