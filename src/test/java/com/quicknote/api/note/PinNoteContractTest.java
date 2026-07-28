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

/** Contract for {@code POST}/{@code DELETE /api/v1/notes/{noteId}/pin} (FR-006). */
@QuarkusTest
class PinNoteContractTest {

    @Test
    void pinsANote() {
        String id = createNote(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .when()
                .post(BASE + "/notes/" + id + "/pin")
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("pinned", is(true));
    }

    @Test
    void unpinsANote() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/pin").then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + id + "/pin")
                .then()
                .statusCode(200)
                .body("pinned", is(false));
    }

    @Test
    void pinStatePersistsAcrossRetrieval() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/pin").then().statusCode(200);

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + id).then().body("pinned", is(true));
    }

    @Test
    void pinningIsIdempotent() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/pin").then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .post(BASE + "/notes/" + id + "/pin")
                .then()
                .statusCode(200)
                .body("pinned", is(true));
    }

    @Test
    void unpinningAnUnpinnedNoteSucceeds() {
        String id = createNote(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + id + "/pin")
                .then()
                .statusCode(200)
                .body("pinned", is(false));
    }

    @Test
    void returnsNotFoundForUnknownId() {
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + UUID.randomUUID() + "/pin").then().statusCode(404);
        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + UUID.randomUUID() + "/pin").then().statusCode(404);
    }

    @Test
    void returnsNotFoundForAnotherUsersNote() {
        String othersNote = createNote(TestTokens.OTHER_USER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + othersNote + "/pin").then().statusCode(404);
    }

    @Test
    void rejectsAnonymousRequest() {
        String id = createNote(TestTokens.OWNER);
        anonymous().when().post(BASE + "/notes/" + id + "/pin").then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.NO_ROLE_USER).when().post(BASE + "/notes/" + id + "/pin").then().statusCode(403);
    }
}
