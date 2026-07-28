package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code GET /api/v1/notes?label=} (FR-020). */
@QuarkusTest
class NoteLabelFilterContractTest {

    private static String createLabel(String user) {
        return as(user)
                .body("{\"name\":\"label-" + UUID.randomUUID() + "\"}")
                .when()
                .post(BASE + "/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    void returnsOnlyNotesCarryingTheLabel() {
        String labelId = createLabel(TestTokens.OWNER);
        String labelled = createNote(TestTokens.OWNER);
        String unlabelled = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().put(BASE + "/notes/" + labelled + "/labels/" + labelId).then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("label", labelId)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(labelled))
                .body("content.id", not(hasItem(unlabelled)));
    }

    @Test
    void returnsAnEmptyPageForALabelWithNoNotes() {
        String labelId = createLabel(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .queryParam("label", labelId)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content", org.hamcrest.Matchers.empty())
                .body("totalElements", is(0));
    }

    @Test
    void combinesWithTheViewFilter() {
        String labelId = createLabel(TestTokens.OWNER);
        String archived = createNote(TestTokens.OWNER);
        String active = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().put(BASE + "/notes/" + archived + "/labels/" + labelId).then().statusCode(200);
        as(TestTokens.OWNER).when().put(BASE + "/notes/" + active + "/labels/" + labelId).then().statusCode(200);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + archived + "/archive").then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("label", labelId)
                .queryParam("view", "archived")
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(archived))
                .body("content.id", not(hasItem(active)));
    }

    @Test
    void deletingALabelRemovesItFromTheFilterAndFromItsNotes() {
        String labelId = createLabel(TestTokens.OWNER);
        String noteId = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().put(BASE + "/notes/" + noteId + "/labels/" + labelId).then().statusCode(200);

        as(TestTokens.OWNER).when().delete(BASE + "/labels/" + labelId).then().statusCode(204);

        // FR-018: the note survives, just without the label.
        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + noteId)
                .then()
                .statusCode(200)
                .body("labelIds", not(hasItem(labelId)));
    }

    @Test
    void returnsNotFoundForAnUnknownLabel() {
        as(TestTokens.OWNER)
                .queryParam("label", UUID.randomUUID().toString())
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(404);
    }

    @Test
    void returnsNotFoundForAnotherUsersLabel() {
        String othersLabel = createLabel(TestTokens.OTHER_USER);

        as(TestTokens.OWNER)
                .queryParam("label", othersLabel)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(404);
    }
}
