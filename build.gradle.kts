plugins {
    id("com.diffplug.spotless") version "6.25.0"
    id("java")
    id("jacoco")
    id("info.solidsoft.pitest") version "1.15.0" apply false
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

    apply(plugin = "info.solidsoft.pitest")

    configure<info.solidsoft.pitest.gradle.PitestTaskConfiguration> {
        targetClasses.set(listOf("io.boomerang.*"))
        pitestVersion.set("1.15.0")
        threads.set(4)
        outputFormats.set(listOf("XML", "HTML"))
        timestampedReports.set(false)
        junit5PluginVersion.set("1.2.1")
    }

    tasks.jacocoTestReport {
        dependsOn(tasks.test)
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
