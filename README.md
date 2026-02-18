# Boomerang

Boomerang is a high-performance, distributed, and persistent scheduler designed for massive-scale task management. It acts as a durable, distributed `setInterval`/`setTimeout` service that accepts binary payloads and dispatches them back to clients after a specified interval.

## 🚀 Key Features

- **High Scale & Performance:** Engineered to support ~1 million concurrent active tasks using a **Hierarchical Timing Wheel (HTW)** for O(1) execution logic.
- **Durable Persistence:** Every task registration is backed by a **Write-Ahead Log (WAL)** and indexed in **RocksDB**, ensuring durability before acknowledgment.
- **Tiered Scheduling:** Optimizes resources by keeping imminent tasks (<30m) in memory while persisting long-term tasks to disk.
- **Pluggable Callback Engine:** Supports multiple protocols for task dispatch, including **Raw TCP (Protobuf)**, **gRPC**, **HTTP/Webhooks**, and **UDP**.
- **Reliability & Fault Tolerance:** Features at-least-once delivery, configurable exponential backoff retries, and a persistent **Dead Letter Queue (DLQ)**.
- **Distributed Architecture:** Uses **etcd** for shard ownership and leader election, allowing for seamless horizontal scaling.

## 🛠 Technical Stack

- **Server:** Java 21 (LTS), Netty, RocksDB, etcd.
- **API/Communication:** Protobuf over raw TCP.
- **CLI Tool (`boomtool`):** Java, picocli, and GraalVM Native Image.
- **Web Panel:** React, TypeScript.
- **Quality Assurance:** Spotless (Java), ESLint/Prettier (TS), PITest (Mutation Testing).

## 🗺 Implementation Roadmap

1.  **Phase 1: Foundation** - Define Protobuf contracts and scaffold the project.
2.  **Phase 2: Core Engine** - Implement the Hierarchical Timing Wheel and tiered scheduling.
3.  **Phase 3: Persistence** - Integrate RocksDB, WAL, and recovery logic.
4.  **Phase 4: Networking** - Build the Netty-based server and multi-protocol callback dispatchers.
5.  **Phase 5: Distribution** - Implement etcd-based sharding and cluster coordination.
6.  **Phase 6: Management** - Develop the `boomtool` CLI and React-based Web Panel.
7.  **Phase 7: Validation** - Extensive load testing (1M+ tasks) and fault tolerance verification.

## 📈 Development Guidelines

Contributions are welcome! Please adhere to the following:
- **Branching:** Trunk-Based Development.
- **Commits:** Follow [Conventional Commits](https://www.conventionalcommits.org/).
- **Testing:** Minimum 90% code coverage is required for all new features.
- **Documentation:** Refer to the `docs/` directory for detailed [guidelines](docs/GUIDELINES.md), [specifications](docs/SPEC.md), and the full [implementation plan](docs/PLAN.md).

---
*Boomerang: Reliable task scheduling that always comes back.*
