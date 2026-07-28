# Data Model: Notes Management API

**Phase 1 output** for [plan.md](plan.md). Derived from the spec's Key Entities section and Functional Requirements. Persistence choices (PostgreSQL, Hibernate ORM/Panache, Flyway) per [research.md](research.md).

## Entity Relationship Overview

```text
User (external identity, JWT subject — not a persisted entity of this feature)
  │
  ├── 1..* Note
  │            │
  │            ├── 0..* ChecklistItem   (owned exclusively by one Note)
  │            └── 0..* NoteLabel       (join of Note ↔ Label)
  │
  └── 1..* Label
               └── 0..* NoteLabel
```

- A `User` is not a table owned by this feature (FR-028 / Assumption A3) — it is represented only as the `owner_id` (JWT `sub` claim value) stored on `Note` and `Label` rows.
- `Note` ↔ `Label` is many-to-many via a join table (`note_label`), scoped implicitly to the same owner on both sides (enforced at the service layer, not by a cross-owner constraint, since both rows always belong to the same authenticated user by construction).
- `ChecklistItem` has a strict one-to-many, composition relationship with `Note` (cascade delete; cannot exist without its parent note).

## Entities

### Note

Represents a single captured piece of content (plain text or checklist), owned by exactly one user.

| Field | Type | Constraints / Notes |
|---|---|---|
| `id` | UUID | Primary key, generated |
| `owner_id` | String | Required. Keycloak-issued JWT `sub` claim of the owning user. Indexed (all list/search/filter queries scope by this). |
| `title` | String (nullable) | Optional (FR-001). Reasonable max length enforced (e.g., 500 chars) to bound storage/index size — not specified by the spec, chosen defensively. |
| `content` | Text (nullable) | Optional (FR-001). Plain text body; only meaningful for non-checklist notes but not schema-enforced as mutually exclusive with checklist items (a note may have both, per spec's "checklist-style notes" being a variant, not a hard type split). |
| `color` | Enum (nullable) | One of a fixed palette (Assumption A4); `null`/unset represents the default/no-color state (FR-005). |
| `pinned` | Boolean | Default `false` (FR-006). |
| `archived` | Boolean | Default `false` (FR-007). |
| `trashed` | Boolean | Default `false` (FR-011). |
| `trashed_at` | Timestamp (nullable) | Set when `trashed` transitions to `true`; used by the 7-day auto-purge job (FR-014); cleared on restore. |
| `created_at` | Timestamp | Set on insert. |
| `updated_at` | Timestamp | Refreshed on every write (last-write-wins per research.md §7). |
| `search_vector` | `tsvector` (generated) | PostgreSQL generated column (or trigger-maintained) combining `title` + `content`, GIN-indexed, used by the search endpoint (research.md §6). Not exposed in the API contract. |

**Validation rules**:
- A note MUST have at least one of: non-empty `title`, non-empty `content`, or at least one checklist item at creation time (FR-001, Edge Cases). Enforced at the service layer since it spans multiple fields/child entities.
- `color`, if present, MUST be one of the supported enum values (FR-005).
- `archived = true` and `trashed = true` MUST NOT both be true simultaneously — a note cannot be archived directly from trash (FR-010); the service layer rejects an archive request while `trashed = true`.

**State transitions** (`archived` / `trashed`, both booleans but treated as one lifecycle state machine at the service layer):

```text
ACTIVE ──archive──▶ ARCHIVED ──unarchive──▶ ACTIVE
ACTIVE ──trash────▶ TRASHED  ──restore────▶ ACTIVE
ARCHIVED ──trash──▶ TRASHED  ──restore────▶ ARCHIVED   (prior archived state is preserved)
TRASHED ──(no direct archive transition — must restore first, FR-010)
TRASHED ──purge (7 days) or explicit permanent delete──▶ (row deleted, FR-013/FR-014)
```

To preserve "restore returns to prior active/archived organization" (FR-011), the note retains its `archived` flag value while `trashed = true`; restore simply clears `trashed`/`trashed_at`, leaving whatever `archived` value it already had.

### ChecklistItem

Represents a single checkable line belonging to exactly one note.

| Field | Type | Constraints / Notes |
|---|---|---|
| `id` | UUID | Primary key, generated |
| `note_id` | UUID | Required. Foreign key to `Note`, `ON DELETE CASCADE`. |
| `text` | String | Required, non-empty (an item with no text carries no information, consistent with the note-level empty-content rule). |
| `checked` | Boolean | Default `false` (FR-022, FR-024). |
| `position` | Integer | Required. Defines display order within the note (FR-022 "ordered collection"); unique within a given `note_id`. |

**Validation rules**:
- `text` MUST be non-empty (FR-023, FR-025).
- `position` is assigned automatically (append = current max + 1) unless the client specifies reordering; reordering support is limited to what FR-022–FR-025 require (add/remove/edit text/toggle checked) — no dedicated reorder endpoint is in scope for this feature.

### Label

A user-defined organizational tag.

| Field | Type | Constraints / Notes |
|---|---|---|
| `id` | UUID | Primary key, generated |
| `owner_id` | String | Required. Keycloak-issued JWT `sub` claim of the owning user. |
| `name` | String | Required, non-empty. Unique per `(owner_id, lower(name))` (FR-021 — case-insensitive uniqueness). |
| `created_at` | Timestamp | Set on insert. |

**Validation rules**:
- `name` MUST be unique per owner, case-insensitively (FR-021) — enforced via a unique index on `(owner_id, lower(name))`.
- Deleting a `Label` cascades only to its `note_label` join rows, never to the `Note` rows themselves (FR-018).

### NoteLabel (join entity)

Associates notes with labels, many-to-many, always within a single owner.

| Field | Type | Constraints / Notes |
|---|---|---|
| `note_id` | UUID | Foreign key to `Note`, `ON DELETE CASCADE`. |
| `label_id` | UUID | Foreign key to `Label`, `ON DELETE CASCADE`. |

Composite primary key `(note_id, label_id)` — applying the same label twice to a note is a no-op, not an error (idempotent apply).

## Indexes (beyond primary/foreign keys)

- `note (owner_id, archived, trashed)` — supports the default active-notes listing filter (FR-008) and the separate archived/trashed views (FR-009, FR-012).
- `note (owner_id, trashed, trashed_at)` — supports the 7-day auto-purge scheduled job (FR-014).
- `note USING GIN (search_vector)` — supports full-text search (FR-026).
- `label (owner_id, lower(name))` UNIQUE — supports FR-021 uniqueness and label lookup by name.
- `checklist_item (note_id, position)` — supports ordered retrieval of a note's items.
- `note_label (label_id)` — supports "list notes filtered by label" (FR-020) and cascading label deletion (FR-018).

## Notes on Scope

No `User` table is created by this feature (Assumption A3 / FR-028); `owner_id` is an opaque string sourced from the Keycloak-validated JWT's `sub` claim and is not a foreign key to a local table. RBAC (the `user` realm role required to call the API at all) is enforced entirely within Keycloak and at the API's authorization layer (research.md §1b) — it has no representation in this data model, since no role/permission data is persisted locally.
