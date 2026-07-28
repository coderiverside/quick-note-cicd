package com.quicknote.api.label;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code POST /api/v1/labels} (FR-016, FR-021). */
@QuarkusTest
class CreateLabelContractTest {

    private static String uniqueName() {
        return "label-" + UUID.randomUUID();
    }

    @Test
    void createsALabel() {
        String name = uniqueName();

        as(TestTokens.OWNER)
                .body("{\"name\":\"" + name + "\"}")
                .when()
                .post(BASE + "/labels")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", is(name))
                .body("createdAt", notNullValue());
    }

    @Test
    void rejectsADuplicateName() {
        String name = uniqueName();
        as(TestTokens.OWNER).body("{\"name\":\"" + name + "\"}").when().post(BASE + "/labels").then().statusCode(201);

        as(TestTokens.OWNER)
                .body("{\"name\":\"" + name + "\"}")
                .when()
                .post(BASE + "/labels")
                .then()
                .statusCode(409)
                .body("error.code", is("CONFLICT"));
    }

    @Test
    void rejectsADuplicateNameDifferingOnlyByCase() {
        // FR-021 makes uniqueness case-insensitive, so "Work" and "work" are the
        // same label rather than two confusingly similar ones.
        String name = uniqueName();
        as(TestTokens.OWNER).body("{\"name\":\"" + name + "\"}").when().post(BASE + "/labels").then().statusCode(201);

        as(TestTokens.OWNER)
                .body("{\"name\":\"" + name.toUpperCase() + "\"}")
                .when()
                .post(BASE + "/labels")
                .then()
                .statusCode(409);
    }

    @Test
    void allowsTwoUsersToUseTheSameLabelName() {
        // Uniqueness is per owner: one user's label vocabulary is their own.
        String name = uniqueName();

        as(TestTokens.OWNER).body("{\"name\":\"" + name + "\"}").when().post(BASE + "/labels").then().statusCode(201);
        as(TestTokens.OTHER_USER)
                .body("{\"name\":\"" + name + "\"}")
                .when()
                .post(BASE + "/labels")
                .then()
                .statusCode(201);
    }

    @Test
    void rejectsABlankName() {
        as(TestTokens.OWNER)
                .body("{\"name\":\"   \"}")
                .when()
                .post(BASE + "/labels")
                .then()
                .statusCode(400)
                .body("error.code", is("VALIDATION_ERROR"));
    }

    @Test
    void rejectsAMissingName() {
        as(TestTokens.OWNER).body("{}").when().post(BASE + "/labels").then().statusCode(400);
    }

    @Test
    void rejectsAnonymousRequest() {
        anonymous().body("{\"name\":\"x\"}").when().post(BASE + "/labels").then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        as(TestTokens.NO_ROLE_USER).body("{\"name\":\"x\"}").when().post(BASE + "/labels").then().statusCode(403);
    }
}
