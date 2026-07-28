package com.quicknote.api;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Shared request setup for API tests.
 *
 * <p>Tests assert on the specific resources they create rather than on absolute
 * collection counts, because every test in a run shares one database.
 */
public final class Api {

    public static final String BASE = "/api/v1";

    private Api() {
    }

    /** A JSON request carrying a real Keycloak token for the given realm user. */
    public static RequestSpecification as(String user) {
        return given().header("Authorization", TestTokens.bearer(user)).contentType(ContentType.JSON);
    }

    /** A JSON request with no {@code Authorization} header at all. */
    public static RequestSpecification anonymous() {
        return given().contentType(ContentType.JSON);
    }

    /** Creates a note for the user and returns its id. */
    public static String createNote(String user, String body) {
        return as(user).body(body).when().post(BASE + "/notes").then().statusCode(201).extract().path("id");
    }

    /** Creates a simple titled note for the user and returns its id. */
    public static String createNote(String user) {
        return createNote(user, "{\"title\":\"note-" + java.util.UUID.randomUUID() + "\"}");
    }
}
