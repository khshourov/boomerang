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

## 2. Javadoc Standards
- **Completeness:** All public and protected classes, interfaces, methods, and constants must have Javadoc.
- **Summary Line:** The first sentence must be a concise, standalone summary of the element's purpose.
- **Tag Usage:**
    - `@param`: Required for every parameter. Describe constraints (e.g., "must be non-null").
    - `@return`: Required for all non-void methods. Describe the returned value.
    - `@throws`: Document all checked and common runtime exceptions.
- **Formatting:**
    - Use `{@code ...}` for literal values, variable names, and short code snippets.
    - Use `<p>` for paragraph breaks and `<ul>`/`<li>` for lists.
    - Avoid repeating the method name or stating the obvious (e.g., "Gets the ID").
- **API Evolution:** Use `@since` for new public APIs and `@deprecated` with a `@see` link to the replacement for retiring ones.

## 3. Coding Standards
- **Guard Clauses:** Prefer early returns (guard clauses) over nested `if` statements to reduce cognitive load and improve readability.

## 4. Quality Assurance & Testing
- **Code Coverage:** Minimum **90% coverage** required for all new features.
- **Mutation Testing:** Use **PITest** (or similar) to ensure test quality beyond simple coverage metrics.
- **Performance:** While strict SLAs are not enforced initially, performance is a core value and should guide architectural choices.

## 5. Development Workflow
- **Branching Model:** **Trunk-Based Development**. All changes should be merged into `main` as frequently as possible.
- **Commit Messages:** Follow **[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)**.
    - *Format:* `<type>(<scope>): <description>`
    - *Example:* `feat(core): add hierarchical timing wheel logic`
- **Pull Requests:** Small, atomic, and verified by tests.

## 6. Architecture & Design
- **Core Principles:** Focus on high-concurrency, low-latency, and durability.
- **Surgical Updates:** Changes should be targeted and idiomatic, avoiding unrelated refactoring.
