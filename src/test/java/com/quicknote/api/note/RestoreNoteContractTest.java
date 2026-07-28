package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code DELETE /api/v1/notes/{noteId}/trash} — restore (FR-011). */
@QuarkusTest
class RestoreNoteContractTest {

    @Test
    void restoresATrashedNote() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + id + "/trash")
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("trashed", is(false));
    }

    @Test
    void restoredNoteReappearsInTheActiveListing() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);
        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .body("content.id", hasItem(id));
    }

    @Test
    void restoringAnArchivedNoteReturnsItToTheArchive() {
        // FR-011: restore puts the note back where it came from. An archived note
        // that was trashed belongs in the archive again, not the active list.
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/archive").then().statusCode(200);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + id + "/trash")
                .then()
                .statusCode(200)
                .body("trashed", is(false))
                .body("archived", is(true));

        as(TestTokens.OWNER)
                .queryParam("view", "archived")
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .body("content.id", hasItem(id));
    }

    @Test
    void restorePreservesColourAndPinState() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"organised\",\"color\":\"PURPLE\"}");
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/pin").then().statusCode(200);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + id + "/trash")
                .then()
                .statusCode(200)
                .body("color", is("PURPLE"))
                .body("pinned", is(true))
                .body("title", is("organised"));
    }

    @Test
    void restoringANoteThatIsNotTrashedSucceeds() {
        String id = createNote(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + id + "/trash")
                .then()
                .statusCode(200)
                .body("trashed", is(false));
    }

    @Test
    void returnsNotFoundForUnknownId() {
        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + UUID.randomUUID() + "/trash").then().statusCode(404);
    }

    @Test
    void returnsNotFoundForAnotherUsersNote() {
        String othersNote = createNote(TestTokens.OTHER_USER);
        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + othersNote + "/trash").then().statusCode(404);
    }

    @Test
    void rejectsAnonymousRequest() {
        String id = createNote(TestTokens.OWNER);
        anonymous().when().delete(BASE + "/notes/" + id + "/trash").then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.NO_ROLE_USER).when().delete(BASE + "/notes/" + id + "/trash").then().statusCode(403);
    }
}
