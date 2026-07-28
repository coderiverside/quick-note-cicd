package com.quicknote.api.label;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;

/**
 * Data access for {@link Label}. Every method is owner-scoped for the same reason
 * as {@code NoteRepository}: a lookup must not be able to reach another user's row.
 */
@ApplicationScoped
public class LabelRepository implements PanacheRepositoryBase<Label, UUID> {

    public Optional<Label> findOwnedById(String ownerId, UUID id) {
        return find("id = :id and ownerId = :ownerId", Parameters.with("id", id).and("ownerId", ownerId))
                .firstResultOptional();
    }

    public List<Label> listOwned(String ownerId, int page, int size) {
        return find("ownerId = ?1", Sort.ascending("name"), ownerId).page(Page.of(page, size)).list();
    }

    public long countOwned(String ownerId) {
        return count("ownerId = ?1", ownerId);
    }

    /**
     * Case-insensitive name lookup backing FR-021's uniqueness rule. Mirrors the
     * {@code lower(name)} unique index so the check and the constraint agree.
     */
    public Optional<Label> findOwnedByName(String ownerId, String name) {
        return find("ownerId = :ownerId and lower(name) = :name",
                Parameters.with("ownerId", ownerId).and("name", name.toLowerCase(Locale.ROOT)))
                .firstResultOptional();
    }
}
