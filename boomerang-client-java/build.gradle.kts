plugins {
    id("java")
    id("info.solidsoft.pitest")
}

dependencies {
    implementation(project(":boomerang-proto"))
    implementation("org.slf4j:slf4j-api:2.0.12")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testImplementation("ch.qos.logback:logback-classic:1.5.3")
    testImplementation("io.grpc:grpc-netty-shaded:1.62.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    junit5PluginVersion.set("1.2.1")
    targetClasses.set(setOf("io.boomerang.client.*"))
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(setOf("XML", "HTML"))
    mutationThreshold.set(80)
}
