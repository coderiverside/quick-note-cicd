package com.quicknote.api.checklist;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code DELETE /api/v1/notes/{noteId}/items/{itemId}} (FR-023). */
@QuarkusTest
class DeleteChecklistItemContractTest {

    private static String itemIdAt(String user, String noteId, int index) {
        return as(user).when().get(BASE + "/notes/" + noteId).then().extract().path("items[" + index + "].id");
    }

    @Test
    void removesAnItem() {
        String noteId = createNote(TestTokens.OWNER, "{\"items\":[{\"text\":\"keep\"},{\"text\":\"drop\"}]}");
        String dropId = itemIdAt(TestTokens.OWNER, noteId, 1);

        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + noteId + "/items/" + dropId).then().statusCode(204);

        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + noteId)
                .then()
                .body("items", hasSize(1))
                .body("items.text", contains("keep"));
    }

    @Test
    void removingEveryItemLeavesAValidNoteWithAnEmptyList() {
        // A checklist that has been fully cleared is still a note, not an error.
        String noteId = createNote(TestTokens.OWNER, "{\"title\":\"Emptying\",\"items\":[{\"text\":\"only\"}]}");
        String itemId = itemIdAt(TestTokens.OWNER, noteId, 0);

        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + noteId + "/items/" + itemId).then().statusCode(204);

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + noteId).then().statusCode(200).body("items", hasSize(0));
    }

    @Test
    void remainingItemsKeepTheirRelativeOrder() {
        String noteId = createNote(TestTokens.OWNER,
                "{\"items\":[{\"text\":\"a\"},{\"text\":\"b\"},{\"text\":\"c\"}]}");
        String middle = itemIdAt(TestTokens.OWNER, noteId, 1);

        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + noteId + "/items/" + middle).then().statusCode(204);

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + noteId).then().body("items.text", contains("a", "c"));
    }

    @Test
    void returnsNotFoundForUnknownItem() {
        String noteId = createNote(TestTokens.OWNER, "{\"items\":[{\"text\":\"x\"}]}");

        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + noteId + "/items/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void returnsNotFoundForAnotherUsersNote() {
        String othersNote = createNote(TestTokens.OTHER_USER, "{\"items\":[{\"text\":\"theirs\"}]}");
        String itemId = itemIdAt(TestTokens.OTHER_USER, othersNote, 0);

        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + othersNote + "/items/" + itemId)
                .then()
                .statusCode(404);
    }

    @Test
    void rejectsAnonymousRequest() {
        String noteId = createNote(TestTokens.OWNER, "{\"items\":[{\"text\":\"x\"}]}");
        String itemId = itemIdAt(TestTokens.OWNER, noteId, 0);
        anonymous().when().delete(BASE + "/notes/" + noteId + "/items/" + itemId).then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        String noteId = createNote(TestTokens.OWNER, "{\"items\":[{\"text\":\"x\"}]}");
        String itemId = itemIdAt(TestTokens.OWNER, noteId, 0);
        as(TestTokens.NO_ROLE_USER).when().delete(BASE + "/notes/" + noteId + "/items/" + itemId).then()
                .statusCode(403);
    }
}
