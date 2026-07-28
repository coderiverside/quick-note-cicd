package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.as;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end capture journey (User Story 1) against real PostgreSQL: create,
 * list, read, edit, trash — the slice that makes the API useful on its own.
 */
@QuarkusTest
class NoteLifecycleIT {

    @Test
    void captureReadEditAndTrashANote() {
        // Capture
        String id = as(TestTokens.OWNER)
                .body("{\"title\":\"Trip planning\",\"content\":\"Book flights\"}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // Appears in the active listing
        as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(id));

        // Reads back exactly what was stored
        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + id)
                .then()
                .statusCode(200)
                .body("title", is("Trip planning"))
                .body("content", is("Book flights"))
                .body("createdAt", notNullValue());

        // Edits persist
        as(TestTokens.OWNER)
                .body("{\"content\":\"Book flights and hotel\"}")
                .when()
                .patch(BASE + "/notes/" + id)
                .then()
                .statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + id)
                .then()
                .body("content", is("Book flights and hotel"))
                .body("title", is("Trip planning"));

        // Trashing removes it from the active listing
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .body("content.id", not(hasItem(id)));
    }

    @Test
    void updatingANoteAdvancesItsUpdatedAtButNotItsCreatedAt() throws InterruptedException {
        String id = as(TestTokens.OWNER)
                .body("{\"title\":\"Timestamps\"}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String createdAt = as(TestTokens.OWNER).when().get(BASE + "/notes/" + id).then().extract().path("createdAt");

        Thread.sleep(10);

        as(TestTokens.OWNER).body("{\"title\":\"Timestamps edited\"}").when().patch(BASE + "/notes/" + id).then()
                .statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + id)
                .then()
                .body("createdAt", is(createdAt));
    }

    @Test
    void twoUsersNotesRemainCompletelySeparate() {
        String mine = as(TestTokens.OWNER)
                .body("{\"title\":\"mine\"}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String theirs = as(TestTokens.OTHER_USER)
                .body("{\"title\":\"theirs\"}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + theirs).then().statusCode(404);
        as(TestTokens.OTHER_USER).when().get(BASE + "/notes/" + mine).then().statusCode(404);

        as(TestTokens.OWNER).queryParam("size", 100).when().get(BASE + "/notes").then()
                .body("content.id", hasItem(mine))
                .body("content.id", not(hasItem(theirs)));
    }
}
