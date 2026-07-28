# Feature Specification: Notes Management API

**Feature Branch**: `001-notes-api`

**Created**: 2026-07-25

**Status**: Draft

**Input**: User description: "Build a REST API for a note-taking service similar to Google Keep. A service where an authenticated user can create and manage personal notes. Each note has a title, text content, color, pinned status, archived status, and trashed status. Users can organize notes using custom labels. Users can create checklist-style notes with checkable items. WHY: To allow users to quickly capture, organize, find, and manage ideas and tasks at scale."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Capture a Note (Priority: P1)

An authenticated user quickly creates a new note with a title and/or text content so they don't lose an idea, then can view, edit, or delete that note later.

**Why this priority**: This is the core value proposition ("quickly capture ideas") and the minimum required for the product to be usable at all. Nothing else in the feature has value without this.

**Independent Test**: Can be fully tested by creating a note via the API, retrieving it, editing its content, and deleting it — independent of labels, checklists, or organization features.

**Acceptance Scenarios**:

1. **Given** an authenticated user with no notes, **When** they create a note with a title and text content, **Then** the note is saved and appears in their list of notes.
2. **Given** a user has an existing note, **When** they update its title or text content, **Then** the retrieved note reflects the updated content.
3. **Given** a user has an existing note, **When** they delete it, **Then** it no longer appears in their active notes.
4. **Given** a user is authenticated, **When** they attempt to view, edit, or delete a note owned by a different user, **Then** the request is rejected and no note data is returned.

---

### User Story 2 - Organize and Prioritize Notes (Priority: P2)

A user pins their most important notes so they surface first, and assigns colors to notes to visually group or prioritize them at a glance.

**Why this priority**: Once a user is capturing multiple notes, surfacing the important ones and visually distinguishing them is the next highest-value capability for staying organized.

**Independent Test**: Can be fully tested by creating notes, pinning/unpinning them, and assigning colors, then verifying pinned notes are distinguishable and colors persist — independent of labels or checklists.

**Acceptance Scenarios**:

1. **Given** a user has multiple notes, **When** they pin one note, **Then** that note is returned as pinned and can be distinguished from unpinned notes in listings.
2. **Given** a pinned note, **When** the user unpins it, **Then** it is returned as unpinned.
3. **Given** an existing note, **When** the user assigns it a color from the supported palette, **Then** the note's color is persisted and returned on subsequent retrieval.

---

### User Story 3 - Archive and Trash Notes (Priority: P2)

A user archives notes they no longer need to see regularly but want to keep, and trashes notes they intend to remove, with the ability to restore a trashed note before it's gone for good.

**Why this priority**: Keeping the primary note list uncluttered while avoiding accidental permanent data loss is essential for a note-taking tool used "at scale," directly after basic capture and organization.

