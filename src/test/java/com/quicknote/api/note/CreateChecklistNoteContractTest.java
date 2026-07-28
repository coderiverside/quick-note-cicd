package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.as;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for creating a checklist-style note via {@code POST /api/v1/notes} (FR-022). */
@QuarkusTest
class CreateChecklistNoteContractTest {

    @Test
    void createsANoteWithChecklistItems() {
        as(TestTokens.OWNER)
                .body("{\"title\":\"Packing list\",\"items\":[{\"text\":\"Passport\"},{\"text\":\"Charger\"}]}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(201)
                .body("items", hasSize(2))
                .body("items.text", contains("Passport", "Charger"))
                .body("items.checked", everyItem(is(false)))
                .body("items[0].id", notNullValue());
    }

    @Test
    void itemsStartUnchecked() {
        // FR-022: a freshly captured list is a list of things still to do.
        as(TestTokens.OWNER)
                .body("{\"title\":\"Chores\",\"items\":[{\"text\":\"Dishes\"}]}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(201)
                .body("items[0].checked", is(false));
    }

    @Test
    void itemsKeepTheOrderTheyWereSubmittedIn() {
        String id = as(TestTokens.OWNER)
                .body("{\"title\":\"Ordered\",\"items\":[{\"text\":\"first\"},{\"text\":\"second\"},{\"text\":\"third\"}]}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + id)
                .then()
                .body("items.text", contains("first", "second", "third"));
    }

    @Test
    void aChecklistNoteNeedsNoTitleOrContent() {
        // Items alone carry information, so FR-001's "not entirely empty" rule
        // is satisfied by them.
        as(TestTokens.OWNER)
                .body("{\"items\":[{\"text\":\"Buy milk\"}]}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(201)
                .body("items", hasSize(1));
    }

    @Test
    void rejectsANoteWhoseOnlyItemsAreBlank() {
        as(TestTokens.OWNER)
                .body("{\"items\":[{\"text\":\"   \"}]}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(400);
    }

    @Test
    void rejectsAnEmptyItemsArrayWithNoTitleOrContent() {
        as(TestTokens.OWNER)
                .body("{\"items\":[]}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(400)
                .body("error.code", is("VALIDATION_ERROR"));
    }

    @Test
    void aPlainNoteReportsAnEmptyItemList() {
        as(TestTokens.OWNER)
                .body("{\"title\":\"Plain\"}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(201)
                .body("items", hasSize(0));
    }
}
