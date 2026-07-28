package com.quicknote.api.checklist;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code POST /api/v1/notes/{noteId}/items} (FR-023). */
@QuarkusTest
class AddChecklistItemContractTest {

    @Test
    void addsAnItemToAnExistingNote() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"List\",\"items\":[{\"text\":\"first\"}]}");

        as(TestTokens.OWNER)
                .body("{\"text\":\"second\"}")
                .when()
                .post(BASE + "/notes/" + id + "/items")
                .then()
                .statusCode(201)
                .body("items", hasSize(2))
                .body("items.text", contains("first", "second"));
    }

    @Test
    void turnsAPlainNoteIntoAChecklistNote() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Was plain\"}");

        as(TestTokens.OWNER)
                .body("{\"text\":\"now has an item\"}")
                .when()
                .post(BASE + "/notes/" + id + "/items")
                .then()
                .statusCode(201)
                .body("items", hasSize(1));
    }

    @Test
    void newItemsAreAppendedAtTheEnd() {
        String id = createNote(TestTokens.OWNER, "{\"items\":[{\"text\":\"a\"},{\"text\":\"b\"}]}");

        as(TestTokens.OWNER).body("{\"text\":\"c\"}").when().post(BASE + "/notes/" + id + "/items").then()
                .statusCode(201);

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + id).then().body("items.text", contains("a", "b", "c"));
    }

    @Test
    void newItemStartsUnchecked() {
        String id = createNote(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .body("{\"text\":\"todo\"}")
                .when()
                .post(BASE + "/notes/" + id + "/items")
                .then()
                .statusCode(201)
                .body("items[0].checked", is(false));
    }

    @Test
    void rejectsABlankItem() {
        String id = createNote(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .body("{\"text\":\"   \"}")
                .when()
                .post(BASE + "/notes/" + id + "/items")
                .then()
                .statusCode(400);
    }

    @Test
    void rejectsAMissingText() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).body("{}").when().post(BASE + "/notes/" + id + "/items").then().statusCode(400);
    }

    @Test
    void returnsNotFoundForUnknownNote() {
        as(TestTokens.OWNER)
                .body("{\"text\":\"x\"}")
                .when()
                .post(BASE + "/notes/" + UUID.randomUUID() + "/items")
                .then()
                .statusCode(404);
    }

    @Test
    void returnsNotFoundForAnotherUsersNote() {
        String othersNote = createNote(TestTokens.OTHER_USER);

        as(TestTokens.OWNER)
                .body("{\"text\":\"x\"}")
                .when()
                .post(BASE + "/notes/" + othersNote + "/items")
                .then()
                .statusCode(404);
    }

    @Test
    void rejectsAnonymousRequest() {
        String id = createNote(TestTokens.OWNER);
        anonymous().body("{\"text\":\"x\"}").when().post(BASE + "/notes/" + id + "/items").then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.NO_ROLE_USER).body("{\"text\":\"x\"}").when().post(BASE + "/notes/" + id + "/items").then()
                .statusCode(403);
    }
}
