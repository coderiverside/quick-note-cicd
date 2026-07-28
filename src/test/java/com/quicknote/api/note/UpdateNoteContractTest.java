package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code PATCH /api/v1/notes/{noteId}} (FR-003). */
@QuarkusTest
class UpdateNoteContractTest {

    @Test
    void updatesTitleAndContent() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Before\",\"content\":\"old\"}");

        as(TestTokens.OWNER)
                .body("{\"title\":\"After\",\"content\":\"new\"}")
                .when()
                .patch(BASE + "/notes/" + id)
                .then()
                .statusCode(200)
                .body("title", is("After"))
                .body("content", is("new"));

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + id).then().body("title", is("After"));
    }

    @Test
    void leavesOmittedFieldsUnchanged() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Keep me\",\"content\":\"original\"}");

        as(TestTokens.OWNER)
                .body("{\"content\":\"replaced\"}")
                .when()
                .patch(BASE + "/notes/" + id)
                .then()
                .statusCode(200)
                .body("title", is("Keep me"))
                .body("content", is("replaced"));
    }

    @Test
    void clearsAFieldWhenExplicitlySetToNull() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Has title\",\"content\":\"and content\"}");

        as(TestTokens.OWNER)
                .body("{\"title\":null}")
                .when()
                .patch(BASE + "/notes/" + id)
                .then()
                .statusCode(200)
                .body("title", nullValue())
                .body("content", is("and content"));
    }

    @Test
    void rejectsAnUpdateThatWouldEmptyTheNote() {
        // A note may not be edited into the empty state FR-001 forbids creating.
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Only title\"}");

        as(TestTokens.OWNER)
                .body("{\"title\":null}")
                .when()
                .patch(BASE + "/notes/" + id)
                .then()
                .statusCode(400)
                .body("error.code", is("VALIDATION_ERROR"));
    }

    @Test
    void returnsNotFoundForUnknownId() {
        as(TestTokens.OWNER)
                .body("{\"title\":\"x\"}")
                .when()
                .patch(BASE + "/notes/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void returnsNotFoundForAnotherUsersNote() {
        String othersNote = createNote(TestTokens.OTHER_USER);

        as(TestTokens.OWNER)
                .body("{\"title\":\"hijacked\"}")
                .when()
                .patch(BASE + "/notes/" + othersNote)
                .then()
                .statusCode(404);
    }

    @Test
    void rejectsAnonymousRequest() {
        String id = createNote(TestTokens.OWNER);
        anonymous().body("{\"title\":\"x\"}").when().patch(BASE + "/notes/" + id).then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.NO_ROLE_USER).body("{\"title\":\"x\"}").when().patch(BASE + "/notes/" + id).then().statusCode(403);
    }
}
