# Building Boomerang CLI Native Binary

Boomerang CLI (`boomtool`) can be compiled into a standalone native binary using GraalVM Native Image. This provides instant startup and zero-dependency execution.

## Prerequisites

1.  **GraalVM 21:** You must have GraalVM for JDK 21 installed.
2.  **Native Image Tool:** Ensure the `native-image` component is installed.
    -   On most recent GraalVM distributions, it is included by default.
    -   You can verify it by running: `native-image --version`
3.  **C Toolchain:**
    -   **macOS:** Xcode Command Line Tools (`xcode-select --install`).
    -   **Linux:** `gcc`, `zlib-devel`, etc. (Check GraalVM docs for your distro).

## Configuration

The project is configured to use Gradle's Java Toolchain. If your GraalVM is installed in a standard location (like `/Library/Java/JavaVirtualMachines/`), Gradle should detect it automatically.

If you need to point Gradle to a specific installation, you can set the `JAVA_HOME` or `GRAALVM_HOME` environment variables, or configure it in `gradle.properties`.

## Build Command

To generate the native binary, run the following command from the project root:

```bash
./gradlew :boomerang-cli:nativeCompile
```

## Binary Location

Once the build completes successfully, the standalone executable will be available at:

```text
boomerang-cli/build/native/nativeCompile/boomtool
```

## Usage

You can run the binary directly:

```bash
./boomerang-cli/build/native/nativeCompile/boomtool --help
```

### Example: List Tasks
```bash
./boomerang-cli/build/native/nativeCompile/boomtool -u admin -p admin123 task list
```

## Troubleshooting

-   **Reflection Issues:** The project uses `picocli-codegen` to generate reflection metadata automatically during compilation. If you add new commands or options, ensure they are compatible with AOT (Ahead-Of-Time) compilation.
-   **Static Linking:** On Linux, if you require a fully static binary, you may need to add `--static --libc=musl` to the `buildArgs` in `boomerang-cli/build.gradle.kts`.
