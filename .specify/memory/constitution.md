<!--
Sync Impact Report
==================
Version change: (none, template) → 1.0.0
Rationale: Initial ratification of the project constitution. MAJOR because this
establishes the full binding governance baseline for the first time (no prior
enforceable version existed — the file was an empty template).

Modified principles: N/A (initial adoption, no prior titled principles)

Added sections:
- Core Principles: I. API Contract-First Design, II. Security by Default
  (NON-NEGOTIABLE), III. Test-First Development (NON-NEGOTIABLE),
  IV. Simplicity & Maintainability, V. Observability & Operational Readiness,
  VI. Versioning & Backward Compatibility, VII. Performance & Scalability
- Architecture & Technology Standards
- Development Workflow & Quality Gates
- Governance

Removed sections: N/A

Templates requiring updates:
- ✅ .specify/templates/plan-template.md — Constitution Check gate references
  generic principles compatible with this version; no structural change needed.
- ✅ .specify/templates/spec-template.md — Requirements/Success Criteria
  sections already align with testable, technology-agnostic requirements
  mandated here; no structural change needed.
- ✅ .specify/templates/tasks-template.md — Task categorization (setup,
  foundational, security, observability) already accommodates principle-driven
  task types; no structural change needed.
- ✅ .claude/skills/speckit-constitution/SKILL.md — this command file; generic
  agent-agnostic language already in use, no outdated references found.
- ⚠ README.md — does not exist yet; recommend creating one that references this
  constitution when project documentation is authored.

Follow-up TODOs:
- TODO(RATIFICATION_DATE): Confirmed as adoption date below using the date this
  constitution was first ratified (2026-07-25). Update only if an earlier,
  documented ratification date is discovered.
-->

# Quick Note Constitution

## Core Principles

### I. API Contract-First Design

Every REST endpoint MUST be designed as a contract before implementation
begins. The request/response schema, status codes, error formats, and
authentication requirements MUST be documented (e.g., OpenAPI/JSON Schema)
prior to writing handler code. Endpoints MUST follow REST conventions:
resource-oriented URLs (nouns, not verbs), correct HTTP methods (GET, POST,
PUT, PATCH, DELETE) mapped to their standard semantics, and correct status
codes (2xx success, 4xx client error, 5xx server error). Breaking changes to
a published contract MUST NOT be made without a version increment (see
Principle VI). Responses MUST use a single, consistent JSON error envelope
across all endpoints.

**Rationale**: Enterprise APIs are consumed by clients the team does not
control (other teams, partners, third parties). A contract agreed upfront
prevents costly renegotiation after integration and keeps client and server
work parallelizable.

### II. Security by Default (NON-NEGOTIABLE)

Every endpoint MUST enforce authentication and authorization explicitly;
there is no implicit "trusted by default" endpoint. All input MUST be
validated and sanitized at the API boundary before use. Secrets, credentials,
and tokens MUST NOT be committed to source control or logged in plaintext.
All external traffic MUST use TLS. Data classified as sensitive (PII,
credentials, financial data) MUST be encrypted at rest. Dependencies MUST be
kept free of known critical/high vulnerabilities; new dependencies introducing
such vulnerabilities MUST NOT be merged. Every security-relevant decision
(authz model, encryption approach, rate limiting) MUST be documented in the
feature's plan.

**Rationale**: Security defects in a REST API are exploitable by any client
that can reach the network boundary. Treating security as a default rather
than an opt-in prevents the single-missed-check failure mode that causes
breaches, and is non-negotiable because the cost of a breach vastly exceeds
the cost of consistent enforcement.

### III. Test-First Development (NON-NEGOTIABLE)

Contract tests MUST be written from the API contract before implementation
and MUST fail prior to implementation (Red-Green-Refactor). Every endpoint
MUST have automated tests covering: the success path, authentication/
authorization failure, validation failure, and at least one relevant edge
case. Integration tests are REQUIRED for any change touching persistence,
inter-service communication, or shared schemas. A pull request MUST NOT merge
with failing tests or with untested new endpoints.

**Rationale**: REST APIs are long-lived integration surfaces; regressions
surface as breakage in systems the team may not directly observe.
Test-first enforces that behavior is specified and verifiable before it
becomes load-bearing for consumers, and is non-negotiable because untested
API behavior is unverifiable behavior.

### IV. Simplicity & Maintainability

Implementations MUST use the simplest design that satisfies the current,
documented requirement (YAGNI). New abstractions, layers, patterns, or
frameworks MUST be justified by a concrete, present need — not a hypothetical
future one. Duplication of a few similar lines is preferred over a premature
abstraction. Any deviation adding architectural complexity (e.g., an
additional service, a new dependency, a design pattern beyond straightforward
layering) MUST be recorded with its justification (see Complexity Tracking in
the plan template) and MUST show that a simpler alternative was rejected for
a specific, stated reason.

**Rationale**: Enterprise systems accumulate maintainers over years who did
not write the original code. Unjustified complexity compounds into
maintenance cost and onboarding friction far exceeding the cost of the
extra work simplicity requires up front.

