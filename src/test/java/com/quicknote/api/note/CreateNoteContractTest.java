package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.empty;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code POST /api/v1/notes} (FR-001). */
@QuarkusTest
class CreateNoteContractTest {

    @Test
    void createsNoteWithTitleAndContent() {
        as(TestTokens.OWNER)
                .body("{\"title\":\"Groceries\",\"content\":\"Milk, eggs, bread\"}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(201)
                .header("Location", notNullValue())
                .body("id", notNullValue())
                .body("title", is("Groceries"))
                .body("content", is("Milk, eggs, bread"))
                .body("pinned", is(false))
                .body("archived", is(false))
                .body("trashed", is(false))
                .body("labelIds", is(empty()))
                .body("items", is(empty()))
                .body("createdAt", notNullValue())
                .body("updatedAt", notNullValue());
    }

    @Test
    void createsNoteWithTitleOnly() {
        as(TestTokens.OWNER)
                .body("{\"title\":\"Just a title\"}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(201)
                .body("title", is("Just a title"));
    }

    @Test
    void createsNoteWithContentOnly() {
        as(TestTokens.OWNER)
                .body("{\"content\":\"Just some body text\"}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(201)
                .body("content", is("Just some body text"));
    }

    @Test
    void rejectsCompletelyEmptyNote() {
        // FR-001: a note with no title, no content and no items carries nothing.
        as(TestTokens.OWNER)
                .body("{}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(400)
                .body("error.code", is("VALIDATION_ERROR"));
    }

    @Test
    void rejectsNoteWhoseFieldsAreOnlyWhitespace() {
        as(TestTokens.OWNER)
                .body("{\"title\":\"   \",\"content\":\"\"}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(400)
                .body("error.code", is("VALIDATION_ERROR"));
    }

    @Test
    void rejectsUnknownColour() {
        as(TestTokens.OWNER)
                .body("{\"title\":\"x\",\"color\":\"CHARTREUSE\"}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(400);
    }

    @Test
    void rejectsAnonymousRequest() {
        anonymous()
                .body("{\"title\":\"x\"}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(401)
                .body("error.code", is("UNAUTHORIZED"));
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        // Valid token, missing the `user` realm role: an RBAC failure, not a
        // missing resource, so 403 rather than 404 (research.md 1b).
        as(TestTokens.NO_ROLE_USER)
                .body("{\"title\":\"x\"}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(403)
                .body("error.code", is("FORBIDDEN"));
    }
}
