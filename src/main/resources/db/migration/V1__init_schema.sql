-- Baseline schema for the Notes Management API (data-model.md).
--
-- No `user` table exists: ownership is the opaque Keycloak JWT `sub` claim
-- stored as owner_id, so it is deliberately not a foreign key (FR-028 / A3).

CREATE TABLE note (
    id              UUID PRIMARY KEY,
    owner_id        VARCHAR(255) NOT NULL,
    title           VARCHAR(500),
    content         TEXT,
    color           VARCHAR(32),
    pinned          BOOLEAN      NOT NULL DEFAULT FALSE,
    archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    trashed         BOOLEAN      NOT NULL DEFAULT FALSE,
    trashed_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    -- Maintained by PostgreSQL so it can never drift from title/content.
    -- Never exposed through the API contract; read only by the search query.
    search_vector   TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(title, '') || ' ' || coalesce(content, ''))
    ) STORED
);
-- Note: `archived` and `trashed` are intentionally independent. Trashing an
-- archived note keeps archived = true so that restoring it returns it to the
-- archive rather than the active list (FR-011). FR-010 ("cannot archive a
-- trashed note") constrains the *transition*, not the stored state, and so is
-- enforced in NoteService rather than by a CHECK constraint here.

CREATE TABLE checklist_item (
    id              UUID PRIMARY KEY,
    note_id         UUID         NOT NULL REFERENCES note (id) ON DELETE CASCADE,
    text            VARCHAR(1000) NOT NULL,
    checked         BOOLEAN      NOT NULL DEFAULT FALSE,
    -- "position" is a reserved word in PostgreSQL, hence the item_ prefix.
    item_position   INTEGER      NOT NULL,
    CONSTRAINT checklist_item_position_unique UNIQUE (note_id, item_position)
);

CREATE TABLE label (
    id              UUID PRIMARY KEY,
    owner_id        VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE TABLE note_label (
    note_id         UUID NOT NULL REFERENCES note (id) ON DELETE CASCADE,
    label_id        UUID NOT NULL REFERENCES label (id) ON DELETE CASCADE,
    PRIMARY KEY (note_id, label_id)
);

-- Default active listing (FR-008) plus the archived/trashed views (FR-009, FR-012).
CREATE INDEX idx_note_owner_views ON note (owner_id, archived, trashed);

-- Drives the 7-day trash auto-purge job (FR-014).
CREATE INDEX idx_note_owner_trashed_at ON note (owner_id, trashed, trashed_at);

-- Index-backed full-text search over title + content (FR-026).
CREATE INDEX idx_note_search_vector ON note USING GIN (search_vector);

-- Case-insensitive label-name uniqueness per owner (FR-021).
CREATE UNIQUE INDEX idx_label_owner_name_unique ON label (owner_id, lower(name));

-- Ordered retrieval of a note's checklist items.
CREATE INDEX idx_checklist_item_note_position ON checklist_item (note_id, item_position);

-- "List notes filtered by label" (FR-020) and cascading label deletion (FR-018).
CREATE INDEX idx_note_label_label ON note_label (label_id);
