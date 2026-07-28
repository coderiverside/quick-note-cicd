---

description: "Task list for Notes Management API implementation"
---

# Tasks: Notes Management API

**Input**: Design documents from `/specs/001-notes-api/` (plan.md, spec.md, data-model.md, contracts/openapi.yaml, research.md, quickstart.md)

**Prerequisites**: plan.md ✅, spec.md ✅, data-model.md ✅, contracts/openapi.yaml ✅, research.md ✅, quickstart.md ✅

**Tests**: **MANDATORY, not optional.** `plan.md`'s "Development Process" section and constitution Principle III (NON-NEGOTIABLE) require strict Red-Green-Refactor TDD for this feature: every test task below MUST be completed, run, and observed to **fail** before its paired implementation task begins.

**Organization**: Tasks are grouped by user story (spec.md priorities P1–P4) to enable independent implementation and testing of each story, per plan.md's package-by-feature structure (`note/`, `checklist/`, `label/` under `src/main/java/com/quicknote/api/`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Maps the task to US1–US6 from spec.md; omitted for Setup/Foundational/Polish
- Every task names its exact file path(s), per `plan.md`'s Project Structure

## Path Conventions

Single Quarkus Maven project (see plan.md "Project Structure"):
- Main sources: `src/main/java/com/quicknote/api/{note,checklist,label,security,common,config}/`
- Resources: `src/main/resources/` (`application.properties`, `db/migration/`)
- Tests: `src/test/java/com/quicknote/api/{note,checklist,label,common}/`
- Infra: `src/main/docker/`, `docker/keycloak/`, `docker-compose.yml` (repository root)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization, dependencies, and local infrastructure scaffolding

- [X] T001 Create the Quarkus 3.x Maven project (`pom.xml` at repository root) with extensions: `quarkus-resteasy-reactive`, `quarkus-resteasy-reactive-jackson`, `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-oidc`, `quarkus-flyway`, `quarkus-hibernate-validator`, `quarkus-smallrye-health`, `quarkus-micrometer`, `quarkus-micrometer-registry-prometheus`, `quarkus-logging-json`, `quarkus-scheduler`, `quarkus-junit5`, `rest-assured`
- [X] T002 [P] Configure `src/main/resources/application.properties` with externalized datasource, Flyway, and `quarkus-oidc` issuer settings (no hard-coded connection strings, per constitution)
- [X] T003 [P] Create `docker-compose.yml` at repository root provisioning PostgreSQL, Keycloak, and the application service (research.md §13)
- [X] T004 [P] Create the declarative Keycloak realm import in `docker/keycloak/quicknote-realm.json` defining the `quicknote` realm, `notes-api` client, `user` realm role, and at least two local test users (research.md §13, §1b)
- [X] T005 [P] Create the multi-stage JVM Dockerfile in `src/main/docker/Dockerfile.jvm`
- [X] T006 [P] Configure a code-formatting/linting Maven plugin (e.g., Spotless) in `pom.xml` per the constitution's CI linting requirement

**Checkpoint**: Project builds (`./mvnw compile`) and `docker compose up -d` starts PostgreSQL + Keycloak.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented — schema, ownership/RBAC plumbing, error envelope, pagination, observability

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T007 [P] Create the baseline Flyway migration for the full schema (`note`, `checklist_item`, `label`, `note_label` tables, all fields, and every index from data-model.md, including the `search_vector` generated column + GIN index) in `src/main/resources/db/migration/V1__init_schema.sql`
- [X] T008 [P] Create the `Note` JPA entity (fields per data-model.md) in `src/main/java/com/quicknote/api/note/Note.java`
- [X] T009 [P] Create the `NoteColor` enum (fixed palette, Assumption A4) in `src/main/java/com/quicknote/api/note/NoteColor.java`
- [X] T010 Create `NoteRepository` (Panache `PanacheRepositoryBase`, owner-scoped query helpers per research.md §2) in `src/main/java/com/quicknote/api/note/NoteRepository.java` (depends on T008, T009)
- [X] T011 [P] Create `CurrentUserProvider`, extracting `owner_id` (JWT `sub`) from the injected `SecurityIdentity`, in `src/main/java/com/quicknote/api/security/CurrentUserProvider.java`
- [X] T012 Configure `quarkus-oidc` as an OIDC resource server against the Keycloak realm issuer and add a global `user`-role enforcement mechanism (`@RolesAllowed("user")` applied consistently, not ad hoc) in `src/main/java/com/quicknote/api/security/` and `src/main/resources/application.properties` (research.md §1, §1b)
- [X] T013 [P] Create the `Error` response DTO and centralized JAX-RS `ExceptionMapper`s producing the single JSON error envelope (validation→400, not-found→404, conflict→409, unhandled→500 with no internal detail leakage) in `src/main/java/com/quicknote/api/common/exception/`
- [X] T014 [P] Create shared `PageRequest`/`PageResponse` pagination types in `src/main/java/com/quicknote/api/common/pagination/`
- [X] T015 Create a correlation-ID `ContainerRequestFilter`/`ContainerResponseFilter` pair and configure structured JSON logging with the correlation ID in the MDC, in `src/main/java/com/quicknote/api/common/web/CorrelationIdFilter.java` and `src/main/resources/application.properties` (research.md §11)
- [X] T016 [P] Integration test verifying `/q/health/live` and `/q/health/ready` return HTTP 200 `"status": "UP"` against Dev Services (PostgreSQL + Keycloak) in `src/test/java/com/quicknote/api/HealthCheckIT.java`

