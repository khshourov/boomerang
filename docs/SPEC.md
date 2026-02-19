# Boomerang Specification
**Status:** Architecture Definition

## Core Value Proposition
A high-performance, distributed, and persistent scheduler that accepts binary payloads and fires them back to clients after a set interval. It acts as a massive-scale, durable `setInterval`/`setTimeout` service.

## Technical Decisions
- **Scale:** Support for ~1 million concurrent active tasks.
- **Architecture:** Partitioned Single Leader (Sharding).
    - *Rationale:* Ensures strict timer ownership per node via etcd leases.
- **Core Algorithm:** Hierarchical Timing Wheel (HTW) for O(1) execution.
- **Tech Stack:** 
    - **Server:** Vanilla Java (Initial implementation) with Netty; future port to Rust.
    - **Coordination:** **etcd** for leader election and shard membership.
    - **Storage:** **RocksDB** for persistent task indexing and WAL.
    - **Communication:** Protobuf over raw TCP.
- **Client Strategy:** Multi-language thin SDKs to abstract TCP/Protobuf details.

## Persistence & Memory Strategy
- **Tiered Scheduling:**
    - **Imminent Tasks (e.g., <30m):** Kept in the in-memory Hierarchical Timing Wheel (HTW) for O(1) execution.
    - **Long-term Tasks (e.g., >30m):** Persisted to disk (WAL/Indexed Storage) and loaded into the HTW as their execution window approaches.
- **Durability:**
    - **WAL (Write-Ahead Log):** Every registration/cancellation is logged to disk before acknowledgment.
    - **Indexing:** Use a time-sorted index (e.g., RocksDB or a simple B-Tree) for long-term tasks to allow efficient "look-ahead" loading.
- **Recovery:** On restart, the service rebuilds the HTW by scanning the WAL/Index for all tasks due within the next 30-minute window.

## Communication & Callback Strategy
- **Inbound Protocol:** Protobuf over TCP for task registration and management.
- **Callback Mechanism:** Server-initiated "Dial-Back." The server establishes a connection to the client's registered endpoint when the timer fires.
- **Pluggable Callback Engine:** The server acts as a multi-protocol dispatcher. Each task defines its preferred callback protocol:
    - **Raw TCP (Protobuf):** For high-performance, low-overhead binary delivery.
    - **gRPC:** For structured, type-safe RPC calls.
    - **HTTP/Webhooks:** For integration with standard web services and serverless functions.
    - **UDP:** For fire-and-forget, ultra-low-latency binary payloads.
- **Connection Management:** Efficient pooling of outbound connections to handle high-frequency callbacks to diverse endpoints.

## Reliability & Error Handling
- **At-Least-Once Delivery:** The server ensures the callback is acknowledged. Clients are expected to use `TaskID` for idempotency.
- **Configurable Retries:** Each task/client can specify a retry policy (e.g., exponential backoff, max attempts).
- **Dead Letter Queue (DLQ):** After all retries are exhausted, the task and its payload are moved to a persistent DLQ for manual inspection or replay.

## Management & Operations
- **Web Panel (Dashboard):**
    - **Tech Stack:** Modern SPA (React + TypeScript).
    - **Functionality:** Centralized UI to manage `ClientConfig`, monitor `Ongoing Tasks`, and inspect/replay `DLQ` items. Visualize shard health, HTW state, and etcd cluster metadata.
- **CLI Tool (`boomtool`):**
    - **Tech Stack:** Java + **picocli** (Targeting GraalVM native-image for a standalone binary).
    - **Commands:** `set`, `update`, `delete`, `show/list`.
    - **Connection:** Direct Protobuf/TCP communication with server nodes.

## Key Workflows
1. **Auth Handshake:** Client sends `[client_id, password, CallbackConfig, RetryPolicy, DLQPolicy]`.
   - Server validates credentials and returns `session_id` and `expires_at_ms`.
2. **Register Task:** Client sends `[Payload, Interval, RepeatFlag]` along with `session_id` in the envelope.
   - `CallbackConfig` and other policies are inherited from the session.
3. **Scheduled Execution:** Service triggers the `CallbackEngine`. If failure occurs, initiate the `RetryLoop`.
4. **Cancel Task:** Client stops a recurring interval using `TaskID`.
5. **Session Refresh:** Client refreshes `session_id` before it expires.
6. **Audit & Monitor:** Admins use the Web Panel to track system performance and manage failed tasks.
