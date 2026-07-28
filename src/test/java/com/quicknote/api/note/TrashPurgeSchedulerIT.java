package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * The 7-day trash retention rule (FR-014).
 *
 * <p>Rather than waiting for the daily schedule to fire, these tests backdate a
 * note's {@code trashedAt} and invoke the purge directly — the schedule only
 * decides <em>when</em> the rule runs, and the rule itself is what matters.
 */
@QuarkusTest
class TrashPurgeSchedulerIT {

    @Inject
    TrashPurgeScheduler scheduler;

    @Inject
    NoteRepository noteRepository;

    @Test
    void purgesNotesTrashedLongerThanTheRetentionWindow() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"expired\"}");
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        backdateTrashedAt(UUID.fromString(id), Duration.ofDays(8));

        scheduler.purgeExpiredNotes();

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + id).then().statusCode(404);
    }

    @Test
    void keepsNotesStillInsideTheRetentionWindow() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"recent\"}");
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        backdateTrashedAt(UUID.fromString(id), Duration.ofDays(6));

        scheduler.purgeExpiredNotes();

        // Still restorable: the whole point of the window (SC-006).
        as(TestTokens.OWNER).when().get(BASE + "/notes/" + id).then().statusCode(200);
        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + id + "/trash").then().statusCode(200);
    }

    @Test
    void neverPurgesNotesThatAreNotInTheTrash() {
        String active = createNote(TestTokens.OWNER, "{\"title\":\"untouched-active\"}");
        String archived = createNote(TestTokens.OWNER, "{\"title\":\"untouched-archived\"}");
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + archived + "/archive").then().statusCode(200);

        scheduler.purgeExpiredNotes();

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + active).then().statusCode(200);
        as(TestTokens.OWNER).when().get(BASE + "/notes/" + archived).then().statusCode(200);
    }

    @Test
    void purgeIsSafeToRunWhenNothingHasExpired() {
        // The daily job runs whether or not there is anything to do.
        scheduler.purgeExpiredNotes();
        scheduler.purgeExpiredNotes();
    }

    private void backdateTrashedAt(UUID noteId, Duration age) {
        QuarkusTransaction.requiringNew().run(() -> noteRepository.update(
                "trashedAt = ?1 where id = ?2", Instant.now().minus(age), noteId));
    }
}
