package com.quicknote.api.label;

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

/**
 * User Story 4 end to end, with particular attention to FR-018: deleting a label
 * must detach it from every note without taking any note with it.
 */
@QuarkusTest
class LabelLifecycleIT {

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
    void createApplyFilterRenameAndDelete() {
        String labelId = createLabel(TestTokens.OWNER);
        String noteId = createNote(TestTokens.OWNER, "{\"title\":\"Q3 planning\"}");

        as(TestTokens.OWNER).when().put(BASE + "/notes/" + noteId + "/labels/" + labelId).then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("label", labelId)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .body("content.id", hasItem(noteId));

        String renamed = "label-" + UUID.randomUUID();
        as(TestTokens.OWNER)
                .body("{\"name\":\"" + renamed + "\"}")
                .when()
                .patch(BASE + "/labels/" + labelId)
                .then()
                .statusCode(200)
                .body("name", is(renamed));

        // Renaming does not disturb the association.
        as(TestTokens.OWNER).when().get(BASE + "/notes/" + noteId).then().body("labelIds", hasItem(labelId));

        as(TestTokens.OWNER).when().delete(BASE + "/labels/" + labelId).then().statusCode(204);

        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + noteId)
                .then()
                .statusCode(200)
                .body("title", is("Q3 planning"))
                .body("labelIds", not(hasItem(labelId)));
    }

    @Test
    void deletingALabelAppliedToManyNotesLeavesEveryNoteIntact() {
        String labelId = createLabel(TestTokens.OWNER);
        String[] noteIds = new String[5];
        for (int i = 0; i < noteIds.length; i++) {
            noteIds[i] = createNote(TestTokens.OWNER, "{\"title\":\"bulk-" + i + "\"}");
            as(TestTokens.OWNER).when().put(BASE + "/notes/" + noteIds[i] + "/labels/" + labelId).then()
                    .statusCode(200);
        }

        as(TestTokens.OWNER).when().delete(BASE + "/labels/" + labelId).then().statusCode(204);

        for (String noteId : noteIds) {
            as(TestTokens.OWNER)
                    .when()
                    .get(BASE + "/notes/" + noteId)
                    .then()
                    .statusCode(200)
                    .body("labelIds", not(hasItem(labelId)));
        }
    }

    @Test
    void aNoteCanCarryManyLabelsAndLoseThemIndependently() {
        String noteId = createNote(TestTokens.OWNER);
        String keep = createLabel(TestTokens.OWNER);
        String drop = createLabel(TestTokens.OWNER);

        as(TestTokens.OWNER).when().put(BASE + "/notes/" + noteId + "/labels/" + keep).then().statusCode(200);
        as(TestTokens.OWNER).when().put(BASE + "/notes/" + noteId + "/labels/" + drop).then().statusCode(200);

        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + noteId + "/labels/" + drop).then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + noteId)
                .then()
                .body("labelIds", hasItem(keep))
                .body("labelIds", not(hasItem(drop)));
    }

    @Test
    void labelsSurviveTheTrashRoundTripWithTheirNote() {
        // SC-006: restoring a note recovers its organisation, labels included.
        String labelId = createLabel(TestTokens.OWNER);
        String noteId = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().put(BASE + "/notes/" + noteId + "/labels/" + labelId).then().statusCode(200);

        as(TestTokens.OWNER).when().post(BASE + "/notes/" + noteId + "/trash").then().statusCode(200);
        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + noteId + "/trash")
                .then()
                .statusCode(200)
                .body("labelIds", hasItem(labelId));
    }
}
