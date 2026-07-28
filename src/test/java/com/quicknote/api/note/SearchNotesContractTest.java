package com.quicknote.api.note;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code GET /api/v1/notes?search=} (FR-026). */
@QuarkusTest
class SearchNotesContractTest {

    /** A term that cannot collide with notes created by other tests. */
    private static String uniqueTerm() {
        return "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @Test
    void findsANoteByAWordInItsTitle() {
        String term = uniqueTerm();
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Meeting about " + term + "\"}");

        as(TestTokens.OWNER)
                .queryParam("search", term)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(id));
    }

    @Test
    void findsANoteByAWordInItsContent() {
        String term = uniqueTerm();
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Notes\",\"content\":\"Remember the " + term + "\"}");

        as(TestTokens.OWNER)
                .queryParam("search", term)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(id));
    }

    @Test
    void searchIsCaseInsensitive() {
        String term = uniqueTerm();
        String id = createNote(TestTokens.OWNER, "{\"title\":\"About " + term.toUpperCase() + "\"}");

        as(TestTokens.OWNER)
                .queryParam("search", term.toLowerCase())
                .when()
                .get(BASE + "/notes")
                .then()
                .body("content.id", hasItem(id));
    }

    @Test
    void returnsAnEmptyPageWhenNothingMatches() {
        as(TestTokens.OWNER)
                .queryParam("search", uniqueTerm())
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content", org.hamcrest.Matchers.empty())
                .body("totalElements", is(0));
    }

    @Test
    void excludesTrashedNotesFromResults() {
        // FR-026: search is for finding things you still have.
        String term = uniqueTerm();
        String id = createNote(TestTokens.OWNER, "{\"title\":\"Discarded " + term + "\"}");
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("search", term)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", not(hasItem(id)));
    }

    @Test
    void neverReturnsAnotherUsersMatchingNotes() {
        String term = uniqueTerm();
        String othersNote = createNote(TestTokens.OTHER_USER, "{\"title\":\"Secret " + term + "\"}");

        as(TestTokens.OWNER)
                .queryParam("search", term)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", not(hasItem(othersNote)));
    }

    @Test
    void searchResultsArePaginated() {
        String term = uniqueTerm();
        createNote(TestTokens.OWNER, "{\"title\":\"first " + term + "\"}");
        createNote(TestTokens.OWNER, "{\"title\":\"second " + term + "\"}");

        as(TestTokens.OWNER)
                .queryParam("search", term)
                .queryParam("size", 1)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("size", is(1))
                .body("content", org.hamcrest.Matchers.hasSize(1))
                .body("totalElements", is(2));
    }

    @Test
    void aBlankSearchTermBehavesLikeNoSearch() {
        String id = createNote(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .queryParam("search", "   ")
                .queryParam("size", 100)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(id));
    }

    @Test
    void combinesWithTheArchivedView() {
        String term = uniqueTerm();
        String archived = createNote(TestTokens.OWNER, "{\"title\":\"Filed " + term + "\"}");
        String active = createNote(TestTokens.OWNER, "{\"title\":\"Current " + term + "\"}");
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + archived + "/archive").then().statusCode(200);

        as(TestTokens.OWNER)
                .queryParam("search", term)
                .queryParam("view", "archived")
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200)
                .body("content.id", hasItem(archived))
                .body("content.id", not(hasItem(active)));
    }

    @Test
    void toleratesPunctuationInTheSearchTerm() {
        // A raw tsquery would reject this; the term has to be sanitised first.
        as(TestTokens.OWNER)
                .queryParam("search", "hello & | ! world :*")
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(200);
    }

    @Test
    void rejectsAnonymousRequest() {
        anonymous().queryParam("search", "anything").when().get(BASE + "/notes").then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        as(TestTokens.NO_ROLE_USER).queryParam("search", "anything").when().get(BASE + "/notes").then()
                .statusCode(403);
    }
}
