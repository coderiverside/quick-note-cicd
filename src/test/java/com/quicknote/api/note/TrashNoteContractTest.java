package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code POST /api/v1/notes/{noteId}/trash} (FR-011). */
@QuarkusTest
class TrashNoteContractTest {

    @Test
    void movesNoteToTrash() {
        String id = createNote(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .when()
                .post(BASE + "/notes/" + id + "/trash")
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("trashed", is(true));
    }

    @Test
    void trashedNoteDisappearsFromTheActiveListing() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", not(hasItem(id)));
    }

    @Test
    void trashedNoteRemainsDirectlyRetrievable() {
        // Trashing hides a note from the active listing; it does not make it
        // unreadable, otherwise the trash view could never render it.
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + id)
                .then()
                .statusCode(200)
                .body("trashed", is(true));
    }

    @Test
    void trashingIsIdempotent() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .post(BASE + "/notes/" + id + "/trash")
                .then()
                .statusCode(200)
                .body("trashed", is(true));
    }

    @Test
    void returnsNotFoundForUnknownId() {
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + UUID.randomUUID() + "/trash").then().statusCode(404);
    }

    @Test
    void returnsNotFoundForAnotherUsersNote() {
        String othersNote = createNote(TestTokens.OTHER_USER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + othersNote + "/trash").then().statusCode(404);
    }

    @Test
    void rejectsAnonymousRequest() {
        String id = createNote(TestTokens.OWNER);
        anonymous().when().post(BASE + "/notes/" + id + "/trash").then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.NO_ROLE_USER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(403);
    }
}
