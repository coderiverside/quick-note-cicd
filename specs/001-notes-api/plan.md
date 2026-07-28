# Implementation Plan: Notes Management API

**Branch**: `001-notes-api` | **Date**: 2026-07-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-notes-api/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

A REST API, similar in scope to Google Keep, that lets an authenticated user capture, organize, find, and manage personal notes at scale. Notes carry a title, text content, color, pinned/archived/trashed status, an optional checklist of checkable items, and zero or more user-defined labels. Built as a single stateless Quarkus (Java 21) service backed by PostgreSQL via Hibernate ORM with Panache, with authentication and RBAC delegated to Keycloak (OIDC/OAuth2) — the service validates Keycloak-issued JWTs and enforces a baseline `user` role before applying per-resource ownership checks — Flyway-managed schema migrations, PostgreSQL full-text search for note lookup, a scheduled job for 7-day trash auto-purge, and Docker/Docker Compose orchestrating the application, PostgreSQL, and Keycloak for local development. Implementation MUST follow strict Test-Driven Development (see "Development Process" below).

## Development Process

Per explicit user direction and constitution Principle III (Test-First Development, NON-NEGOTIABLE), this feature MUST be built using strict Red-Green-Refactor TDD: for every unit of work in `tasks.md`, the corresponding contract and/or integration test MUST be written first, run and confirmed to **fail**, only then followed by the minimum implementation to make it pass, then refactored. `/speckit-tasks` MUST sequence a failing-test task immediately before its implementation task for every endpoint and business rule — never generate an implementation task without a preceding test task.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Quarkus 3.x — RESTEasy Reactive (Jakarta REST) for endpoints, Hibernate ORM with Panache (Repository pattern) for persistence, Quarkus OIDC (`quarkus-oidc`) configured as an OIDC resource server for Keycloak-issued JWT validation plus `@RolesAllowed` RBAC enforcement, SmallRye Health for health checks, Micrometer + Prometheus registry for metrics, Hibernate Validator (Jakarta Bean Validation) for input validation, Flyway for schema migrations

**Storage**: PostgreSQL (relational), accessed via Hibernate ORM/Panache; schema versioned with Flyway migrations

**Testing**: JUnit 5 + REST Assured for contract tests (per-endpoint success/401/403/validation-failure/404 coverage), written before implementation per the mandatory TDD process above; Quarkus Dev Services (Testcontainers-backed PostgreSQL and Keycloak) for integration tests exercising persistence, full-text search, and real token/role validation

**Target Platform**: Linux container (Docker), stateless — deployable to any container runtime/orchestrator

**Project Type**: Single web-service (REST API only; no frontend in scope for this feature)

**Performance Goals**: p95 < 200ms for standard CRUD operations under normal load; supports spec SC-001 (<1s note capture) and SC-002 (10,000+ notes/user with no noticeable degradation) with headroom for network/client overhead

**Constraints**: Application layer MUST remain stateless (no server-side session, no local token cache beyond in-request validation); TLS termination and disk-level encryption-at-rest are deployment/infrastructure responsibilities documented in [quickstart.md](quickstart.md), not application code; all collection-returning endpoints MUST be paginated; Keycloak realm/client/role provisioning for local dev is declarative (realm import JSON), not application code

**Scale/Scope**: Single service; designed for 10,000+ notes per user without degradation (SC-002); 6 user stories, 4 primary resource types (Note, Checklist Item, Label, and the User identity carried by the Keycloak-issued JWT)

See [research.md](research.md) for the rationale behind each of these decisions.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design — see "Post-Design Constitution Check" below.*

| Principle | Status | How this plan satisfies it |
|---|---|---|
| I. API Contract-First Design | PASS | `contracts/openapi.yaml` is authored in Phase 1 before any implementation task is generated; resource-oriented URLs under `/api/v1/...`; standard HTTP methods/status codes; single JSON error envelope (research.md §10). |
| II. Security by Default (NON-NEGOTIABLE) | PASS | Keycloak-issued JWT bearer auth validated on every endpoint via `quarkus-oidc`, plus a coarse-grained `user` realm role required through `@RolesAllowed` before any request reaches business logic (research.md §1); fine-grained ownership scoping on every query, not per-controller ad hoc checks (research.md §2); input validated via Bean Validation at the API boundary; no secrets in source (config/realm import externalized, research.md §13); TLS termination is a deployment-layer requirement documented in quickstart.md since this service is not the network edge. |
| III. Test-First Development (NON-NEGOTIABLE) | PASS | Strict TDD is mandatory for this feature (see "Development Process" above, and constitution reinforcement here): contract tests (REST Assured) written and confirmed failing before each endpoint's implementation task; integration tests against real PostgreSQL via Dev Services for every persistence-touching change (research.md §12). `/speckit-tasks` MUST order every test task immediately before its implementation task. |
| IV. Simplicity & Maintainability | PASS | Panache Repository pattern over active-record to keep layering clean without extra boilerplate; offset pagination chosen over keyset (research.md §5); last-write-wins over optimistic locking (research.md §7); in-process scheduled job over external cron/queue for trash purge (research.md §8) — each alternative's rejection is documented in research.md rather than silently assumed. |
| V. Observability & Operational Readiness | PASS | Structured JSON logs with request correlation ID; `/q/health/live` and `/q/health/ready`; Micrometer/Prometheus metrics; error envelope never leaks internal detail (research.md §11, §10). |
| VI. Versioning & Backward Compatibility | PASS | All endpoints rooted at `/api/v1/...` (research.md §9); no breaking change is introduced by this initial version. |
| VII. Performance & Scalability | PASS | Stateless JWT-based auth (no session affinity); pagination on every collection endpoint; PostgreSQL full-text search with GIN index instead of unindexed scans, chosen specifically for the 10,000-notes/user scale target (research.md §5, §6); documented p95 target above. |