**Checkpoint**: Foundation ready — schema exists, ownership/RBAC/error/pagination/observability plumbing compiles and T016 passes. No note/label/checklist endpoints exist yet; per-endpoint 401/403/404 coverage is verified within each user story's own contract tests below.

---

## Phase 3: User Story 1 - Capture a Note (Priority: P1) 🎯 MVP

**Goal**: An authenticated user can create a note (title/text), retrieve it, list their active notes, edit it, and remove it from their active view.

**Independent Test**: Create a note via the API, retrieve it, edit its content, and trash it — verified without labels, checklists, or archive.

### Tests for User Story 1 (MANDATORY — TDD)

> Write these first; run and confirm they FAIL before any implementation task in this phase.

- [X] T017 [P] [US1] Contract test for `POST /notes` (201 success, 400 empty-note per FR-001, 401, 403) in `src/test/java/com/quicknote/api/note/CreateNoteContractTest.java`
- [X] T018 [P] [US1] Contract test for `GET /notes` default active view + pagination (200, 401, 403) in `src/test/java/com/quicknote/api/note/ListNotesContractTest.java`
- [X] T019 [P] [US1] Contract test for `GET /notes/{noteId}` (200, 401, 403, 404) in `src/test/java/com/quicknote/api/note/GetNoteContractTest.java`
- [X] T020 [P] [US1] Contract test for `PATCH /notes/{noteId}` title/content update (200, 400, 401, 403, 404) in `src/test/java/com/quicknote/api/note/UpdateNoteContractTest.java`
- [X] T021 [P] [US1] Contract test for `POST /notes/{noteId}/trash` (200, 401, 403, 404; note excluded from the active list afterward) in `src/test/java/com/quicknote/api/note/TrashNoteContractTest.java`
- [X] T022 [P] [US1] Integration test for the full capture lifecycle (create → list → get → update → trash) against real PostgreSQL via Dev Services in `src/test/java/com/quicknote/api/note/NoteLifecycleIT.java`

### Implementation for User Story 1

- [X] T023 [P] [US1] Create `NoteCreateRequest`, `NoteUpdateRequest`, `Note` (response), `NotePage` DTOs per `contracts/openapi.yaml` in `src/main/java/com/quicknote/api/note/dto/`
- [X] T024 [US1] Create `NoteMapper` (entity ↔ DTO; `items`/`labelIds` serialize as empty arrays until US4/US5 add real content) in `src/main/java/com/quicknote/api/note/NoteMapper.java` (depends on T008, T023)
- [X] T025 [US1] Implement `NoteService` create/get/list/update/trash methods — owner-scoped (research.md §2), FR-001 empty-note validation, FR-008 active-view default — in `src/main/java/com/quicknote/api/note/NoteService.java` (depends on T010, T024)
- [X] T026 [US1] Implement `NoteResource` with `POST /notes`, `GET /notes`, `GET /notes/{noteId}`, `PATCH /notes/{noteId}`, `POST /notes/{noteId}/trash` in `src/main/java/com/quicknote/api/note/NoteResource.java` (depends on T025)

**Checkpoint**: T017–T022 all pass. A user can capture, read, edit, and trash notes end-to-end — deployable/demoable as the MVP.

---

## Phase 4: User Story 2 - Organize and Prioritize Notes (Priority: P2)

