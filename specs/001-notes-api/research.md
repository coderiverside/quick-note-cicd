# Research: Notes Management API

**Phase 0 output** for [plan.md](plan.md). Resolves every open technical decision implied by the Technical Context and by the constitution's mandatory principles, given the user-specified stack (Java 21+, Quarkus, PostgreSQL, Hibernate ORM with Panache, Docker/Docker Compose).

## 1. Authentication & Authorization Mechanism

- **Decision**: Keycloak is the OIDC/OAuth2 identity provider (user-specified). The service is configured as an OIDC **resource server** using `quarkus-oidc` (`quarkus.oidc.application-type=service`), pointed at the Keycloak realm's issuer URL, and validates every incoming bearer JWT's signature, issuer, audience, and expiry against Keycloak's published JWKS. The authenticated user's identity is the JWT `sub` claim (Keycloak's stable user id), mapped to the `owner_id` reference on every `Note`/`Label`. No login/registration/token-issuance endpoints are implemented by this service (per spec Assumption A3 / FR-028) — Keycloak owns the login/credential/token-issuance flow entirely; this API only validates tokens it receives.
- **Rationale**: The constitution (Principle II, Security by Default) requires every endpoint to enforce authentication/authorization explicitly; a REST API cannot satisfy that with no auth mechanism at all. Delegating identity to Keycloak (an enterprise-standard OIDC provider) rather than hand-rolling token issuance avoids the far larger security surface of building credential storage, password reset, MFA, etc. in-house (Principle IV, Simplicity — don't reimplement what a dedicated, audited IAM system already solves). `quarkus-oidc` in resource-server mode requires no session state (Principle VII).
- **Alternatives considered**:
  - *`quarkus-smallrye-jwt` validating an unspecified external issuer*: this was the prior plan's approach before Keycloak was specified; superseded because Keycloak is now the concrete, user-chosen IAM system, and `quarkus-oidc` is Quarkus's purpose-built extension for integrating with an OIDC provider like Keycloak (automatic JWKS discovery/rotation via the realm's well-known endpoint, native `SecurityIdentity`/`@RolesAllowed` integration for RBAC — see §1b).
  - *API keys*: rejected — doesn't carry a verifiable per-user subject claim or role information without a custom lookup table, effectively reinventing what Keycloak already provides.
  - *Session cookies*: rejected — violates statelessness (Principle VII) and implies a login flow the service itself does not own.

## 1b. Role-Based Access Control (RBAC)

- **Decision**: A single realm role, `user`, is defined in Keycloak and required on every endpoint via `@RolesAllowed("user")` (enforced globally through a base resource class / `SecurityIdentity` check, not repeated ad hoc per method). RBAC is a coarse-grained gate — "is this caller a recognized API user at all" — layered in front of the fine-grained, per-resource ownership check from §2. A request with a valid JWT but missing the `user` role is rejected with `403 Forbidden`; a request for a resource the caller doesn't own is rejected with `404 Not Found` (FR-015) — these are deliberately different status codes because they mean different things (not authorized to use the API at all, vs. this specific resource doesn't exist for you).
- **Rationale**: The user-specified stack calls for Keycloak RBAC enforcement explicitly. The spec itself defines only one class of end user (no admin/support role is in scope), so a single `user` role is the minimum RBAC model that satisfies "RBAC enforcement" without inventing roles the spec never asked for (Principle IV, YAGNI) — additional roles (e.g., an `admin` role for support tooling) can be added additively later without a breaking change (Principle VI).
- **Alternatives considered**: Fine-grained per-endpoint roles (e.g., separate `notes:read`/`notes:write` scopes) — rejected as unjustified complexity; the spec has no requirement distinguishing read-only API users from full-access ones.

## 2. Authorization / Ownership Enforcement Pattern

- **Decision**: Every persistence query for Note, Label, and ChecklistItem is scoped by `owner_id = currentUser` at the repository/service layer (never trust a client-supplied owner id). Lookups for a resource owned by another user return HTTP 404 (not 403), per FR-015's "return not-found without revealing whether the resource exists for another user." This is distinct from — and layered behind — the RBAC `user`-role check in §1b, which returns 403 when it fails; ownership failures always return 404, never 403, regardless of role.
- **Rationale**: Centralizing the ownership filter in the service layer (rather than per-resource ad hoc checks in controllers) avoids the single-missed-check failure mode the constitution's Security principle warns about, and keeps the check in one place per entity (Simplicity).
- **Alternatives considered**: Row-level security in PostgreSQL — rejected as unnecessary complexity for a single-tenant-per-row model with no direct DB access from clients; application-layer scoping is sufficient and simpler (Principle IV).

## 3. Data Access Pattern

- **Decision**: Hibernate ORM with Panache, using the **Repository pattern** (`PanacheRepositoryBase`) rather than the active-record pattern.
- **Rationale**: Repository pattern keeps persistence access out of entity classes, aligning directly with the constitution's mandatory separation of business logic from data access (Architecture & Technology Standards). Panache still eliminates JPA boilerplate (Simplicity).
- **Alternatives considered**: Panache active-record (entities extend `PanacheEntity` and query themselves) — rejected because it blends the data-access layer into the entity/model layer, which the constitution's layering requirement discourages.

## 4. Schema Migrations

- **Decision**: Flyway (`quarkus-flyway`), with versioned SQL migration scripts under `src/main/resources/db/migration`.
- **Rationale**: The constitution requires schema changes to be managed through versioned, reversible migrations, never applied ad hoc. Flyway is the standard Quarkus-integrated choice.
- **Alternatives considered**: Hibernate `hbm2ddl.auto=update` — rejected outright; the constitution explicitly forbids ad hoc schema application, and auto-DDL provides no versioned/reviewable migration history.

## 5. Pagination Strategy

- **Decision**: Offset-based pagination (`page`, `size` query parameters) with a default and maximum page size, applied to every collection-returning endpoint (list notes, list labels, search results, checklist items are returned inline with their parent note so are not separately paginated).
- **Rationale**: Constitution Principle VII mandates pagination for any endpoint that can return an unbounded collection. Offset pagination is the simplest option that meets the need (Simplicity/YAGNI) at the data scale implied by the spec (SC-002: ~10,000 notes/user) — keyset pagination would add complexity not currently justified.
- **Alternatives considered**: Keyset/cursor pagination — more scalable for very large offsets but more complex to implement and consume; rejected for now as unjustified complexity at the stated scale (documented so it can be revisited if scale assumptions change).

## 6. Search Implementation

- **Decision**: PostgreSQL native full-text search (`tsvector`/`tsquery`) over the note's title and text content, backed by a GIN index, exposed through a `search` query parameter on the notes listing endpoint.
- **Rationale**: SC-002 and SC-004 require search across 10,000+ notes per user with no noticeable latency increase. Simple `ILIKE '%term%'` cannot use an index for arbitrary substring matches and degrades linearly with data volume; PostgreSQL full-text search is index-backed and already available in the chosen database with no new infrastructure dependency (Simplicity — no external search service needed).
- **Alternatives considered**: Dedicated search engine (Elasticsearch/OpenSearch) — rejected as disproportionate infrastructure for the stated per-user data scale (Principle IV, YAGNI); introduces an additional service to operate, deploy, and keep in sync. Plain `ILIKE` — rejected on performance grounds at the target scale.

## 7. Concurrency / Conflicting Update Handling

- **Decision**: Last-write-wins for note/checklist-item updates — no optimistic-locking version conflict errors surfaced to clients. `updated_at` is refreshed on every write.
- **Rationale**: The spec's edge case explicitly requires that concurrent updates never produce unexpected request errors and leave the note in a valid state ("a consistent conflict-resolution behavior (e.g., last-write-wins)"). Optimistic locking (JPA `@Version`) would instead surface 409 Conflict responses, which is a valid but different behavior the spec did not select; last-write-wins is simpler and matches the explicit requirement (Simplicity).
- **Alternatives considered**: Optimistic locking with `@Version` — rejected because it contradicts the "no request errors out unexpectedly" requirement in the spec's edge case; noted here so it can be revisited if the product decision changes.

## 8. Trash Retention / Auto-Purge (FR-014, 7-day retention)

- **Decision**: A Quarkus `@Scheduled` job runs on a fixed daily interval and permanently deletes any note with `trashed = true` whose `trashed_at` timestamp is older than 7 days (cascading delete of its checklist items and label associations).
- **Rationale**: This is a background, time-based business rule with no client-facing endpoint trigger — a scheduled in-process job is the simplest mechanism available in Quarkus and requires no additional infrastructure (Simplicity), consistent with a single-service deployment.
- **Alternatives considered**: External cron hitting a purge endpoint — rejected as an unnecessary extra network hop and an endpoint that would itself need to be secured for an operation with no legitimate external caller; a dedicated message queue/worker — rejected as disproportionate infrastructure for a daily housekeeping task (YAGNI).

## 9. API Versioning

- **Decision**: URL path versioning: all endpoints are rooted at `/api/v1/...`.
- **Rationale**: Constitution Principle VI requires an explicit, visible API version. Path versioning is the most discoverable and cache/proxy-friendly option and needs no custom header contract with clients.
- **Alternatives considered**: Header-based versioning (e.g., `Accept: application/vnd.quicknote.v1+json`) — rejected as less discoverable for API consumers and unnecessary complexity given a single currently-supported version.

## 10. Error Response Format

- **Decision**: A single JSON error envelope for all error responses: `{ "error": { "code": string, "message": string, "details": [ ... ]? } }`, produced by centralized JAX-RS `ExceptionMapper`s (validation errors → 400, auth failures → 401, not-found → 404, conflicts → 409, unhandled → 500 with no internal detail leakage).
- **Rationale**: Constitution Principle I mandates a single, consistent JSON error envelope across all endpoints; Principle V mandates that internal error detail never leaks to clients.
- **Alternatives considered**: RFC 7807 `application/problem+json` — a reasonable alternative; not chosen only because a project-specific minimal envelope is simpler to implement and document for this API's current consumer set (Simplicity), and can be migrated to `problem+json` later as an additive, backward-compatible change if needed.

## 11. Observability

- **Decision**: `quarkus-smallrye-health` for `/q/health/live` and `/q/health/ready`; `quarkus-micrometer` + `quarkus-micrometer-registry-prometheus` for latency/error-rate/throughput metrics; JSON structured logging (`quarkus-logging-json`) with a request-scoped correlation ID (generated or propagated from an inbound `X-Request-Id` header) attached to every log line and echoed back in the response header.
- **Rationale**: Directly required by constitution Principle V (structured logs with correlation ID, health endpoints, metrics for production-traffic endpoints).
- **Alternatives considered**: None seriously considered — these are the standard Quarkus extensions for each requirement and introduce no extra infrastructure beyond what Docker Compose already provisions for local dev (a Prometheus scrape target, no new service).

## 12. Testing Strategy & Development Process (TDD)

- **Decision**: `quarkus-junit5` + `rest-assured` for contract tests (one per endpoint, covering success/RBAC-failure (403)/ownership-failure (404)/validation-failure paths per constitution Principle III); integration tests use Quarkus Dev Services (Testcontainers-backed) to run against real PostgreSQL and a real Keycloak container rather than in-memory or mocked substitutes. Development MUST follow strict Test-Driven Development, mandated explicitly by the user for this feature and reinforced by constitution Principle III (NON-NEGOTIABLE): for every endpoint and business rule, the test is written first, run to confirm it fails (no implementation yet or a deliberately-wrong stub), then the minimum implementation is added to pass it, then refactored. `tasks.md` (generated by `/speckit-tasks`) MUST encode this as an explicit test-task-before-implementation-task ordering, not left as an unstated developer discipline.
- **Rationale**: Constitution Principle III requires integration tests for anything touching persistence and forbids merging untested endpoints; the user's explicit TDD directive makes the red-green-refactor discipline a hard process requirement, not just an outcome. Testing against real PostgreSQL and real Keycloak (both available as Dev Services/Testcontainers) avoids behavioral drift between test doubles and the production dependencies — directly relevant here since the plan relies on PostgreSQL-specific full-text search (§6) and Keycloak-specific token/role semantics (§1, §1b).
- **Alternatives considered**: H2 in-memory database for tests — rejected because H2's SQL dialect cannot exercise PostgreSQL full-text search or other Postgres-specific behavior, which would leave the persistence layer under-tested. Mocking the OIDC/JWT validation layer instead of testing against a real Keycloak container — rejected because it would leave the RBAC and token-validation configuration itself untested, which is precisely the part most likely to be misconfigured (wrong issuer, wrong role claim path, etc.).

## 13. Containerization / Local Dev

- **Decision**: A multi-stage `Dockerfile` (JVM mode) under `src/main/docker/`, plus a root-level `docker-compose.yml` that provisions a PostgreSQL container, a Keycloak container (official `quay.io/keycloak/keycloak` image, started with a declarative realm import at `docker/keycloak/quicknote-realm.json` defining the `quicknote` realm, `notes-api` client, and `user` realm role), and the application container — wired together via environment variables (datasource URL/credentials, OIDC issuer URL all externalized per constitution's Architecture standard against hard-coded configuration).
- **Rationale**: Directly specified by the user's requested stack; realm import (rather than manual click-ops setup) keeps Keycloak's configuration itself version-controlled and reproducible, consistent with the constitution's ban on ad hoc, undocumented configuration. Satisfies the constitution's requirement that configuration be externalized rather than hard-coded.
- **Alternatives considered**: Quarkus native-image build — noted as a future optimization for startup time/footprint, not adopted now to avoid the added build complexity (native-image compilation constraints) until a concrete need is documented (Simplicity/YAGNI). Manually configuring Keycloak through its admin console for local dev — rejected as non-reproducible and undocumented, contrary to treating infrastructure setup with the same rigor as schema migrations.

## Summary of Resolved Technical Context

| Aspect | Resolution |
|---|---|
| Language/Version | Java 21 |
| Primary Framework | Quarkus (RESTEasy Reactive / Jakarta REST, Quarkus OIDC for Keycloak integration, SmallRye Health, Micrometer) |
| Identity & Access | Keycloak (OIDC/OAuth2 provider); `quarkus-oidc` resource-server JWT validation; single `user` realm role via `@RolesAllowed` RBAC (§1, §1b) |
| Persistence | PostgreSQL via Hibernate ORM with Panache (Repository pattern), Flyway migrations |
| Testing / Process | JUnit 5 + REST Assured (contract), Quarkus Dev Services/Testcontainers + PostgreSQL + Keycloak (integration); mandatory strict TDD (§12) |
| Target Platform | Linux container (Docker), deployable to any container runtime |
| Project Type | Single web-service (REST API), no frontend in scope |
| Performance Goals | p95 < 200ms for CRUD operations under normal load (supports spec SC-001's <1s end-to-end capture budget with headroom for network/client) |
| Constraints | Stateless application layer; TLS termination and disk-level encryption-at-rest are deployment/infrastructure responsibilities, not application code (documented in quickstart.md) |
| Scale/Scope | Designed for 10,000+ notes per user (per SC-002) without degradation |

No `NEEDS CLARIFICATION` markers remain.
