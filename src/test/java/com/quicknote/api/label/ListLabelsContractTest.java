package com.quicknote.api.label;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;

/** Contract for {@code GET /api/v1/labels}. */
@QuarkusTest
class ListLabelsContractTest {

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
    void listsOwnLabelsInAPaginatedEnvelope() {
        String id = createLabel(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/labels")
                .then()
                .statusCode(200)
                .body("page", is(0))
                .body("totalElements", greaterThanOrEqualTo(1))
                .body("content.id", hasItem(id));
    }

    @Test
    void honoursPageAndSizeParameters() {
        createLabel(TestTokens.OWNER);
        createLabel(TestTokens.OWNER);

        as(TestTokens.OWNER)
                .queryParam("page", 0)
                .queryParam("size", 1)
                .when()
                .get(BASE + "/labels")
                .then()
                .statusCode(200)
                .body("size", is(1))
                .body("content", hasSize(lessThanOrEqualTo(1)));
    }

    @Test
    void rejectsPageSizeAboveTheConfiguredMaximum() {
        as(TestTokens.OWNER).queryParam("size", 5000).when().get(BASE + "/labels").then().statusCode(400);
    }

    @Test
    void neverReturnsAnotherUsersLabels() {
        String othersLabel = createLabel(TestTokens.OTHER_USER);

        as(TestTokens.OWNER)
                .queryParam("size", 100)
                .when()
                .get(BASE + "/labels")
                .then()
                .body("content.id", not(hasItem(othersLabel)));
    }

    @Test
    void rejectsAnonymousRequest() {
        anonymous().when().get(BASE + "/labels").then().statusCode(401);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutUserRole() {
        as(TestTokens.NO_ROLE_USER).when().get(BASE + "/labels").then().statusCode(403);
    }
}
