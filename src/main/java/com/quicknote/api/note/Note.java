package com.quicknote.api.note;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.quicknote.api.checklist.ChecklistItem;
import com.quicknote.api.label.Label;

/**
 * A single captured piece of content owned by exactly one user (data-model.md).
 *
 * <p>The {@code search_vector} column is generated and maintained by PostgreSQL
 * and is deliberately not mapped here; it is read only by the full-text search
 * query in {@link NoteRepository}.
 */
@Entity
@Table(name = "note")
public class Note {

    @Id
    @GeneratedValue
    private UUID id;

    /** Keycloak JWT {@code sub} claim of the owner; every query is scoped by it. */
    @Column(name = "owner_id", nullable = false, length = 255)
    private String ownerId;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private NoteColor color;

    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false)
    private boolean archived;

    @Column(nullable = false)
    private boolean trashed;

    /** Set when the note enters the trash; drives the 7-day purge (FR-014). */
    @Column(name = "trashed_at")
    private Instant trashedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Labels applied to this note, over the {@code note_label} join table.
     *
     * <p>Mapped as a plain many-to-many rather than through an explicit join
     * entity: the association carries no attributes of its own, so a dedicated
     * entity would add a class without adding information.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "note_label",
            joinColumns = @JoinColumn(name = "note_id"),
            inverseJoinColumns = @JoinColumn(name = "label_id"))
    private Set<Label> labels = new LinkedHashSet<>();

    /**
     * Checklist lines, in display order. Owned outright by the note: removing an
     * item from this list deletes it, and deleting the note deletes them all.
     */
    @OneToMany(mappedBy = "note", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<ChecklistItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        // Last-write-wins: every write refreshes the timestamp and no version
        // conflict is ever surfaced to the client (research.md 7).
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public NoteColor getColor() {
        return color;
    }

    public void setColor(NoteColor color) {
        this.color = color;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public boolean isTrashed() {
        return trashed;
    }

    public void setTrashed(boolean trashed) {
        this.trashed = trashed;
    }

    public Instant getTrashedAt() {
        return trashedAt;
    }

    public void setTrashedAt(Instant trashedAt) {
        this.trashedAt = trashedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<Label> getLabels() {
        return labels;
    }

    public List<ChecklistItem> getItems() {
        return items;
    }

    /** Appends an item at the end of the list and keeps both sides in sync. */
    public void addItem(ChecklistItem item) {
        item.setNote(this);
        item.setPosition(items.stream().mapToInt(ChecklistItem::getPosition).max().orElse(-1) + 1);
        items.add(item);
    }
}
