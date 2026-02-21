plugins {
    id("info.solidsoft.pitest")
}

dependencies {
    implementation(project(":boomerang-proto"))

    // Networking
    implementation("io.netty:netty-all:4.1.110.Final")
    implementation("io.grpc:grpc-netty-shaded:1.62.2")

    // Persistence
    implementation("org.rocksdb:rocksdbjni:9.1.1")

    // Distributed Coordination
    implementation("io.etcd:jetcd-core:0.7.7")

    // Utilities
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.awaitility:awaitility:4.2.0")
    testImplementation("io.grpc:grpc-testing:1.62.2")
    testImplementation("io.grpc:grpc-inprocess:1.62.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    junit5PluginVersion.set("1.2.1")
    targetClasses.set(setOf("io.boomerang.*"))
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(setOf("XML", "HTML"))
    mutationThreshold.set(80)
}