**Goal**: A user can pin/unpin notes and assign a color, both visible on retrieval.

**Independent Test**: Pin/unpin a note and assign a color; verify both persist on subsequent `GET` — independent of labels/checklists/archive.

### Tests for User Story 2 (MANDATORY — TDD)

- [X] T027 [P] [US2] Contract test for `POST`/`DELETE /notes/{noteId}/pin` (pin/unpin: 200, 401, 403, 404) in `src/test/java/com/quicknote/api/note/PinNoteContractTest.java`
- [X] T028 [P] [US2] Contract test for `PATCH /notes/{noteId}` color assignment (200 persists/round-trips, 400 invalid color) in `src/test/java/com/quicknote/api/note/UpdateNoteColorContractTest.java`
- [X] T029 [P] [US2] Integration test for pin + color persistence across retrieval in `src/test/java/com/quicknote/api/note/NoteOrganizationIT.java`

### Implementation for User Story 2

- [X] T030 [US2] Implement pin/unpin service methods and `POST`/`DELETE /notes/{noteId}/pin` resource endpoints in `src/main/java/com/quicknote/api/note/NoteService.java` and `src/main/java/com/quicknote/api/note/NoteResource.java` (depends on T025, T026)
- [X] T031 [US2] Wire `color` validation and persistence into `NoteService.update` (FR-005) in `src/main/java/com/quicknote/api/note/NoteService.java` (depends on T030)

**Checkpoint**: T027–T029 pass. Users can pin and color notes on top of the US1 MVP.

---

## Phase 5: User Story 3 - Archive and Trash Notes (Priority: P2)

**Goal**: A user can archive/unarchive notes, and fully manage the trash lifecycle (trash, restore, permanent delete, 7-day auto-purge), with archive blocked directly from trash (FR-010).

**Independent Test**: Archive a note (confirm excluded from active view), unarchive it, trash it, restore it, and permanently delete it — independent of labels/checklists.

### Tests for User Story 3 (MANDATORY — TDD)

- [X] T032 [P] [US3] Contract test for `POST`/`DELETE /notes/{noteId}/archive`, including 409 when currently trashed (FR-010) in `src/test/java/com/quicknote/api/note/ArchiveNoteContractTest.java`
- [X] T033 [P] [US3] Contract test for `DELETE /notes/{noteId}/trash` (restore, preserving prior archived state) in `src/test/java/com/quicknote/api/note/RestoreNoteContractTest.java`
- [X] T034 [P] [US3] Contract test for `DELETE /notes/{noteId}` permanent delete, including 409 when not yet trashed (FR-004) in `src/test/java/com/quicknote/api/note/DeleteNoteContractTest.java`
- [X] T035 [P] [US3] Contract test for `GET /notes?view=archived` and `?view=trashed` listings (FR-009, FR-012) in `src/test/java/com/quicknote/api/note/NoteViewFilterContractTest.java`
- [X] T036 [P] [US3] Integration test for the full archive → trash → restore → permanent-delete lifecycle in `src/test/java/com/quicknote/api/note/NoteTrashLifecycleIT.java`
- [X] T037 [P] [US3] Integration test for the 7-day trash auto-purge scheduled job (FR-014), using a fixture note with a backdated `trashed_at`, in `src/test/java/com/quicknote/api/note/TrashPurgeSchedulerIT.java`

### Implementation for User Story 3

- [X] T038 [US3] Implement archive/unarchive service + resource methods with the FR-010 trashed-conflict check in `src/main/java/com/quicknote/api/note/NoteService.java` and `src/main/java/com/quicknote/api/note/NoteResource.java` (depends on T026)
- [X] T039 [US3] Implement the restore service + resource method, preserving prior `archived` state (FR-011) in `src/main/java/com/quicknote/api/note/NoteService.java` and `src/main/java/com/quicknote/api/note/NoteResource.java` (depends on T038)
- [X] T040 [US3] Implement the permanent-delete service + resource method with the FR-004 409 guard in `src/main/java/com/quicknote/api/note/NoteService.java` and `src/main/java/com/quicknote/api/note/NoteResource.java` (depends on T039)
- [X] T041 [US3] Implement `view=active|archived|trashed` filtering in `NoteService.list` and the `NoteResource` query parameter in `src/main/java/com/quicknote/api/note/NoteService.java` and `src/main/java/com/quicknote/api/note/NoteResource.java` (depends on T040)
- [X] T042 [P] [US3] Implement the `TrashPurgeScheduler` (`@Scheduled` daily job, FR-014, research.md §8) in `src/main/java/com/quicknote/api/note/TrashPurgeScheduler.java` (depends on T010)

