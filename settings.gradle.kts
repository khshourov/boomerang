plugins {
    // Enables automatic Java toolchain provisioning to ensure the specific Oracle/GraalVM
    // distributions required for native compilation are available across all environments.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "boomerang"
include("boomerang-proto")
include("boomerang-core")
include("boomerang-cli")
include("boomerang-client-java")
