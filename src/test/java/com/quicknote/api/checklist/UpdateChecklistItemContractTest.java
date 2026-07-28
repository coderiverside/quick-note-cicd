package com.quicknote.api.checklist;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code PATCH /api/v1/notes/{noteId}/items/{itemId}} (FR-024, FR-025). */
@QuarkusTest
class UpdateChecklistItemContractTest {

    private static String noteWithOneItem(String user, String text) {
        return createNote(user, "{\"title\":\"list\",\"items\":[{\"text\":\"" + text + "\"}]}");
    }

    private static String firstItemId(String user, String noteId) {
        return as(user).when().get(BASE + "/notes/" + noteId).then().extract().path("items[0].id");
    }

    @Test
    void checksAnItemOff() {
        String noteId = noteWithOneItem(TestTokens.OWNER, "Buy milk");
        String itemId = firstItemId(TestTokens.OWNER, noteId);

        as(TestTokens.OWNER)
                .body("{\"checked\":true}")
                .when()
                .patch(BASE + "/notes/" + noteId + "/items/" + itemId)
                .then()
                .statusCode(200)
                .body("items[0].checked", is(true));
    }

    @Test
    void unchecksAnItem() {
        String noteId = noteWithOneItem(TestTokens.OWNER, "Buy bread");
        String itemId = firstItemId(TestTokens.OWNER, noteId);
        as(TestTokens.OWNER).body("{\"checked\":true}").when()
                .patch(BASE + "/notes/" + noteId + "/items/" + itemId).then().statusCode(200);

        as(TestTokens.OWNER)
                .body("{\"checked\":false}")
                .when()
                .patch(BASE + "/notes/" + noteId + "/items/" + itemId)
                .then()
                .statusCode(200)
                .body("items[0].checked", is(false));
    }

    @Test
    void checkedStatePersists() {
        String noteId = noteWithOneItem(TestTokens.OWNER, "Persisted");
        String itemId = firstItemId(TestTokens.OWNER, noteId);
        as(TestTokens.OWNER).body("{\"checked\":true}").when()
                .patch(BASE + "/notes/" + noteId + "/items/" + itemId).then().statusCode(200);

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + noteId).then().body("items[0].checked", is(true));
    }

    @Test
    void editsItemText() {
        String noteId = noteWithOneItem(TestTokens.OWNER, "Typo");
        String itemId = firstItemId(TestTokens.OWNER, noteId);

        as(TestTokens.OWNER)
                .body("{\"text\":\"Corrected\"}")
                .when()
                .patch(BASE + "/notes/" + noteId + "/items/" + itemId)
                .then()
                .statusCode(200)
                .body("items[0].text", is("Corrected"));
    }

    @Test
    void editingTextLeavesCheckedStateAlone() {
        String noteId = noteWithOneItem(TestTokens.OWNER, "Original");
        String itemId = firstItemId(TestTokens.OWNER, noteId);
        as(TestTokens.OWNER).body("{\"checked\":true}").when()
                .patch(BASE + "/notes/" + noteId + "/items/" + itemId).then().statusCode(200);

        as(TestTokens.OWNER)
                .body("{\"text\":\"Renamed\"}")
                .when()
                .patch(BASE + "/notes/" + noteId + "/items/" + itemId)
                .then()
                .statusCode(200)
                .body("items[0].text", is("Renamed"))
                .body("items[0].checked", is(true));
    }

    @Test
    void rejectsABlankText() {
        String noteId = noteWithOneItem(TestTokens.OWNER, "Keep me");
        String itemId = firstItemId(TestTokens.OWNER, noteId);

        as(TestTokens.OWNER)
                .body("{\"text\":\"  \"}")
                .when()
                .patch(BASE + "/notes/" + noteId + "/items/" + itemId)
                .then()
                .statusCode(400);
    }

    @Test
    void rejectsAnUpdateWithNeitherTextNorChecked() {
        String noteId = noteWithOneItem(TestTokens.OWNER, "Untouched");
        String itemId = firstItemId(TestTokens.OWNER, noteId);

        as(TestTokens.OWNER)
                .body("{}")
                .when()
                .patch(BASE + "/notes/" + noteId + "/items/" + itemId)
                .then()
                .statusCode(400);
    }

    @Test
    void returnsNotFoundForUnknownItem() {
        String noteId = noteWithOneItem(TestTokens.OWNER, "x");

        as(TestTokens.OWNER)
                .body("{\"checked\":true}")
                .when()
                .patch(BASE + "/notes/" + noteId + "/items/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void returnsNotFoundWhenTheItemBelongsToADifferentNote() {
        // An item id is only meaningful within its own note; borrowing one from
        // another note must not work.
        String firstNote = noteWithOneItem(TestTokens.OWNER, "first");
        String secondNote = noteWithOneItem(TestTokens.OWNER, "second");
        String itemOfSecond = firstItemId(TestTokens.OWNER, secondNote);

        as(TestTokens.OWNER)
                .body("{\"checked\":true}")
                .when()
                .patch(BASE + "/notes/" + firstNote + "/items/" + itemOfSecond)
                .then()
                .statusCode(404);
    }

    @Test
    void returnsNotFoundForAnotherUsersNote() {
        String othersNote = noteWithOneItem(TestTokens.OTHER_USER, "theirs");
        String itemId = firstItemId(TestTokens.OTHER_USER, othersNote);

        as(TestTokens.OWNER)
                .body("{\"checked\":true}")
                .when()
                .patch(BASE + "/notes/" + othersNote + "/items/" + itemId)
                .then()
                .statusCode(404);
    }

    @Test
    void rejectsAnonymousRequest() {
        String noteId = noteWithOneItem(TestTokens.OWNER, "x");
        String itemId = firstItemId(TestTokens.OWNER, noteId);
        anonymous().body("{\"checked\":true}").when()
                .patch(BASE + "/notes/" + noteId + "/items/" + itemId).then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        String noteId = noteWithOneItem(TestTokens.OWNER, "x");
        String itemId = firstItemId(TestTokens.OWNER, noteId);
        as(TestTokens.NO_ROLE_USER).body("{\"checked\":true}").when()
                .patch(BASE + "/notes/" + noteId + "/items/" + itemId).then().statusCode(403);
    }
}
