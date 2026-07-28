package com.quicknote.api.label;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** A user-defined organisational tag (data-model.md). */
@Entity
@Table(name = "label")
public class Label {

    @Id
    @GeneratedValue
    private UUID id;

    /** Keycloak JWT {@code sub} claim of the owner; label vocabularies are per user. */
    @Column(name = "owner_id", nullable = false, length = 255)
    private String ownerId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // Identity by primary key, so a Label behaves correctly inside the Set that
    // models a note's labels.
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Label label && id != null && id.equals(label.id);
    }

    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }
}
