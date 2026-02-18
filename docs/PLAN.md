# Boomerang Implementation Plan

This plan tracks the development of Boomerang, a high-performance, distributed, and persistent scheduler.

## Phase 1: Foundation & Contract Definition
- [x] **Define Protobuf Schema:** Create `proto/boomerang.proto` covering task registration, cancellation, and callback configurations.
- [x] **Project Scaffolding:** Set up a "Vanilla Java" project (Maven/Gradle) with Netty, RocksDB, and etcd client dependencies.
- [ ] **Auth Strategy:** Implement initial API Key/JWT validation logic.

## Phase 2: Core Scheduling Engine
- [ ] **Hierarchical Timing Wheel (HTW):** Implement the in-memory O(1) scheduling logic.
- [ ] **Tiered Scheduling Logic:** Implement the "Imminent vs. Long-term" task transition logic (the 30-minute window).
- [ ] **Basic Task Lifecycle:** CRUD operations for tasks in the memory wheel.

## Phase 3: Persistence & Reliability
- [ ] **RocksDB Integration:** Implement the time-sorted index for long-term tasks.
- [ ] **Write-Ahead Log (WAL):** Ensure every task registration is durable before ACK.
- [ ] **Recovery Logic:** Implement system startup logic to reload tasks from RocksDB into the HTW.
- [ ] **Retry Engine & DLQ:** Implement the retry loop logic and persistent Dead Letter Queue.

## Phase 4: Networking & Callback Engine
- [ ] **Inbound TCP Server:** Implement the Netty-based Protobuf/TCP server for task registration.
- [ ] **Outbound Callback Dispatcher:**
    - [ ] Raw TCP/Protobuf callback.
    - [ ] gRPC callback.
    - [ ] HTTP/Webhook callback.
    - [ ] UDP callback.
- [ ] **Connection Pooling:** Manage efficient reuse of outbound connections.

## Phase 5: Distributed Coordination (etcd)
- [ ] **Shard Management:** Implement etcd-based shard ownership and leader election.
- [ ] **Partitioning Logic:** Implement client-side or gateway-level hashing to route tasks to the correct shard.
- [ ] **Cluster State Sync:** Use etcd to track node health and dynamic shard reassignment.

## Phase 6: Management Tools
- [ ] **CLI Tool (`boomtool`):** Build the picocli application for task management.
- [ ] **GraalVM Native Image:** Configure the build to produce a standalone CLI binary.
- [ ] **Web Panel (Frontend):** Develop the React + TypeScript SPA.
- [ ] **Management API:** Add specialized endpoints to the server to feed the dashboard.

## Phase 7: Scale & Performance Validation
- [ ] **Load Testing:** Verify 1 million concurrent tasks and jitter (1-10ms).
- [ ] **Fault Tolerance Testing:** Simulate node failures and verify etcd/RocksDB recovery.
- [ ] **Metrics & Telemetry:** Finalize Prometheus/Grafana integration.
