package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code GET /api/v1/notes} default active view (FR-002, FR-008). */
@QuarkusTest
class ListNotesContractTest {

    @Test
    void listsOwnNotesInPaginatedEnvelope() {
        String id = createNote(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("page", is(0))
                .body("size", greaterThanOrEqualTo(1))
                .body("totalElements", greaterThanOrEqualTo(1))
                .body("content.id", hasItem(id));
    }

    @Test
    void honoursPageAndSizeParameters() {
        createNote(TestTokens.OWNER);
        createNote(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .queryParam("page", 0)
                .queryParam("size", 1)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("page", is(0))
                .body("size", is(1))
                .body("content", hasSize(lessThanOrEqualTo(1)));
    }

    @Test
    void rejectsPageSizeAboveTheConfiguredMaximum() {
        // Pagination bounds are what keep this endpoint from returning an
        // unbounded collection (constitution Principle VII).
        as(TestTokens.OWNER)
                .queryParam("size", 5000)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(400)
                .body("error.code", is("VALIDATION_ERROR"));
    }

    @Test
    void rejectsNegativePage() {
        as(TestTokens.OWNER)
                .queryParam("page", -1)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(400);
    }

    @Test
    void neverReturnsAnotherUsersNotes() {
        String othersNote = createNote(TestTokens.OTHER_USER);

        as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", not(hasItem(othersNote)));
    }

    @Test
    void activeViewExcludesTrashedNotes() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", not(hasItem(id)))
                .body("content.trashed", everyItem(is(false)));
    }

    @Test
    void rejectsAnonymousRequest() {
        anonymous().when().get(BASE + "/notes").then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        as(TestTokens.NO_ROLE_USER).when().get(BASE + "/notes").then().statusCode(403);
    }
}
