package com.quicknote.api.common.exception;

import static com.quicknote.api.Api.BASE;
import static com.quicknote.api.Api.anonymous;
import static com.quicknote.api.Api.as;
import static com.quicknote.api.Api.createNote;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quicknote.api.TestTokens;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;

/**
 * Every error response must be the shared envelope and nothing more.
 *
 * <p>Constitution Principle V: internal detail — stack traces, SQL, class names,
 * internal hostnames — must not cross the API boundary, because an error
 * response is the cheapest reconnaissance an attacker has.
 */
@QuarkusTest
class ErrorEnvelopeLeakageTest {

    /** Fragments that would indicate implementation detail escaping. */
    private static final String[] FORBIDDEN_FRAGMENTS = {
        "exception", "stacktrace", "at com.quicknote", "at io.quarkus", "at org.hibernate",
        "select ", "insert ", "update ", "jdbc", "postgres", "hibernate", "panache",
        "org.hibernate", "java.lang", "jakarta.", "caused by", "localhost:5432",
    };

    private static void assertNoLeakage(Response response) {
        String body = response.asString();
        if (body == null || body.isBlank()) {
            return;
        }
        String lowered = body.toLowerCase(Locale.ROOT);
        for (String fragment : FORBIDDEN_FRAGMENTS) {
            assertFalse(lowered.contains(fragment),
                    "error body leaked internal detail '" + fragment + "': " + body);
        }
    }

    @Test
    void notFoundIsAPlainEnvelope() {
        Response response = as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("error.code", is("NOT_FOUND"))
                .body("error.message", notNullValue())
                .extract()
                .response();

        assertNoLeakage(response);
    }

    @Test
    void validationFailureIsAPlainEnvelope() {
        Response response = as(TestTokens.OWNER)
                .body("{}")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(400)
                .body("error.code", is("VALIDATION_ERROR"))
                .extract()
                .response();

        assertNoLeakage(response);
    }

    @Test
    void unauthorizedIsAPlainEnvelope() {
        Response response = anonymous()
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(401)
                .body("error.code", is("UNAUTHORIZED"))
                .extract()
                .response();

        assertNoLeakage(response);
    }

    @Test
    void forbiddenIsAPlainEnvelope() {
        Response response = as(TestTokens.NO_ROLE_USER)
                .when()
                .get(BASE + "/notes")
                .then()
                .statusCode(403)
                .body("error.code", is("FORBIDDEN"))
                .extract()
                .response();

        assertNoLeakage(response);
    }

    @Test
    void conflictIsAPlainEnvelope() {
        String id = createNote(TestTokens.OWNER);

        Response response = as(TestTokens.OWNER)
                .when()
                .delete(BASE + "/notes/" + id)
                .then()
                .statusCode(409)
                .body("error.code", is("CONFLICT"))
                .extract()
                .response();

        assertNoLeakage(response);
    }

    @Test
    void malformedJsonDoesNotEchoParserInternals() {
        Response response = as(TestTokens.OWNER)
                .body("{\"title\": ")
                .when()
                .post(BASE + "/notes")
                .then()
                .statusCode(400)
                .extract()
                .response();

        assertNoLeakage(response);
    }

    @Test
    void unmatchedRouteDoesNotRevealTheFramework() {
        Response response = as(TestTokens.OWNER)
                .when()
                .get(BASE + "/notes/" + UUID.randomUUID() + "/nonexistent-subresource")
                .then()
                .statusCode(404)
                .extract()
                .response();

        assertNoLeakage(response);
    }

    @Test
    void unsupportedMethodDoesNotRevealTheFramework() {
        Response response = as(TestTokens.OWNER)
                .when()
                .put(BASE + "/notes")
                .then()
                .extract()
                .response();

        assertNoLeakage(response);
    }

    @Test
    void correlationIdIsEchoedSoAnOperatorCanFindTheLoggedDetail() {
        // The detail withheld from the caller still has to be findable; the
        // request id is the link between the two.
        as(TestTokens.OWNER)
                .header("X-Request-Id", "test-correlation-id")
                .when()
                .get(BASE + "/notes/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .header("X-Request-Id", is("test-correlation-id"));
    }

    @Test
    void aForgedCorrelationIdIsReplacedRatherThanReflected() {
        // An id containing control characters could forge log lines, so a value
        // that is not plainly alphanumeric is discarded.
        String forged = "abc\ndef injected";

        String echoed = as(TestTokens.OWNER)
                .header("X-Request-Id", forged)
                .when()
                .get(BASE + "/notes/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .extract()
                .header("X-Request-Id");

        assertFalse(forged.equals(echoed), "a correlation id with control characters must not be reflected");
    }
}
