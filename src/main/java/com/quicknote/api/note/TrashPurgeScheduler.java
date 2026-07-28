package com.quicknote.api.note;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;

/**
 * Permanently deletes notes that have sat in the trash past the retention window
 * (FR-014, default 7 days).
 *
 * <p>Runs in-process on a schedule rather than behind an endpoint: the operation
 * has no legitimate external caller, so exposing it would mean securing a route
 * nobody should reach (research.md 8). Checklist items and label associations go
 * with the note through ON DELETE CASCADE.
 */
@ApplicationScoped
public class TrashPurgeScheduler {

    private static final Logger LOG = Logger.getLogger(TrashPurgeScheduler.class);

    @Inject
    NoteRepository noteRepository;

    @ConfigProperty(name = "quicknote.trash.retention-days")
    int retentionDays;

    @Scheduled(cron = "{quicknote.trash.purge-cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledPurge() {
        purgeExpiredNotes();
    }

    /**
     * Deletes every note whose retention window has elapsed.
     *
     * <p>Package-private rather than private so the retention rule can be tested
     * directly instead of by waiting for the schedule to fire.
     */
    @Transactional
    public void purgeExpiredNotes() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        List<Note> expired = noteRepository.findTrashedBefore(cutoff);

        if (expired.isEmpty()) {
            return;
        }

        expired.forEach(noteRepository::delete);
        LOG.infof("Purged %d note(s) trashed before %s (retention: %d days)", expired.size(), cutoff, retentionDays);
    }
}
