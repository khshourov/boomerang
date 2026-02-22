plugins {
    id("org.graalvm.buildtools.native") version "0.10.1"
    id("info.solidsoft.pitest")
}

dependencies {
    implementation(project(":boomerang-proto"))

    // CLI logic
    implementation("info.picocli:picocli:4.7.5")
    implementation("org.jline:jline:3.25.1")
    implementation("info.picocli:picocli-shell-jline3:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")

    // Networking (Netty)
    implementation("io.netty:netty-all:4.1.110.Final")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.12")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("boomtool")
            mainClass.set("io.boomerang.cli.BoomTool")
            buildArgs.add("--no-fallback")
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
