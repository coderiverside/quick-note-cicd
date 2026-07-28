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

/** Contract for {@code GET /api/v1/notes/{noteId}} (FR-002, FR-015). */
@QuarkusTest
class GetNoteContractTest {

    @Test
    void returnsOwnNote() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Readable\",\"content\":\"body\"}");

        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + id)
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("title", is("Readable"))
                .body("content", is("body"));
    }

    @Test
    void returnsNotFoundForUnknownId() {
        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("error.code", is("NOT_FOUND"));
    }

    @Test
    void returnsNotFoundRatherThanForbiddenForAnotherUsersNote() {
        // FR-015: the response must not reveal that the note exists for someone
        // else, so this is a 404 and not a 403.
        String othersNote = createNote(TestTokens.OTHER_USER);

        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + othersNote)
                .then()
                .statusCode(404)
                .body("error.code", is("NOT_FOUND"));
    }

    @Test
    void rejectsMalformedId() {
        as(TestTokens.OWNER).when().get(BASE + "/notes/not-a-uuid").then().statusCode(404);
    }

    @Test
    void rejectsAnonymousRequest() {
        String id = createNote(TestTokens.OWNER);
        anonymous().when().get(BASE + "/notes/" + id).then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.NO_ROLE_USER).when().get(BASE + "/notes/" + id).then().statusCode(403);
    }
}
