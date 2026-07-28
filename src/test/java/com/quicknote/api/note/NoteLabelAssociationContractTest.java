package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code PUT}/{@code DELETE /api/v1/notes/{noteId}/labels/{labelId}} (FR-019). */
@QuarkusTest
class NoteLabelAssociationContractTest {

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
    void appliesALabelToANote() {
        String noteId = createNote(TestTokens.OWNER);
        String labelId = createLabel(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .when()
                .put(BASE + "/notes/" + noteId + "/labels/" + labelId)
                .then()
                .statusCode(200)
                .body("labelIds", hasItem(labelId));
    }

    @Test
    void appliedLabelIsVisibleWhenTheNoteIsRetrieved() {
        String noteId = createNote(TestTokens.OWNER);
        String labelId = createLabel(TestTokens.OWNER);
        as(TestTokens.OWNER).when().put(BASE + "/notes/" + noteId + "/labels/" + labelId).then().statusCode(200);

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + noteId).then().body("labelIds", hasItem(labelId));
    }

    @Test
    void applyingTheSameLabelTwiceIsANoOp() {
        String noteId = createNote(TestTokens.OWNER);
        String labelId = createLabel(TestTokens.OWNER);

        as(TestTokens.OWNER).when().put(BASE + "/notes/" + noteId + "/labels/" + labelId).then().statusCode(200);
        as(TestTokens.OWNER)
                .when()
                .put(BASE + "/notes/" + noteId + "/labels/" + labelId)
                .then()
                .statusCode(200)
                .body("labelIds", hasSize(1));
    }

    @Test
    void appliesSeveralLabelsToOneNote() {
        String noteId = createNote(TestTokens.OWNER);
        String first = createLabel(TestTokens.OWNER);
        String second = createLabel(TestTokens.OWNER);

        as(TestTokens.OWNER).when().put(BASE + "/notes/" + noteId + "/labels/" + first).then().statusCode(200);
        as(TestTokens.OWNER)
                .when()
                .put(BASE + "/notes/" + noteId + "/labels/" + second)
                .then()
                .statusCode(200)
                .body("labelIds", hasItem(first))
                .body("labelIds", hasItem(second));
    }

    @Test
    void removesALabelFromANoteWithoutDeletingTheLabel() {
        String noteId = createNote(TestTokens.OWNER);
        String labelId = createLabel(TestTokens.OWNER);
        as(TestTokens.OWNER).when().put(BASE + "/notes/" + noteId + "/labels/" + labelId).then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + noteId + "/labels/" + labelId)
                .then()
                .statusCode(200)
                .body("labelIds", not(hasItem(labelId)));

        // The label itself survives for use on other notes (FR-019).
        as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/labels")
                .then()
                .body("content.id", hasItem(labelId));
    }

    @Test
    void removingALabelThatIsNotAppliedSucceeds() {
        String noteId = createNote(TestTokens.OWNER);
        String labelId = createLabel(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + noteId + "/labels/" + labelId)
                .then()
                .statusCode(200)
                .body("labelIds", not(hasItem(labelId)));
    }

    @Test
    void returnsNotFoundForAnUnknownNote() {
        String labelId = createLabel(TestTokens.OWNER);
        as(TestTokens.OWNER)
                .when()
                .put(BASE + "/notes/" + UUID.randomUUID() + "/labels/" + labelId)
                .then()
                .statusCode(404);
    }

    @Test
    void returnsNotFoundForAnUnknownLabel() {
        String noteId = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER)
                .when()
                .put(BASE + "/notes/" + noteId + "/labels/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void refusesToApplyAnotherUsersLabel() {
        // Neither half of the pair may cross an ownership boundary.
        String noteId = createNote(TestTokens.OWNER);
        String othersLabel = createLabel(TestTokens.OTHER_USER);

        as(TestTokens.OWNER)
                .when()
                .put(BASE + "/notes/" + noteId + "/labels/" + othersLabel)
                .then()
                .statusCode(404);
    }

    @Test
    void refusesToLabelAnotherUsersNote() {
        String othersNote = createNote(TestTokens.OTHER_USER);
        String labelId = createLabel(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .when()
                .put(BASE + "/notes/" + othersNote + "/labels/" + labelId)
                .then()
                .statusCode(404);
    }

    @Test
    void rejectsAnonymousRequest() {
        String noteId = createNote(TestTokens.OWNER);
        String labelId = createLabel(TestTokens.OWNER);
        anonymous().when().put(BASE + "/notes/" + noteId + "/labels/" + labelId).then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        String noteId = createNote(TestTokens.OWNER);
        String labelId = createLabel(TestTokens.OWNER);
        as(TestTokens.NO_ROLE_USER)
                .when()
                .put(BASE + "/notes/" + noteId + "/labels/" + labelId)
                .then()
                .statusCode(403);
    }
}
