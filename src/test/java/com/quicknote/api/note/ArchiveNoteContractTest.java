package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code POST}/{@code DELETE /api/v1/notes/{noteId}/archive} (FR-007, FR-010). */
@QuarkusTest
class ArchiveNoteContractTest {

    @Test
    void archivesANote() {
        String id = createNote(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .when()
                .post(BASE + "/notes/" + id + "/archive")
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("archived", is(true));
    }

    @Test
    void unarchivesANote() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/archive").then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + id + "/archive")
                .then()
                .statusCode(200)
                .body("archived", is(false));
    }

    @Test
    void archivingIsIdempotent() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/archive").then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .post(BASE + "/notes/" + id + "/archive")
                .then()
                .statusCode(200)
                .body("archived", is(true));
    }

    @Test
    void refusesToArchiveANoteThatIsInTheTrash() {
        // FR-010: archiving is not a way out of the trash; the note has to be
        // restored first, so this is a conflict rather than a silent success.
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .post(BASE + "/notes/" + id + "/archive")
                .then()
                .statusCode(409)
                .body("error.code", is("CONFLICT"));
    }

    @Test
    void archivedNoteDisappearsFromTheActiveListing() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/archive").then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .body("content.id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(id)));
    }

    @Test
    void returnsNotFoundForUnknownId() {
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + UUID.randomUUID() + "/archive").then().statusCode(404);
    }

    @Test
    void returnsNotFoundForAnotherUsersNote() {
        String othersNote = createNote(TestTokens.OTHER_USER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + othersNote + "/archive").then().statusCode(404);
    }

    @Test
    void rejectsAnonymousRequest() {
        String id = createNote(TestTokens.OWNER);
        anonymous().when().post(BASE + "/notes/" + id + "/archive").then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.NO_ROLE_USER).when().post(BASE + "/notes/" + id + "/archive").then().statusCode(403);
    }
}