**Checkpoint**: T032–T037 pass. Full note lifecycle (active/archived/trashed, with auto-purge) works on top of US1+US2.

---

## Phase 6: User Story 4 - Organize with Labels (Priority: P3)

**Goal**: A user can create, rename, and delete labels; apply/remove them on notes; and filter notes by label.

**Independent Test**: Create a label, apply it to notes, filter by it, then delete it and confirm notes survive unaffected — independent of checklists.

### Tests for User Story 4 (MANDATORY — TDD)

- [X] T043 [P] [US4] Contract test for `POST /labels`, including 400/409 duplicate-name (FR-021) in `src/test/java/com/quicknote/api/label/CreateLabelContractTest.java`
- [X] T044 [P] [US4] Contract test for `GET /labels` paginated list in `src/test/java/com/quicknote/api/label/ListLabelsContractTest.java`
- [X] T045 [P] [US4] Contract test for `PATCH /labels/{labelId}` rename (including 409 duplicate) and `DELETE /labels/{labelId}` in `src/test/java/com/quicknote/api/label/UpdateDeleteLabelContractTest.java`
- [X] T046 [P] [US4] Contract test for `PUT`/`DELETE /notes/{noteId}/labels/{labelId}` apply/remove (idempotent apply) in `src/test/java/com/quicknote/api/note/NoteLabelAssociationContractTest.java`
- [X] T047 [P] [US4] Contract test for `GET /notes?label={labelId}` filter (FR-020) in `src/test/java/com/quicknote/api/note/NoteLabelFilterContractTest.java`
- [X] T048 [P] [US4] Integration test for label lifecycle, including cascade-removal from notes on label delete without deleting the notes (FR-018), in `src/test/java/com/quicknote/api/label/LabelLifecycleIT.java`

### Implementation for User Story 4

- [X] T049 [P] [US4] Create `Label` JPA entity + `LabelRepository` (owner-scoped, case-insensitive unique per FR-021) in `src/main/java/com/quicknote/api/label/Label.java` and `src/main/java/com/quicknote/api/label/LabelRepository.java`
- [X] T050 [P] [US4] Create the `NoteLabel` join entity + query helpers in `src/main/java/com/quicknote/api/note/NoteLabel.java`
- [X] T051 [P] [US4] Create `LabelCreateRequest`, `LabelUpdateRequest`, `Label`, `LabelPage` DTOs in `src/main/java/com/quicknote/api/label/dto/`
- [X] T052 [P] [US4] Implement `LabelService` (create/rename/delete with uniqueness enforcement) and `LabelResource` in `src/main/java/com/quicknote/api/label/LabelService.java` and `src/main/java/com/quicknote/api/label/LabelResource.java` (depends on T049, T051)
- [X] T053 [P] [US4] Implement note-label association service methods (apply/remove, idempotent) and the `NoteResource` endpoints in `src/main/java/com/quicknote/api/note/NoteService.java` and `src/main/java/com/quicknote/api/note/NoteResource.java` (depends on T050)
- [X] T054 [US4] Extend `NoteMapper` to populate `labelIds` and implement the label-filter query in `NoteService.list` in `src/main/java/com/quicknote/api/note/NoteMapper.java` and `src/main/java/com/quicknote/api/note/NoteService.java` (depends on T052, T053)

**Checkpoint**: T043–T048 pass. Labels fully organize notes on top of US1–US3.

---

## Phase 7: User Story 5 - Checklist Notes (Priority: P3)

**Goal**: A user can create checklist-style notes and add, edit, check/uncheck, and remove individual items.

**Independent Test**: Create a checklist note, add items, check/uncheck and remove items, and confirm state persists — independent of labels/archive/trash.

### Tests for User Story 5 (MANDATORY — TDD)

