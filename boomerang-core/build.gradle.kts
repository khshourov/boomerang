dependencies {
    implementation(project(":boomerang-proto"))

    // Networking
    implementation("io.netty:netty-all:4.1.110.Final")

    // Persistence
    implementation("org.rocksdb:rocksdbjni:9.1.1")

    // Distributed Coordination
    implementation("io.etcd:jetcd-core:0.7.7")

    // Utilities
    implementation("org.slf4j:slf4j-api:2.0.12")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testImplementation("org.assertj:assertj-core:3.25.3")
}
