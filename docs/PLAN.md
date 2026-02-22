# Boomerang Implementation Plan

This plan tracks the development of Boomerang, a high-performance, persistent scheduler.

## Phase 1: Foundation & Contract Definition
- [x] **Define Protobuf Schema:** Create `proto/boomerang.proto` covering task registration, cancellation, and callback configurations.
- [x] **Project Scaffolding:** Set up a "Vanilla Java" project (Maven/Gradle) with Netty and RocksDB client dependencies.
- [x] **Auth Strategy:** Implement initial client-id/password mechanism and session management.

## Phase 2: Core Scheduling Engine
- [x] **Hierarchical Timing Wheel (HTW):** Implement the in-memory O(1) scheduling logic.
- [x] **Tiered Scheduling Logic:** Implement the "Imminent vs. Long-term" task transition logic (the 30-minute window).
- [x] **Basic Task Lifecycle:** CRUD operations for tasks in the memory wheel.

## Phase 3: Persistence & Reliability
- [x] **RocksDB Integration:** Implement the time-sorted index for long-term tasks.
- [x] **Client Credential Storage:** Implement encrypted persistent storage for admin and client credentials.
- [x] **Persistent Client Policies:** Store callback and retry configurations at the client level during registration.
- [x] **Recovery Logic:** Implement system startup logic to reload tasks from RocksDB into the HTW.
- [x] **Retry Engine & DLQ:** Implement the retry loop logic and persistent Dead Letter Queue.

## Phase 4: Networking & Callback Engine
- [x] **Inbound TCP Server:** Implement the Netty-based Protobuf/TCP server for task registration.
- [x] **Outbound Callback Dispatcher:**
    - [x] Raw TCP/Protobuf callback.
    - [x] gRPC callback.
    - [x] HTTP/Webhook callback.
    - [x] UDP callback.
- [x] **Connection Pooling:** Manage efficient reuse of outbound connections.

## Phase 5: Management Tools
- [x] **CLI Tool (`boomtool`):** Build the picocli application for task management.
- [ ] **Management API & Task Listing:**
    - [ ] Implement `ListTasks` and `GetTask` in Protobuf and Server.
    - [ ] Support scope-based visibility (Admin: all, Client: own).
    - [ ] Add pagination support for task listing.
- [ ] **E2E Testing (CLI & REPL):**
    - [ ] Implement full integration tests for `boomtool` against a live `BoomerangServer`.
    - [ ] Test one-shot/batch mode commands.
    - [ ] Test interactive REPL flow using terminal simulation.
- [ ] **GraalVM Native Image:** Configure the build to produce a standalone CLI binary.
- [ ] **Web Panel (Frontend):** Develop the React + TypeScript SPA.
- [ ] **Management API:** Add specialized endpoints to the server to feed the dashboard.

## Phase 6: Performance & Validation
- [ ] **Load Testing:** Verify concurrent task processing and timing accuracy.
- [ ] **Fault Tolerance Testing:** Simulate server restarts and verify RocksDB recovery.
- [ ] **Metrics & Telemetry:** Finalize Prometheus/Grafana integration.
- [x] **Mutation Testing:** Configure PITest for Gradle 9/Kotlin DSL compatibility to ensure high test quality.
