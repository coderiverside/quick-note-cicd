package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/**
 * User Story 2 end to end: pinning and colour survive a round trip through
 * PostgreSQL and are visible in listings.
 */
@QuarkusTest
class NoteOrganizationIT {

    @Test
    void pinAndColourPersistTogether() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Important\",\"content\":\"do this\"}");

        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/pin").then().statusCode(200);
        as(TestTokens.OWNER).body("{\"color\":\"ORANGE\"}").when().patch(BASE + "/notes/" + id).then().statusCode(200);

        as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + id)
                .then()
                .statusCode(200)
                .body("pinned", is(true))
                .body("color", is("ORANGE"))
                .body("title", is("Important"));
    }

    @Test
    void pinnedNotesAreDistinguishableInTheListing() {
        String pinned = createNote(TestTokens.OWNER, "{\"title\":\"pinned-note\"}");
        createNote(TestTokens.OWNER, "{\"title\":\"unpinned-note\"}");

        as(TestTokens.OWNER).when().post(BASE + "/notes/" + pinned + "/pin").then().statusCode(200);

        List<String> pinnedIds = as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .extract()
                .path("content.findAll { it.pinned == true }.id");

        org.junit.jupiter.api.Assertions.assertTrue(pinnedIds.contains(pinned),
                "the pinned note should be identifiable as pinned in the listing");
    }

    @Test
    void pinnedNotesSortAheadOfUnpinnedOnes() {
        // Pinned notes surface first so the listing itself carries the priority
        // signal, rather than every client having to re-sort.
        String unpinned = createNote(TestTokens.OTHER_USER, "{\"title\":\"sort-unpinned\"}");
        String pinned = createNote(TestTokens.OTHER_USER, "{\"title\":\"sort-pinned\"}");
        as(TestTokens.OTHER_USER).when().post(BASE + "/notes/" + pinned + "/pin").then().statusCode(200);

        List<String> ids = as(TestTokens.OTHER_USER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .extract()
                .path("content.id");

        org.junit.jupiter.api.Assertions.assertTrue(ids.indexOf(pinned) < ids.indexOf(unpinned),
                "pinned notes should be listed before unpinned ones");
    }

    @Test
    void unpinningRestoresNormalPriority() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/pin").then().statusCode(200);
        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + id + "/pin").then().statusCode(200);

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + id).then().body("pinned", is(false));

        as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .body("content.id", hasItem(id));
    }
}
