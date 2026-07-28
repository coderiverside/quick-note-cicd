package com.quicknote.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.ConfigProvider;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

/**
 * Obtains real access tokens from the Dev Services Keycloak container.
 *
 * <p>Tests deliberately go through Keycloak's token endpoint rather than mocking
 * the OIDC layer: the realm, client and role-mapping configuration is exactly the
 * part most likely to be misconfigured, and mocking would leave it untested
 * (research.md 12).
 *
 * <p>Users come from {@code docker/keycloak/quicknote-realm.json}, which is the
 * same realm file docker-compose imports. Passwords equal usernames there.
 */
public final class TestTokens {

    /** Owns notes in most tests; holds the {@code user} realm role. */
    public static final String OWNER = "test-user";

    /** A second, distinct user with the {@code user} role — for isolation tests. */
    public static final String OTHER_USER = "other-user";

    /** Authenticates successfully but has no roles — for RBAC (403) tests. */
    public static final String NO_ROLE_USER = "no-role-user";

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private TestTokens() {
    }

    /** Bearer token for the given realm user, cached for the lifetime of the JVM. */
    public static String accessToken(String username) {
        return CACHE.computeIfAbsent(username, TestTokens::fetch);
    }

    /** Convenience: {@code Authorization} header value for the given user. */
    public static String bearer(String username) {
        return "Bearer " + accessToken(username);
    }

    private static String fetch(String username) {
        String authServerUrl = ConfigProvider.getConfig().getValue("quarkus.oidc.auth-server-url", String.class);
        String clientId = ConfigProvider.getConfig().getValue("quarkus.oidc.client-id", String.class);

        return RestAssured.given()
                .contentType(ContentType.URLENC)
                .formParam("grant_type", "password")
                .formParam("client_id", clientId)
                .formParam("username", username)
                .formParam("password", username)
                .when()
                .post(authServerUrl + "/protocol/openid-connect/token")
                .then()
                .statusCode(200)
                .extract()
                .path("access_token");
    }
}
