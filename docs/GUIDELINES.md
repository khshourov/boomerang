# Boomerang Project Guidelines

These guidelines are the definitive reference for development within the Boomerang project. All contributions must adhere to these standards.

## 1. Technical Stack & Standards
- **Java:** Version 21 (LTS).
- **Build Tool:** **Gradle** (Kotlin DSL).
- **TypeScript:** Strict type-checking enabled for the React SPA.
- **Code Formatting:**
    - **Java:** Enforced via **Spotless** and **Checkstyle**.
    - **TypeScript:** Standard ESLint + Prettier configuration.
- **Documentation:**
    - **Architecture:** Major technical decisions must be documented in `docs/ADRs/`.
    - **API:** Standard JavaDoc for internal and Protobuf-generated code.

## 2. Quality Assurance & Testing
- **Code Coverage:** Minimum **90% coverage** required for all new features.
- **Mutation Testing:** Use **PITest** (or similar) to ensure test quality beyond simple coverage metrics.
- **Performance:** While strict SLAs are not enforced initially, performance is a core value and should guide architectural choices.

## 3. Development Workflow
- **Branching Model:** **Trunk-Based Development**. All changes should be merged into `main` as frequently as possible.
- **Commit Messages:** Follow **[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)**.
    - *Format:* `<type>(<scope>): <description>`
    - *Example:* `feat(core): add hierarchical timing wheel logic`
- **Pull Requests:** Small, atomic, and verified by tests.

## 4. Architecture & Design
- **Core Principles:** Focus on high-concurrency, low-latency, and durability.
- **Surgical Updates:** Changes should be targeted and idiomatic, avoiding unrelated refactoring.