### V. Observability & Operational Readiness

Every service MUST emit structured logs (not free-form text) for requests,
errors, and significant state changes, including a correlation/request ID
that propagates across service boundaries. Every service MUST expose a
health check endpoint suitable for automated liveness/readiness probing.
Errors returned to clients MUST be distinguishable from errors logged
internally — internal details (stack traces, SQL, internal hostnames) MUST
NOT leak into client-facing responses. Metrics for latency, error rate, and
throughput SHOULD be exposed for any endpoint expected to carry production
traffic.

**Rationale**: An API that cannot be observed in production cannot be
operated reliably; enterprise SLAs require the ability to detect, diagnose,
and resolve incidents without redeploying instrumentation after the fact.

### VI. Versioning & Backward Compatibility

The API MUST expose an explicit version (e.g., in the URL path or a header).
Backward-incompatible changes (removing/renaming fields, changing types,
altering status code semantics, tightening validation on existing fields)
MUST NOT be introduced into an existing version; they MUST be released as a
new version. Additive, backward-compatible changes (new optional fields, new
endpoints) MAY be released within the current version. Deprecated versions
MUST be announced with a documented sunset timeline before removal.
Internal service versioning MUST follow semantic versioning
(MAJOR.MINOR.PATCH).

**Rationale**: External and internal consumers integrate against a specific
contract; silently breaking that contract breaks consumers the API owner may
not be able to see or coordinate with directly, which is unacceptable at
enterprise scale.

### VII. Performance & Scalability

Services MUST be designed to be stateless at the application layer so
horizontal scaling requires no sticky session or local-state coordination.
Endpoints expected to handle non-trivial load MUST have a documented
performance target (e.g., p95 latency, throughput) established during
planning, and MUST be load-tested against that target before being declared
production-ready. Pagination MUST be used for any endpoint that can return an
unbounded collection. Expensive operations (N+1 queries, unbounded joins,
synchronous long-running work in the request path) MUST be identified and
addressed before merge, not deferred as "optimize later" without an explicit,
tracked follow-up.

**Rationale**: Enterprise APIs must sustain growth in traffic and data volume
without architectural rewrites; performance and scalability problems are far
cheaper to prevent at design time than to retrofit once a service is load-bearing.

## Architecture & Technology Standards

REST APIs in this project MUST follow a layered architecture that separates
routing/controllers, business logic (services), and data access
(repositories/persistence) — business logic MUST NOT be embedded directly in
route handlers, and data access MUST NOT be embedded directly in business
logic. Cross-cutting concerns (authentication, logging, error handling, rate
limiting) MUST be implemented as middleware/interceptors applied
consistently, not duplicated per-endpoint. All configuration (connection
strings, feature flags, external endpoints) MUST be externalized from code
(environment variables or a configuration service) — MUST NOT be hard-coded.
Database schema changes MUST be managed through versioned, reversible
migrations; MUST NOT be applied ad hoc against production. All inter-service
and client-facing communication MUST use JSON over HTTP unless a documented
requirement (e.g., streaming, binary payloads) justifies an alternative, in
which case the alternative MUST be recorded with its justification.

## Development Workflow & Quality Gates

Every change MUST go through code review by at least one other contributor
before merge; the reviewer MUST verify compliance with this constitution, not
only correctness. Continuous integration MUST run the full automated test
suite, linting, and a dependency vulnerability scan on every pull request;
a red pipeline MUST block merge. Any request that violates a Core Principle
MUST either be revised to comply or have its violation explicitly justified
in the plan's Complexity Tracking section and approved by a reviewer —
silent violations are not permitted. Documentation (API contract, README,
runbook) MUST be updated in the same change that alters the behavior it
describes, not deferred to a follow-up.

## Governance

This constitution supersedes any conflicting team convention, prior practice,
or informal agreement. All specifications, implementation plans, task lists,
and code reviews MUST verify compliance with these principles; the
Constitution Check gate in the plan template is the enforcement point for new
features, and code review is the enforcement point for all other changes.

**Amendment procedure**: A proposed amendment MUST be written as a concrete
diff to this file, MUST state the rationale for the change, and MUST update
the version per the policy below. Amendments take effect once merged; any
in-flight work found to conflict with a newly merged amendment MUST be
updated to comply before it merges.

**Versioning policy**: This constitution follows semantic versioning.
- MAJOR: Backward-incompatible governance changes — a principle is removed or
  redefined such that previously compliant work would no longer comply.
- MINOR: A new principle or section is added, or existing guidance is
  materially expanded.
- PATCH: Clarifications, wording fixes, or non-semantic refinements that do
  not change what is required.

**Compliance review**: Every plan produced by `/speckit-plan` MUST pass the
Constitution Check gate before Phase 0 research begins and MUST be re-checked
after Phase 1 design. Any unresolved violation MUST be documented in
Complexity Tracking with a justification; unjustified violations MUST block
progression to implementation.

**Version**: 1.0.0 | **Ratified**: 2026-07-25 | **Last Amended**: 2026-07-25