No violations requiring justification. Complexity Tracking table below is left empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-notes-api/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── openapi.yaml
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
src/main/java/com/quicknote/api/
├── note/
│   ├── Note.java                  # JPA entity
│   ├── NoteRepository.java        # Panache repository (data access)
│   ├── NoteService.java           # business logic, ownership enforcement
│   ├── NoteResource.java          # JAX-RS resource (routing/controller)
│   └── dto/                       # request/response DTOs for notes + checklist items
├── checklist/
│   ├── ChecklistItem.java         # JPA entity (belongs to a Note)
│   └── (repository/service logic co-located with note/, since a checklist
│         item has no independent lifecycle outside its parent note)
├── label/
│   ├── Label.java
│   ├── LabelRepository.java
│   ├── LabelService.java
│   └── LabelResource.java
├── security/
│   └── CurrentUserProvider.java   # extracts owner identity (JWT `sub`) from the OIDC-validated,
│                                  # RBAC-checked SecurityIdentity supplied by quarkus-oidc
├── common/
│   ├── exception/                 # centralized ExceptionMapper classes -> JSON error envelope
│   ├── pagination/                # shared page request/response types
│   └── web/                       # correlation-id filter
└── config/                        # CDI producers / cross-cutting configuration

src/main/resources/
├── application.properties
└── db/migration/                  # Flyway versioned SQL migrations

src/main/docker/
└── Dockerfile.jvm

docker/keycloak/
└── quicknote-realm.json           # declarative realm import: `quicknote` realm, `notes-api` client,
                                    # `user` realm role, local test users (dev/test only)

docker-compose.yml                 # app + PostgreSQL + Keycloak for local development

src/test/java/com/quicknote/api/
├── note/          # contract + integration tests for note lifecycle, pin/color/archive/trash, search
├── checklist/     # contract + integration tests for checklist item operations
└── label/         # contract + integration tests for label CRUD and note filtering
```

**Structure Decision**: Single Quarkus Maven project (Option 1: single project), packaged by feature (`note`, `checklist`, `label`) with each feature package internally separated into entity / repository / service / resource layers per the constitution's mandatory routing↔business-logic↔data-access separation. `checklist` has no standalone resource/repository because checklist items are always accessed through their owning note (see [data-model.md](data-model.md)); this is a deliberate simplicity choice, not an omission.

## Complexity Tracking

*No entries — Constitution Check reported no violations requiring justification.*

## Post-Design Constitution Check

*Re-evaluated after Phase 1 design (data-model.md, contracts/openapi.yaml, quickstart.md).*

| Principle | Status | Confirmation from Phase 1 artifacts |
|---|---|---|
| I. API Contract-First Design | PASS | `contracts/openapi.yaml` fully specifies every resource (Note, Checklist Item, Label) with resource-oriented URLs, correct HTTP methods/status codes, and the single `Error` envelope schema used consistently across all error responses. |
| II. Security by Default | PASS | `security: [bearerAuth]` applied globally in the contract, with the token issued and signed by Keycloak; every path's 401/404 responses are explicit; `data-model.md` scopes every entity by `owner_id`, and FR-015's not-found-over-forbidden behavior is reflected in the `NotFound` response description on every owned resource; `quickstart.md` now includes acquiring a token from Keycloak and a 403-vs-404 note distinguishing missing-role from missing-resource. |
| III. Test-First Development | PASS | `quickstart.md` §4 enumerates one concrete, runnable scenario per user story (including negative/edge cases: empty note rejection, archive-from-trash conflict, cross-user 404, missing-role 403) that maps directly to the contract tests `/speckit-tasks` will generate, each preceded by a failing-test task per the mandatory TDD process. |
| IV. Simplicity & Maintainability | PASS | Data model avoids an unnecessary separate resource/repository for `ChecklistItem` (composed under `Note`); no additional services or infrastructure introduced beyond what research.md justified. |
| V. Observability & Operational Readiness | PASS | `quickstart.md` §3 makes health-check verification an explicit, required first validation step before functional testing. |
| VI. Versioning & Backward Compatibility | PASS | `contracts/openapi.yaml` server URL is `/api/v1`; `info.version: "1.0.0"`. |
| VII. Performance & Scalability | PASS | `NotePage`/`LabelPage` schemas make pagination structurally mandatory for every list response; `data-model.md`'s index list directly supports the filtered-listing and search query patterns the contract exposes. |

No violations. Design is ready for `/speckit-tasks`.