- [X] T055 [P] [US5] Contract test for `POST /notes` with a non-empty `items[]` creating a checklist note (FR-022) in `src/test/java/com/quicknote/api/note/CreateChecklistNoteContractTest.java`
- [X] T056 [P] [US5] Contract test for `POST /notes/{noteId}/items` add item (FR-023) in `src/test/java/com/quicknote/api/checklist/AddChecklistItemContractTest.java`
- [X] T057 [P] [US5] Contract test for `PATCH /notes/{noteId}/items/{itemId}` text/checked update (FR-024, FR-025) in `src/test/java/com/quicknote/api/checklist/UpdateChecklistItemContractTest.java`
- [X] T058 [P] [US5] Contract test for `DELETE /notes/{noteId}/items/{itemId}` remove item (FR-023) in `src/test/java/com/quicknote/api/checklist/DeleteChecklistItemContractTest.java`
- [X] T059 [P] [US5] Integration test for the checklist note lifecycle (create with items, add, check/uncheck, remove) in `src/test/java/com/quicknote/api/checklist/ChecklistItemLifecycleIT.java`

### Implementation for User Story 5

- [X] T060 [P] [US5] Create `ChecklistItem` JPA entity + repository (ordered by `position`) in `src/main/java/com/quicknote/api/checklist/ChecklistItem.java` and `src/main/java/com/quicknote/api/checklist/ChecklistItemRepository.java`
- [X] T061 [US5] Wire checklist item creation into `NoteService.create` and item serialization into `NoteMapper` in `src/main/java/com/quicknote/api/note/NoteService.java` and `src/main/java/com/quicknote/api/note/NoteMapper.java` (depends on T060)
- [X] T062 [US5] Implement the add-item service + resource method (auto-incrementing `position`) in `src/main/java/com/quicknote/api/note/NoteService.java` and `src/main/java/com/quicknote/api/note/NoteResource.java` (depends on T061)
- [X] T063 [US5] Implement the update-item (text/checked) service + resource method in `src/main/java/com/quicknote/api/note/NoteService.java` and `src/main/java/com/quicknote/api/note/NoteResource.java` (depends on T062)
- [X] T064 [US5] Implement the remove-item service + resource method in `src/main/java/com/quicknote/api/note/NoteService.java` and `src/main/java/com/quicknote/api/note/NoteResource.java` (depends on T063)

**Checkpoint**: T055–T059 pass. Checklist notes work end-to-end on top of US1–US4.

---

## Phase 8: User Story 6 - Find Notes via Search (Priority: P4)

**Goal**: A user can search their notes by keyword across title and content, excluding trashed notes.

**Independent Test**: Create notes with distinct titles/content and issue search queries that should and should not match.

### Tests for User Story 6 (MANDATORY — TDD)

- [X] T065 [P] [US6] Contract test for `GET /notes?search=` matches / no-match / excludes-trashed (FR-026) in `src/test/java/com/quicknote/api/note/SearchNotesContractTest.java`
- [X] T066 [P] [US6] Integration test for full-text search relevance against real PostgreSQL `tsvector`/`tsquery` in `src/test/java/com/quicknote/api/note/NoteSearchIT.java`

### Implementation for User Story 6

- [X] T067 [US6] Implement the `search_vector`/`tsquery` search method in `src/main/java/com/quicknote/api/note/NoteRepository.java` (depends on T010; uses the GIN index from T007)
- [X] T068 [US6] Wire the `search` query parameter into `NoteService.list` and `NoteResource` in `src/main/java/com/quicknote/api/note/NoteService.java` and `src/main/java/com/quicknote/api/note/NoteResource.java` (depends on T067)

**Checkpoint**: T065–T066 pass. All 6 user stories are independently functional.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories, and final validation against the spec/plan/constitution

- [X] T069 [P] Integration test confirming 500 responses never leak stack traces, SQL, or internal hostnames in the error envelope (constitution Principle V) in `src/test/java/com/quicknote/api/common/exception/ErrorEnvelopeLeakageTest.java`
- [X] T070 [P] Write `README.md` at repository root summarizing setup and linking to `specs/001-notes-api/quickstart.md`
- [X] T071 [P] Add Micrometer counters/timers for note create/search operations (constitution Principle V, "SHOULD expose metrics") in `src/main/java/com/quicknote/api/note/NoteService.java`
- [X] T072 Execute the full `quickstart.md` validation walkthrough end-to-end against the `docker-compose` environment and record results
- [X] T073 Run the full automated suite (`./mvnw verify`) and resolve any regressions before considering the feature done

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Stories (Phase 3–8)**: All depend on Foundational completion
  - US1 (P1) has no dependency on other stories
  - US2 (P2) and US3 (P2) both build directly on US1's `Note` entity/service/resource (T025/T026) — implement after US1
  - US4 (P3) builds on US1's `Note`/`NoteMapper`/`NoteResource` (adds `Label`/`NoteLabel`) — implement after US1 (and, per this plan, after US2/US3 to avoid touching `NoteService.java`/`NoteResource.java` concurrently with those stories, though there is no hard functional dependency on US2/US3)
  - US5 (P3) builds on US1 similarly (adds `ChecklistItem`) — same sequencing note as US4
  - US6 (P4) builds on US1's `NoteRepository`/`NoteService`/`NoteResource` and the search index from Foundational T007
