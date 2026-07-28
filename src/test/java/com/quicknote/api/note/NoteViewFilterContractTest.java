package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for the {@code view} filter on {@code GET /api/v1/notes} (FR-008, FR-009, FR-012). */
@QuarkusTest
class NoteViewFilterContractTest {

    @Test
    void archivedViewReturnsOnlyArchivedNotes() {
        String archived = createNote(TestTokens.OWNER);
        String active = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + archived + "/archive").then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("view", "archived")
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(archived))
                .body("content.id", not(hasItem(active)))
                .body("content.archived", everyItem(is(true)))
                .body("content.trashed", everyItem(is(false)));
    }

    @Test
    void trashedViewReturnsOnlyTrashedNotes() {
        String trashed = createNote(TestTokens.OWNER);
        String active = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + trashed + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("view", "trashed")
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(trashed))
                .body("content.id", not(hasItem(active)))
                .body("content.trashed", everyItem(is(true)));
    }

    @Test
    void trashedViewIncludesNotesThatWereArchivedBeforeBeingTrashed() {
        // A note keeps its archived flag while in the trash so restore can put it
        // back; the trash view must still show it.
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/archive").then().statusCode(200);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("view", "trashed")
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(id));
    }

    @Test
    void archivedViewExcludesArchivedNotesThatAreNowTrashed() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/archive").then().statusCode(200);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("view", "archived")
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .body("content.id", not(hasItem(id)));
    }

    @Test
    void defaultViewIsActive() {
        String archived = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + archived + "/archive").then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .body("content.archived", everyItem(is(false)))
                .body("content.trashed", everyItem(is(false)));
    }

    @Test
    void rejectsAnUnknownView() {
        as(TestTokens.OWNER)
                .queryParam("view", "everything")
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(400)
                .body("error.code", is("VALIDATION_ERROR"));
    }

    @Test
    void viewsNeverLeakAnotherUsersNotes() {
        String othersArchived = createNote(TestTokens.OTHER_USER);
        as(TestTokens.OTHER_USER).when().post(BASE + "/notes/" + othersArchived + "/archive").then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("view", "archived")
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .body("content.id", not(hasItem(othersArchived)));
    }
}
