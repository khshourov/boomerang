# Boomerang Java Client SDK

A lightweight, synchronous Java library for interacting with the Boomerang scheduler and receiving task callbacks.

## Features

- **Simple API:** Synchronous methods for task registration, cancellation, and retrieval.
- **Modular Callback Receivers:** Support for TCP, UDP, HTTP, and gRPC callbacks.
- **Smart Client:** Automatic session management and re-login on expiration.
- **Minimal Dependencies:** Built on standard JDK features and Protobuf.

## Installation

Add the following to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":boomerang-client-java"))
}
```

## Quick Start

### 1. Initialize the Client

Use the `SmartBoomerangClient` for a managed session that handles authentication automatically.

```java
import io.boomerang.client.BoomerangClient;
import io.boomerang.client.DefaultBoomerangClient;
import io.boomerang.client.SmartBoomerangClient;

BoomerangClient baseClient = new DefaultBoomerangClient("localhost", 1234);
BoomerangClient client = new SmartBoomerangClient(baseClient, "my-client-id", "my-password");

client.connect();
```

### 2. Register a Task

```java
import io.boomerang.proto.Task;
import io.boomerang.proto.RegistrationResponse;
import com.google.protobuf.ByteString;

Task task = Task.newBuilder()
    .setPayload(ByteString.copyFromUtf8("Hello Boomerang!"))
    .setDelayMs(5000) // Execute in 5 seconds
    .setRepeatIntervalMs(0) // 0 for one-shot task
    .build();

RegistrationResponse response = client.register(task);
System.out.println("Scheduled Task ID: " + response.getTaskId());
```

### 3. Setup a Callback Receiver

Implement the `CallbackHandler` to process your tasks when they fire.

```java
import io.boomerang.client.CallbackHandler;
import io.boomerang.client.CallbackReceiver;
import io.boomerang.client.CallbackReceiverBuilder;
import io.boomerang.proto.CallbackResponse;
import io.boomerang.proto.Status;

CallbackHandler myHandler = request -> {
    System.out.println("Received task: " + request.getTaskId());
    System.out.println("Payload: " + request.getPayload().toStringUtf8());
    return CallbackResponse.newBuilder().setStatus(Status.OK).build();
};

// Start a TCP receiver on port 8080
CallbackReceiver receiver = new CallbackReceiverBuilder()
    .withPort(8080)
    .withHandler(myHandler)
    .buildTcp();

receiver.start();
```

## Protocol Support

The SDK provides builders for multiple callback protocols:

| Protocol | Builder Method | Requirements |
|----------|----------------|--------------|
| **TCP**  | `buildTcp()`   | Raw TCP (Default) |
| **UDP**  | `buildUdp()`   | Fire-and-forget |
| **HTTP** | `buildHttp()`  | Uses JDK `HttpServer` |
| **gRPC** | `buildGrpc()`  | Requires gRPC dependencies |

Example for HTTP:
```java
CallbackReceiver httpReceiver = new CallbackReceiverBuilder()
    .withPort(8080)
    .withHttpPath("/my-callback")
    .withHandler(myHandler)
    .buildHttp();
```

## Error Handling

All client operations throw `BoomerangException` for communication or protocol errors.

```java
try {
    client.register(task);
} catch (BoomerangException e) {
    System.err.println("Failed to schedule task: " + e.getMessage());
}
```