- **Polish (Phase 9)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: No dependencies on other stories — the foundation every later story extends
- **US2 (P2)**: Extends US1's note CRUD (pin/color); no dependency on US3–US6
- **US3 (P2)**: Extends US1's note CRUD (lifecycle states); no dependency on US2, US4–US6
- **US4 (P3)**: Extends US1's note model (labels); no functional dependency on US2/US3/US5/US6
- **US5 (P3)**: Extends US1's note model (checklist items); no functional dependency on US2/US3/US4/US6
- **US6 (P4)**: Extends US1's note listing (search); no functional dependency on US2–US5

### Within Each User Story

- Tests MUST be written and confirmed FAILING before implementation (mandatory TDD, not optional)
- Entities/DTOs before services; services before resources
- Story complete and its checkpoint tests green before moving to the next priority

### Parallel Opportunities

- All Setup tasks marked `[P]` (T002–T006) can run in parallel once T001 exists
- Foundational tasks marked `[P]` (T007, T008, T009, T011, T013, T014, T016) can run in parallel; T010, T012, T015 depend on those
- All test tasks within a user story phase (marked `[P]`) can run in parallel with each other
- Within US4, `[P]` implementation tasks T049–T053 touch disjoint files and can run in parallel; T054 depends on T052 and T053
- Across stories: once Foundational is done, US1 must land first (everything else edits `NoteService.java`/`NoteResource.java`, which US1 creates); after that, US2, US3, US4, and US5 each touch those same shared files, so treat them as sequential per-story batches even though the *stories themselves* have no functional dependency on each other — a second developer picking up, say, US4 before US2 is merged would hit merge conflicts, not a broken feature

---

## Parallel Example: User Story 1

```bash
# Launch all US1 test tasks together (T017-T022 — different files, no interdependency):
Task: "Contract test for POST /notes in src/test/java/com/quicknote/api/note/CreateNoteContractTest.java"
Task: "Contract test for GET /notes in src/test/java/com/quicknote/api/note/ListNotesContractTest.java"
Task: "Contract test for GET /notes/{noteId} in src/test/java/com/quicknote/api/note/GetNoteContractTest.java"
Task: "Contract test for PATCH /notes/{noteId} in src/test/java/com/quicknote/api/note/UpdateNoteContractTest.java"
Task: "Contract test for POST /notes/{noteId}/trash in src/test/java/com/quicknote/api/note/TrashNoteContractTest.java"
Task: "Integration test for note lifecycle in src/test/java/com/quicknote/api/note/NoteLifecycleIT.java"

# Confirm all six FAIL, then implement sequentially (T023 -> T024 -> T025 -> T026),
# since they build on the same DTOs/mapper/service/resource in order.
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Run T017–T022 green, then the relevant slice of `quickstart.md` §4 ("Capture a note")
5. Deploy/demo if ready — this is a usable note-capture API

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. Add US1 → validate independently → MVP
3. Add US2 (pin/color) → validate independently
4. Add US3 (archive/trash lifecycle + auto-purge) → validate independently
5. Add US4 (labels) → validate independently
6. Add US5 (checklists) → validate independently
7. Add US6 (search) → validate independently
8. Polish → final full-suite validation

### Single-Developer Sequential Strategy

Given US2–US6 all extend the `Note`/`NoteService`/`NoteResource` files US1 creates (see "Parallel Opportunities" above), this feature is best implemented by one contributor moving through phases in priority order rather than parallelized across developers — parallelism is available *within* each story's test-writing step, not across stories.

---

## Notes

- `[P]` tasks touch different files with no unfinished prerequisite
- `[Story]` labels map every user-story-phase task to US1–US6 for traceability back to spec.md
- Every implementation task is preceded by the test task(s) that must fail first — this ordering is mandatory here, not a style preference
- Commit after each task or logical group
- Stop at any checkpoint to validate a story independently, including running the corresponding section of `quickstart.md`
- Avoid: vague tasks, unnecessary same-file [P] conflicts, skipping the red step of TDD
