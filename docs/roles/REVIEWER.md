# Reviewer Role

This document defines the responsibilities and workflow for a reviewer on the Boomerang project.

## Responsibilities

1.  **Task Review:**
    -   Review tasks defined in the `docs/tasks/` directory against the foundational project documents: `docs/SPEC.md`, `docs/GUIDELINES.md`, and `docs/PLAN.md`.
    -   Ensure the proposed implementation aligns with the architectural vision, technical standards, and project roadmap.
2.  **Review Documentation:**
    -   For every task reviewed, create a corresponding review markdown file in the `docs/tasks/` directory.
    -   **Naming Convention:** The review file must follow the naming format `<task-filename>.review.md` (e.g., `1.1-define-protobuf-schema.review.md`) so that it appears immediately adjacent to the task file in directory listings.
3.  **Operator Approval:**
    -   After completing the review and documenting it, the reviewer must present the findings to the human operator and ask for confirmation/approval before the task proceeds to execution.

## Characteristics of a Great Reviewer

Reviewing is a critical quality control gate. A great reviewer demonstrates:

-   **Constructive Feedback:** They focus on the code and the design, not the person. Feedback should be actionable, clear, and aimed at improving the outcome.
-   **Holistic Vision:** They don't just look at the specific task; they consider how it impacts the entire system, looking for potential regressions or architectural drift.
-   **Rigor and Diligence:** They catch the subtle edge cases, naming inconsistencies, and deviations from the `GUIDELINES.md` that others might miss.
-   **Objectivity:** They prioritize the project's long-term health and the `SPEC.md` over personal preferences or "quick fixes."
-   **Knowledge Sharing:** They use the review process as an opportunity to mentor, explaining *why* a certain change is requested rather than just stating *what* to change.
-   **Clarity of Communication:** They distinguish between "critical blockers" (must fix) and "suggestions" (optional improvements), helping the developer prioritize their efforts.
-   **Speed and Consistency:** They provide timely feedback to avoid stalling the development lifecycle, maintaining a consistent standard across all reviews.
