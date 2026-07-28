package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Full-text search against real PostgreSQL.
 *
 * <p>These behaviours come from {@code tsvector}/{@code tsquery} specifically —
 * stemming and word-boundary matching are exactly what an in-memory database or
 * a {@code LIKE '%term%'} fallback would not reproduce (research.md 6, 12).
 */
@QuarkusTest
class NoteSearchIT {

    private static String uniqueTerm() {
        return "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @Test
    void matchesOnWordStemsRatherThanExactStringsOnly() {
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Planning the migration\"}");

        // "migrate" and "migration" share a stem, so an index-backed text search
        // finds the note even though the strings differ.
        as(TestTokens.OWNER)
                .queryParam("search", "migrate")
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(id));
    }

    @Test
    void matchesWholeWordsNotArbitrarySubstrings() {
        String term = uniqueTerm();
        String id = createNote(TestTokens.OWNER, "{\"title\":\"" + term + "\"}");

        // A fragment of the middle of a word is not a word, so it must not match.
        as(TestTokens.OWNER)
                .queryParam("search", term.substring(4, 9))
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", not(hasItem(id)));
    }

    @Test
    void findsNotesByAnyOfSeveralWords() {
        String term = uniqueTerm();
        String titled = createNote(TestTokens.OWNER, "{\"title\":\"" + term + " groceries\"}");
        String bodied = createNote(TestTokens.OWNER, "{\"content\":\"" + term + " hardware\"}");

        as(TestTokens.OWNER)
                .queryParam("search", term)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(titled))
                .body("content.id", hasItem(bodied));
    }

    @Test
    void reflectsEditsImmediately() {
        // The search vector is a generated column, so it cannot fall behind edits.
        String before = uniqueTerm();
        String after = uniqueTerm();
        String id = createNote(TestTokens.OWNER, "{\"title\":\"" + before + "\"}");

        as(TestTokens.OWNER).body("{\"title\":\"" + after + "\"}").when().patch(BASE + "/notes/" + id).then()
                .statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("search", after)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .body("content.id", hasItem(id));

        as(TestTokens.OWNER)
                .queryParam("search", before)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .body("content.id", not(hasItem(id)));
    }

    @Test
    void combinesSearchWithLabelFiltering() {
        String term = uniqueTerm();
        String labelId = as(TestTokens.OWNER)
                .body("{\"name\":\"label-" + UUID.randomUUID() + "\"}")
                .when()
                .post(BASE + "/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String labelled = createNote(TestTokens.OWNER, "{\"title\":\"" + term + " labelled\"}");
        String unlabelled = createNote(TestTokens.OWNER, "{\"title\":\"" + term + " plain\"}");
        as(TestTokens.OWNER).when().put(BASE + "/notes/" + labelled + "/labels/" + labelId).then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("search", term)
                .queryParam("label", labelId)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(labelled))
                .body("content.id", not(hasItem(unlabelled)));
    }
}
