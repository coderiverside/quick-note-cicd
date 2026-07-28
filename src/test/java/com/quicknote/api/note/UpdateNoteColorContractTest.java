package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for colour assignment via {@code PATCH /api/v1/notes/{noteId}} (FR-005). */
@QuarkusTest
class UpdateNoteColorContractTest {

    @Test
    void newNoteDefaultsToNoColour() {
        String id = createNote(TestTokens.OWNER);

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + id).then().body("color", is("DEFAULT"));
    }

    @Test
    void assignsAColourFromThePalette() {
        String id = createNote(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .body("{\"color\":\"YELLOW\"}")
                .when()
                .patch(BASE + "/notes/" + id)
                .then()
                .statusCode(200)
                .body("color", is("YELLOW"));
    }

    @Test
    void colourPersistsAcrossRetrieval() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).body("{\"color\":\"TEAL\"}").when().patch(BASE + "/notes/" + id).then().statusCode(200);

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + id).then().body("color", is("TEAL"));
    }

    @Test
    void colourCanBeSetAtCreationTime() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Coloured\",\"color\":\"BLUE\"}");

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + id).then().body("color", is("BLUE"));
    }

    @Test
    void clearsColourBackToDefault() {
        // FR-005 requires being able to return a note to the no-colour state.
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Coloured\",\"color\":\"RED\"}");

        as(TestTokens.OWNER)
                .body("{\"color\":\"DEFAULT\"}")
                .when()
                .patch(BASE + "/notes/" + id)
                .then()
                .statusCode(200)
                .body("color", is("DEFAULT"));
    }

    @Test
    void rejectsAColourOutsideThePalette() {
        String id = createNote(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .body("{\"color\":\"NEON_TURQUOISE\"}")
                .when()
                .patch(BASE + "/notes/" + id)
                .then()
                .statusCode(400);
    }

    @Test
    void changingColourLeavesTitleAndContentAlone() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Keep\",\"content\":\"both\"}");

        as(TestTokens.OWNER)
                .body("{\"color\":\"GREEN\"}")
                .when()
                .patch(BASE + "/notes/" + id)
                .then()
                .statusCode(200)
                .body("title", is("Keep"))
                .body("content", is("both"))
                .body("color", is("GREEN"));
    }
}
