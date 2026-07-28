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

/** Contract for {@code DELETE /api/v1/notes/{noteId}} — permanent delete (FR-004, FR-013). */
@QuarkusTest
class DeleteNoteContractTest {

    @Test
    void permanentlyDeletesATrashedNote() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);

        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + id).then().statusCode(204);
    }

    @Test
    void deletedNoteIsUnretrievableThroughAnyView() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.OWNER).when().post(BASE + "/notes/" + id + "/trash").then().statusCode(200);
        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + id).then().statusCode(204);

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + id).then().statusCode(404);

        for (String view : new String[] {"active", "archived", "trashed"}) {
            as(TestTokens.OWNER)
                    .queryParam("view", view)
                    .queryParam("size", 100)
                    .when()
                    .get(BASE + "/notes")
                    .then()
                    .body("content.id", not(hasItem(id)));
        }
    }

    @Test
    void refusesToPermanentlyDeleteANoteThatIsNotTrashed() {
        // FR-004: permanent deletion is only reachable from the trash, so a
        // single mistaken call cannot destroy a live note.
        String id = createNote(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + id)
                .then()
                .statusCode(409)
                .body("error.code", is("CONFLICT"));

        as(TestTokens.OWNER).when().get(BASE + "/notes/" + id).then().statusCode(200);
    }

    @Test
    void returnsNotFoundForUnknownId() {
        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + UUID.randomUUID()).then().statusCode(404);
    }

    @Test
    void returnsNotFoundForAnotherUsersNote() {
        String othersNote = createNote(TestTokens.OTHER_USER);
        as(TestTokens.OTHER_USER).when().post(BASE + "/notes/" + othersNote + "/trash").then().statusCode(200);

        as(TestTokens.OWNER).when().delete(BASE + "/notes/" + othersNote).then().statusCode(404);

        // ...and it is still there for its actual owner.
        as(TestTokens.OTHER_USER).when().get(BASE + "/notes/" + othersNote).then().statusCode(200);
    }

    @Test
    void rejectsAnonymousRequest() {
        String id = createNote(TestTokens.OWNER);
        anonymous().when().delete(BASE + "/notes/" + id).then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        String id = createNote(TestTokens.OWNER);
        as(TestTokens.NO_ROLE_USER).when().delete(BASE + "/notes/" + id).then().statusCode(403);
    }
}
