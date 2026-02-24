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
- [x] **Management API & Task Listing:**
    - [x] Implement `ListTasksRequest`, `ListTasksResponse`, `GetTaskRequest`, and `GetTaskResponse` in Protobuf.
    - [x] Support advanced filtering in `ListTasks`:
        - [x] Interval-based: `scheduled_after`, `scheduled_before`, and `range(start, end)`.
        - [x] Task Type: Filter by one-shot or recurring (repeat) tasks.
        - [x] Administrative: `client_id` filter (Admin can see all/specific clients, Client sees only own).
    - [x] Add pagination support (cursor-based using `next_token`).
    - [x] Implement `GetTask` by ID with optional `client_id` for Admin.
- [x] **CLI Integration for Task Listing:**
    - [x] Implement `task list` command in `boomtool` with support for filtering by client, time range, and recurrence.
    - [x] Implement `task get <id>` command in `boomtool` for detailed task inspection.
    - [x] Update `BoomerangClient` to support the new listing and retrieval requests.
    - [x] Handle pagination and `next_token` in the CLI for smooth listing of large task sets.
- [ ] **E2E Testing (CLI & REPL):**
    - [ ] Implement full integration tests for `boomtool` against a live `BoomerangServer`.
    - [ ] Test one-shot/batch mode commands.
    - [ ] Test interactive REPL flow using terminal simulation.
- [x] **GraalVM Native Image:** Configure the build to produce a standalone CLI binary.
- [x] **Java Client SDK (`boomerang-client-java`):**
    - [x] Scaffold `boomerang-client-java` module as a separate library.
    - [x] Implement a clean, synchronous Java API for task registration and management.
    - [x] Implement an integrated Callback Receiver supporting TCP, UDP, gRPC, and HTTP.
    - [x] Support auth session lifecycle and automatic reconnection.
- [x] **Migrate CLI to SDK:**
    - [x] Refactor `boomerang-cli` to use the `boomerang-client` library.
    - [x] Remove duplicate communication logic from the CLI module.

## Phase 6: Management Web Panel
- [x] **Backend (Spring Boot):**
    - [x] Scaffold `boomerang-web-backend` as a standalone module.
    - [x] Integrate `boomerang-client-java` for all core communication.
    - [x] Implement REST API wrappers for all Boomerang commands (Register, Cancel, List, Get).
    - [x] Implement backend-side auth/session refresh using core's mechanism.
    - [x] Configure standard Spring Security (CORS, CSRF, Secure Auth).
    - [ ] Implement real-time monitoring endpoints (WebSockets/SSE) for task activity.
- [x] **Frontend (React + TS):**
    - [x] Scaffold `boomerang-web-ui` as a separate React + TypeScript module.
    - [ ] Implement Dashboard with real-time task status and performance monitoring.
    - [x] Implement Task Management views (List, Details, and Registration forms).
    - [x] Integrate with Backend REST APIs for management and real-time updates.
    - [x] Ensure separate build/deployment pipeline for the frontend SPA.

## Phase 7: Performance & Validation
- [ ] **Load Testing:** Verify concurrent task processing and timing accuracy.
- [ ] **Fault Tolerance Testing:** Simulate server restarts and verify RocksDB recovery.
- [ ] **Metrics & Telemetry:** Finalize Prometheus/Grafana integration.
- [x] **Mutation Testing:** Configure PITest for Gradle 9/Kotlin DSL compatibility to ensure high test quality.
