package com.quicknote.api.label;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code PATCH}/{@code DELETE /api/v1/labels/{labelId}} (FR-017). */
@QuarkusTest
class UpdateDeleteLabelContractTest {

    private static String uniqueName() {
        return "label-" + UUID.randomUUID();
    }

    private static String createLabel(String user, String name) {
        return as(user)
                .body("{\"name\":\"" + name + "\"}")
                .when()
                .post(BASE + "/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    void renamesALabel() {
        String id = createLabel(TestTokens.OWNER, uniqueName());
        String newName = uniqueName();

        as(TestTokens.OWNER)
                .body("{\"name\":\"" + newName + "\"}")
                .when()
                .patch(BASE + "/labels/" + id)
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("name", is(newName));
    }

    @Test
    void renameRejectsANameAlreadyUsedByAnotherLabel() {
        String taken = uniqueName();
        createLabel(TestTokens.OWNER, taken);
        String id = createLabel(TestTokens.OWNER, uniqueName());

        as(TestTokens.OWNER)
                .body("{\"name\":\"" + taken + "\"}")
                .when()
                .patch(BASE + "/labels/" + id)
                .then()
                .statusCode(409);
    }

    @Test
    void renamingALabelToItsOwnNameSucceeds() {
        String name = uniqueName();
        String id = createLabel(TestTokens.OWNER, name);

        as(TestTokens.OWNER)
                .body("{\"name\":\"" + name + "\"}")
                .when()
                .patch(BASE + "/labels/" + id)
                .then()
                .statusCode(200)
                .body("name", is(name));
    }

    @Test
    void rejectsABlankRename() {
        String id = createLabel(TestTokens.OWNER, uniqueName());

        as(TestTokens.OWNER).body("{\"name\":\"  \"}").when().patch(BASE + "/labels/" + id).then().statusCode(400);
    }

    @Test
    void deletesALabel() {
        String id = createLabel(TestTokens.OWNER, uniqueName());

        as(TestTokens.OWNER).when().delete(BASE + "/labels/" + id).then().statusCode(204);

        as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/labels")
                .then()
                .body("content.id", not(hasItem(id)));
    }

    @Test
    void deletedNameBecomesAvailableAgain() {
        String name = uniqueName();
        String id = createLabel(TestTokens.OWNER, name);
        as(TestTokens.OWNER).when().delete(BASE + "/labels/" + id).then().statusCode(204);

        as(TestTokens.OWNER)
                .body("{\"name\":\"" + name + "\"}")
                .when()
                .post(BASE + "/labels")
                .then()
                .statusCode(201);
    }

    @Test
    void returnsNotFoundForUnknownId() {
        as(TestTokens.OWNER)
                .body("{\"name\":\"x\"}")
                .when()
                .patch(BASE + "/labels/" + UUID.randomUUID())
                .then()
                .statusCode(404);
        as(TestTokens.OWNER).when().delete(BASE + "/labels/" + UUID.randomUUID()).then().statusCode(404);
    }

    @Test
    void returnsNotFoundForAnotherUsersLabel() {
        String othersLabel = createLabel(TestTokens.OTHER_USER, uniqueName());

        as(TestTokens.OWNER)
                .body("{\"name\":\"hijacked\"}")
                .when()
                .patch(BASE + "/labels/" + othersLabel)
                .then()
                .statusCode(404);
        as(TestTokens.OWNER).when().delete(BASE + "/labels/" + othersLabel).then().statusCode(404);
    }

    @Test
    void rejectsAnonymousRequest() {
        String id = createLabel(TestTokens.OWNER, uniqueName());
        anonymous().when().delete(BASE + "/labels/" + id).then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        String id = createLabel(TestTokens.OWNER, uniqueName());
        as(TestTokens.NO_ROLE_USER).when().delete(BASE + "/labels/" + id).then().statusCode(403);
    }
}
