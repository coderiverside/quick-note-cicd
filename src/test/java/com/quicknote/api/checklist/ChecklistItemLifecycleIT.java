package com.quicknote.api.checklist;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** User Story 5 end to end: build a shopping list and work through it. */
@QuarkusTest
class ChecklistItemLifecycleIT {

    @Test
    void buildTickOffAndPruneAShoppingList() {
        String noteId = createNote(TestTokens.OWNER,
                "{\"title\":\"Shopping\",\"items\":[{\"text\":\"Milk\"},{\"text\":\"Eggs\"}]}");

        // Add a third thing we remembered later
        as(TestTokens.OWNER)
                .body("{\"text\":\"Bread\"}")
                .when()
                .post(BASE + "/notes/" + noteId + "/items")
                .then()
                .statusCode(201)
                .body("items.text", contains("Milk", "Eggs", "Bread"));

        String milk = as(TestTokens.OWNER).when().get(BASE + "/notes/" + noteId).then().extract()
                .path("items[0].id");
        String eggs = as(TestTokens.OWNER).when().get(BASE + "/notes/" + noteId).then().extract()
                .path("items[1].id");

        // Tick two off
        as(TestTokens.OWNER).body("{\"checked\":true}").when()
                .patch(BASE + "/notes/" + noteId + "/items/" + milk).then().statusCode(200);
        as(TestTokens.OWNER).body("{\"checked\":true}").when()
                .patch(BASE + "/notes/" + noteId + "/items/" + eggs).then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + noteId)
                .then()
                .body("items.checked", contains(true, true, false));

        // Changed our mind about eggs
        as(TestTokens.OWNER).body("{\"checked\":false}").when()
                .patch(BASE + "/notes/" + noteId + "/items/" + eggs).then().statusCode(200);

        // ...and we do not need milk after all
        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + noteId + "/items/" + milk).then().statusCode(204);

        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + noteId)
                .then()
                .body("items", hasSize(2))
                .body("items.text", contains("Eggs", "Bread"))
                .body("items.checked", contains(false, false))
                .body("title", is("Shopping"));
    }

    @Test
    void checklistItemsSurviveTheTrashRoundTrip() {
        String noteId = createNote(TestTokens.OWNER,
                "{\"title\":\"Recoverable\",\"items\":[{\"text\":\"one\"},{\"text\":\"two\"}]}");
        String first = as(TestTokens.OWNER).when().get(BASE + "/notes/" + noteId).then().extract()
                .path("items[0].id");
        as(TestTokens.OWNER).body("{\"checked\":true}").when()
                .patch(BASE + "/notes/" + noteId + "/items/" + first).then().statusCode(200);

        as(TestTokens.OWNER).when().post(BASE + "/notes/" + noteId + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + noteId + "/trash")
                .then()
                .statusCode(200)
                .body("items", hasSize(2))
                .body("items.checked", contains(true, false));
    }

    @Test
    void deletingTheNotePermanentlyRemovesItsItems() {
        String noteId = createNote(TestTokens.OWNER, "{\"items\":[{\"text\":\"doomed\"}]}");

        as(TestTokens.OWNER).when().post(BASE + "/notes/" + noteId + "/trash").then().statusCode(200);
        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + noteId).then().statusCode(204);

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + noteId).then().statusCode(404);
    }

    @Test
    void checklistNotesAppearInListingsWithTheirItems() {
        String noteId = createNote(TestTokens.OWNER,
                "{\"title\":\"listed-checklist\",\"items\":[{\"text\":\"visible\"}]}");

        List<?> items = as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .extract()
                .path("content.find { it.id == '" + noteId + "' }.items");

        assertEquals(1, items.size(), "a checklist note should carry its items in the listing too");
    }
}
