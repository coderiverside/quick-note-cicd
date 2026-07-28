package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/**
 * User Story 3 end to end: the full archive and trash lifecycle, including the
 * guarantee that nothing is lost until an explicit permanent delete.
 */
@QuarkusTest
class NoteTrashLifecycleIT {

    @Test
    void archiveTrashRestoreAndPermanentlyDelete() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Lifecycle\",\"content\":\"body\"}");

        // Archive hides it from the active list but keeps it in the archive
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/archive").then().statusCode(200);
        assertNotInView(id, "active");
        assertInView(id, "archived");

        // Unarchive brings it back
        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + id + "/archive").then().statusCode(200);
        assertInView(id, "active");

        // Trash removes it from both active and archived views
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);
        assertNotInView(id, "active");
        assertNotInView(id, "archived");
        assertInView(id, "trashed");

        // Archiving straight out of the trash is refused (FR-010)
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/archive").then().statusCode(409);

        // Restore returns it to the active list with content intact
        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + id + "/trash")
                .then()
                .statusCode(200)
                .body("title", is("Lifecycle"))
                .body("content", is("body"));
        assertInView(id, "active");

        // Trash again, then delete for good
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);
        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + id).then().statusCode(204);
        as(TestTokens.OWNER).when().get(BASE + "/notes/" + id).then().statusCode(404);
    }

    @Test
    void archivedNoteTrashedThenRestoredReturnsToTheArchive() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Round trip\"}");

        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/archive").then().statusCode(200);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);
        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        assertInView(id, "archived");
        assertNotInView(id, "active");
    }

    private void assertInView(String id, String view) {
        as(TestTokens.OWNER)
                .queryParam("view", view)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(id));
    }

    private void assertNotInView(String id, String view) {
        as(TestTokens.OWNER)
                .queryParam("view", view)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", not(hasItem(id)));
    }
}
