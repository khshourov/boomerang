plugins {
    id("org.graalvm.buildtools.native") version "0.10.3"
    id("info.solidsoft.pitest")
    id("application")
}

application {
    mainClass.set("io.boomerang.cli.BoomTool")
}

dependencies {
    implementation(project(":boomerang-proto"))
    implementation(project(":boomerang-client-java"))

    // CLI logic
    implementation("info.picocli:picocli:4.7.5")
    implementation("org.jline:jline:3.25.1")
    implementation("info.picocli:picocli-shell-jline3:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.12")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.ORACLE)
    }
}

graalvmNative {
    toolchainDetection.set(false)
    binaries {
        named("main") {
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
                vendor.set(JvmVendorSpec.ORACLE)
            })
            imageName.set("boomtool")
            mainClass.set("io.boomerang.cli.BoomTool")
            buildArgs.addAll(
                "--no-fallback",
                "-march=native",
                "--strict-image-heap",
                "-H:+ReportExceptionStackTraces",
                "--install-exit-handlers"
            )
        }
    }
}

configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    junit5PluginVersion.set("1.2.1")
    targetClasses.set(setOf("io.boomerang.*"))
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(setOf("XML", "HTML"))
    mutationThreshold.set(0)
}
