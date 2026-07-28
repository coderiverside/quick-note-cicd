package com.quicknote.api.note;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Query;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;

/**
 * Data access for {@link Note}.
 *
 * <p>Every method takes an {@code ownerId} and filters on it. Ownership is never
 * taken from client input and never left to the caller to remember: a lookup for
 * another user's note simply returns empty, which the service layer turns into a
 * 404 (FR-015, research.md 2).
 */
@ApplicationScoped
public class NoteRepository implements PanacheRepositoryBase<Note, UUID> {

    /** Pinned notes first, then most recently updated. */
    private static final Sort DEFAULT_SORT = Sort.descending("pinned", "updatedAt");

    public Optional<Note> findOwnedById(String ownerId, UUID id) {
        return find("id = :id and ownerId = :ownerId", Parameters.with("id", id).and("ownerId", ownerId))
                .firstResultOptional();
    }

    public List<Note> listByView(String ownerId, NoteView view, UUID labelId, String searchTerm, int page, int size) {
        List<Note> notes = searchTerm != null
                ? searchPage(ownerId, view, labelId, searchTerm, page, size)
                : listPage(ownerId, view, labelId, page, size);

        initializeAssociations(notes);
        return notes;
    }

    public long countByView(String ownerId, NoteView view, UUID labelId, String searchTerm) {
        if (searchTerm != null) {
            return ((Number) searchQuery(countSelect(labelId), view, labelId, ownerId, searchTerm, false)
                    .getSingleResult()).longValue();
        }
        return labelId == null
                ? count(viewQuery(view), Parameters.with("ownerId", ownerId))
                : find(labelFilteredQuery(view), Parameters.with("ownerId", ownerId).and("labelId", labelId)).count();
    }

    private List<Note> listPage(String ownerId, NoteView view, UUID labelId, int page, int size) {
        return labelId == null
                ? find(viewQuery(view), DEFAULT_SORT, Parameters.with("ownerId", ownerId))
                        .page(Page.of(page, size))
                        .list()
                : find(labelFilteredQuery(view), Parameters.with("ownerId", ownerId).and("labelId", labelId))
                        .page(Page.of(page, size))
                        .list();
    }

    /**
     * Full-text search (FR-026).
     *
     * <p>Native SQL because the match is a PostgreSQL {@code tsvector} operator
     * with no JPQL equivalent. Using the generated, GIN-indexed column keeps this
     * an index lookup rather than a scan that grows with the user's note count
     * (research.md 6).
     */
    @SuppressWarnings("unchecked")
    private List<Note> searchPage(String ownerId, NoteView view, UUID labelId, String term, int page, int size) {
        Query query = searchQuery("select n.*", view, labelId, ownerId, term, true);
        query.setFirstResult(page * size);
        query.setMaxResults(size);
        return query.getResultList();
    }

    private Query searchQuery(String select, NoteView view, UUID labelId, String ownerId, String term,
            boolean asEntity) {
        String sql = select + " from note n "
                + (labelId == null ? "" : "join note_label nl on nl.note_id = n.id and nl.label_id = :labelId ")
                + "where n.owner_id = :ownerId and " + nativeViewPredicate(view)
                // plainto_tsquery treats the input as plain text, so arbitrary
                // punctuation from a user cannot become tsquery syntax.
                + " and n.search_vector @@ plainto_tsquery('english', :term)"
                + (asEntity ? " order by n.pinned desc, n.updated_at desc" : "");

        Query query = asEntity
                ? getEntityManager().createNativeQuery(sql, Note.class)
                : getEntityManager().createNativeQuery(sql);

        query.setParameter("ownerId", ownerId).setParameter("term", term);
        if (labelId != null) {
            query.setParameter("labelId", labelId);
        }
        return query;
    }

    private static String countSelect(UUID labelId) {
        return labelId == null ? "select count(*)" : "select count(distinct n.id)";
    }

    /**
     * Loads labels and checklist items for a whole page in two bounded queries.
     *
     * <p>Without this, rendering a page would lazily load each note's collections
     * one note at a time — the classic N+1 the constitution requires us to design
     * out rather than discover in production (Principle VII). The two collections
     * are fetched separately because a single query fetching both would produce a
     * cartesian product.
     */
    private void initializeAssociations(List<Note> notes) {
        if (notes.isEmpty()) {
            return;
        }
        getEntityManager()
                .createQuery("select distinct n from Note n left join fetch n.labels where n in :notes", Note.class)
                .setParameter("notes", notes)
                .getResultList();
        getEntityManager()
                .createQuery("select distinct n from Note n left join fetch n.items where n in :notes", Note.class)
                .setParameter("notes", notes)
                .getResultList();
    }

    private static String nativeViewPredicate(NoteView view) {
        return switch (view) {
            case ACTIVE -> "n.archived = false and n.trashed = false";
            case ARCHIVED -> "n.archived = true and n.trashed = false";
            case TRASHED -> "n.trashed = true";
        };
    }

    private static String labelFilteredQuery(NoteView view) {
        return "select n from Note n join n.labels l where l.id = :labelId and "
                + viewPredicate(view) + " and n.ownerId = :ownerId order by n.pinned desc, n.updatedAt desc";
    }

    /**
     * The trash is deliberately matched on {@code trashed} alone: a note that was
     * archived before being trashed keeps {@code archived = true} so it can be
     * restored to the archive, and it must still appear in the trash view.
     */
    private static String viewQuery(NoteView view) {
        return "ownerId = :ownerId and " + switch (view) {
            case ACTIVE -> "archived = false and trashed = false";
            case ARCHIVED -> "archived = true and trashed = false";
            case TRASHED -> "trashed = true";
        };
    }

    /** Same view rule as {@link #viewQuery}, qualified for a joined query. */
    private static String viewPredicate(NoteView view) {
        return switch (view) {
            case ACTIVE -> "n.archived = false and n.trashed = false";
            case ARCHIVED -> "n.archived = true and n.trashed = false";
            case TRASHED -> "n.trashed = true";
        };
    }

    /** Trashed notes whose retention window has elapsed (FR-014). */
    public List<Note> findTrashedBefore(Instant cutoff) {
        return find("trashed = true and trashedAt is not null and trashedAt < ?1", cutoff).list();
    }
}