**Independent Test**: Can be fully tested by archiving a note (and confirming it's excluded from the default view), unarchiving it, trashing it, restoring it, and permanently deleting it — independent of labels or checklists.

**Acceptance Scenarios**:

1. **Given** an active note, **When** the user archives it, **Then** it no longer appears in the default (active) notes view but remains retrievable in the archived view.
2. **Given** an archived note, **When** the user unarchives it, **Then** it reappears in the default active notes view.
3. **Given** an active note, **When** the user moves it to trash, **Then** it no longer appears in the default active or archived views but appears in the trash view.
4. **Given** a trashed note, **When** the user restores it, **Then** it returns to the active notes view with its prior organization (labels, color, pin status) intact.
5. **Given** a trashed note, **When** the user permanently deletes it, **Then** it is no longer retrievable through any view.

---

### User Story 4 - Organize with Labels (Priority: P3)

A user creates custom labels (e.g., "Work", "Recipes"), applies them to notes, and filters their notes by label to find related notes together.

**Why this priority**: Labels add a structured organization layer valuable at higher note volumes, but a user can be productive with pinning/color/archive alone first, so this ranks below core lifecycle management.

**Independent Test**: Can be fully tested by creating a label, applying it to one or more notes, filtering notes by that label, and removing/deleting the label — independent of checklist functionality.

**Acceptance Scenarios**:

1. **Given** an authenticated user, **When** they create a new label with a name, **Then** the label is available to apply to their notes.
2. **Given** an existing note and an existing label, **When** the user applies the label to the note, **Then** the note is returned with that label included in its label list.
3. **Given** a note with an applied label, **When** the user removes the label from the note, **Then** the note is returned without that label, and the label itself still exists for use on other notes.
4. **Given** a user has notes with various labels, **When** they request notes filtered by a specific label, **Then** only notes with that label are returned.
5. **Given** a label applied to multiple notes, **When** the user deletes the label, **Then** the label is removed from all notes it was applied to and no longer available for use, but the notes themselves remain intact.

---

### User Story 5 - Checklist Notes (Priority: P3)

A user creates a checklist-style note (e.g., a shopping list or task list), adds individual checkable items to it, and checks/unchecks items as they complete them.

**Why this priority**: Checklist notes are a distinct, valuable note type ("manage ... tasks") but are additive to the core note model, so they're prioritized after the fundamental note lifecycle and organization features.

**Independent Test**: Can be fully tested by creating a checklist note, adding items, checking/unchecking items, removing items, and confirming state persists — independent of labels or archive/trash state.

**Acceptance Scenarios**:

1. **Given** an authenticated user, **When** they create a checklist-style note with one or more items, **Then** the note is saved with each item in an unchecked state by default.
2. **Given** an existing checklist note, **When** the user adds a new item to it, **Then** the item appears in the note's item list in an unchecked state.
3. **Given** a checklist item, **When** the user checks it off, **Then** it is returned as checked; **when** they uncheck it, **then** it is returned as unchecked.
4. **Given** a checklist note, **When** the user removes an item, **Then** that item no longer appears in the note.

---

### User Story 6 - Find Notes via Search (Priority: P4)

A user searches across their notes by keyword to quickly locate a specific note among many, without manually scanning lists.

**Why this priority**: Search becomes valuable once a user has accumulated enough notes that browsing is inefficient; it depends on note capture already existing and is the least critical for an initial usable product.

**Independent Test**: Can be fully tested by creating several notes with distinct titles/content and issuing search queries that should and should not match them.

**Acceptance Scenarios**:

1. **Given** a user has multiple notes, **When** they search using a keyword that matches a note's title or text content, **Then** that note is included in the results.
2. **Given** a user has multiple notes, **When** they search using a keyword that matches no note, **Then** an empty result set is returned.
3. **Given** a user has trashed notes, **When** they perform a standard search, **Then** trashed notes are excluded from the results.

---

### Edge Cases

- What happens when a user attempts to create a note with no title, no text content, and (if a checklist) no items? The system MUST reject entirely empty notes since they carry no information to capture.
- How does the system handle deleting a label that is currently applied to hundreds of notes? All associations MUST be removed without deleting the underlying notes.
- What happens when a user tries to archive a note that is currently in the trash? Archiving MUST NOT be permitted directly from trash; the note MUST be restored first.
- What happens when a user tries to pin a note that is archived or trashed? Pin status MAY be set independently, but pinned notes that are archived or trashed MUST NOT appear in the default pinned view until restored/unarchived.
- What happens if a user restores a note whose only label was deleted while it was trashed? The note is restored without that label; no error occurs.
- How does the system handle a checklist note with zero remaining items after all items are removed? The note remains a valid checklist note with an empty item list.
- What happens when two updates to the same note are submitted concurrently? The system MUST apply a consistent conflict-resolution behavior (e.g., last-write-wins) so no request errors out unexpectedly and the note is left in a valid state.
- What happens when a user requests a note, label, or checklist item that does not exist or does not belong to them? The system MUST return a not-found response without revealing whether the resource exists for another user.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow an authenticated user to create a note with an optional title and optional text content, rejecting creation only when both are empty and no checklist items are provided.
- **FR-002**: System MUST allow a user to retrieve a single note they own, and to list their own notes.
- **FR-003**: System MUST allow a user to update the title and/or text content of a note they own.
- **FR-004**: System MUST allow a user to permanently delete a note they own once it has been trashed (see FR-011).
- **FR-005**: System MUST allow a user to assign one color, from a predefined set of supported colors, to a note they own, and to clear it back to a default/no-color state.
- **FR-006**: System MUST allow a user to pin and unpin a note they own; pinned notes MUST be distinguishable from unpinned notes when notes are listed.
- **FR-007**: System MUST allow a user to archive and unarchive a note they own.
- **FR-008**: The default (active) notes listing MUST exclude archived and trashed notes.
- **FR-009**: System MUST provide a way to list a user's archived notes separately from their active notes.
- **FR-010**: System MUST NOT allow a note to be archived directly while it is trashed; it MUST be restored from trash first.
- **FR-011**: System MUST allow a user to move a note they own to trash, and to restore a trashed note back to its prior active/archived organization (labels, color, pin status preserved).
- **FR-012**: System MUST provide a way to list a user's trashed notes separately from active and archived notes.
- **FR-013**: Trashed notes that are permanently deleted (FR-004) MUST no longer be retrievable through any listing or lookup.
- **FR-014**: Trashed notes MUST be automatically and permanently deleted after remaining in trash for 7 days, matching the reference product's retention behavior. *(See Assumption A1 — confirm this is acceptable or provide an alternate retention period.)*
- **FR-015**: System MUST prevent a user from viewing, modifying, or deleting another user's notes, labels, or checklist items, returning a not-found response rather than confirming existence.
- **FR-016**: System MUST allow a user to create a custom label with a name, scoped to that user's account.
- **FR-017**: System MUST allow a user to rename or delete a label they own.
- **FR-018**: Deleting a label MUST remove it from every note it was applied to without deleting those notes.
- **FR-019**: System MUST allow a user to apply one or more of their existing labels to a note they own, and to remove a label from a note without deleting the label itself.
- **FR-020**: System MUST allow a user to list/filter their notes by a specific label.
- **FR-021**: System MUST enforce that a given label name is unique per user (case-insensitive), rejecting duplicate creation attempts.
- **FR-022**: System MUST allow a user to create a checklist-style note containing zero or more checkable items, each with its own text.
- **FR-023**: System MUST allow a user to add a new item to an existing checklist note, and to remove an existing item from it.
- **FR-024**: System MUST allow a user to independently mark each checklist item as checked or unchecked, and this state MUST persist and be retrievable.
- **FR-025**: System MUST allow a user to update the text of an individual checklist item.
- **FR-026**: System MUST allow a user to search their own notes by keyword, matching against note title and text content, excluding trashed notes from results by default.
- **FR-027**: All note-management operations in this feature apply strictly to notes owned by the authenticated user; sharing or collaborative access to another user's notes is out of scope for this feature. *(See Assumption A2.)*
- **FR-028**: This feature assumes the authenticated user's identity is established by an existing authentication mechanism external to note management; user registration, login, and session/token issuance are out of scope for this feature. *(See Assumption A3.)*

### Key Entities

- **Note**: A single captured piece of content owned by exactly one user. Attributes: title (optional text), text content (optional text), color (one of a predefined palette, or none), pinned status (boolean), archived status (boolean), trashed status (boolean), created and last-updated timestamps, an ordered collection of checklist items (empty for plain text notes), and zero or more associated labels. A note is either a plain text note or a checklist note based on whether it has checklist items.
- **Checklist Item**: A single checkable line belonging to exactly one note. Attributes: text, checked status (boolean), and its position/order within the note's item list.
- **Label**: A user-defined organizational tag owned by exactly one user. Attributes: name (unique per user). Many notes may share a label, and a note may have many labels.
- **User**: The authenticated owner of notes and labels. This feature treats the user's identity as given by an external authentication system; only the association between a user and their own notes/labels/checklist items is in scope here.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can capture a new note (from submitting content to it being retrievable) in under 1 second under normal load.
- **SC-002**: A user with 10,000 or more notes can retrieve their active notes list or search results without any noticeable increase in wait time compared to a user with a handful of notes.
- **SC-003**: 100% of attempts by one user to access, modify, or delete another user's notes, labels, or checklist items are blocked.
- **SC-004**: A user can locate a specific note among 500+ notes using search or label filtering in under 10 seconds.
- **SC-005**: A user can pin, color, archive, or trash a note and see that change reflected immediately on the next retrieval, 100% of the time.
- **SC-006**: A user can restore any trashed note within the 7-day retention window and recover it with all prior organization (color, pin, labels) intact, 100% of the time.
- **SC-007**: A user can create a checklist note and check off an item without any errors on first attempt.

## Assumptions

- **A1**: Trashed notes are automatically and permanently purged 7 days after being trashed, mirroring the reference product (Google Keep). If a different retention period or a manual-only purge model is required, this should be revised (FR-014).
- **A2**: Notes are strictly personal/private to their owning user for this feature — no sharing or multi-user collaboration on a single note is included, consistent with the feature description's "personal notes" framing (FR-027). Collaboration could be introduced as a future feature.
- **A3**: User authentication (registration, login, credential/session management) is provided by an existing system outside this feature's scope; this feature only requires that each request carries a verifiable authenticated user identity (FR-028).
- **A4**: The supported note color palette is a small, fixed, predefined set of colors (in the style of the reference product), rather than arbitrary user-supplied colors.
- **A5**: Notes, once permanently deleted from trash, are not recoverable through any user-facing capability in this feature.
- **A6**: There is no enforced maximum on the number of notes, labels, or checklist items per user beyond what is needed to meet the performance expectations in Success Criteria.
