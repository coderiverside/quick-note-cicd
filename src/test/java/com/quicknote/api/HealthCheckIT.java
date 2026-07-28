package com.quicknote.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Operational readiness gate (constitution Principle V).
 *
 * <p>Runs against Dev Services: a real PostgreSQL container (so the readiness
 * probe genuinely exercises the datasource) and a real Keycloak container.
 */
@QuarkusTest
class HealthCheckIT {

    @Test
    void livenessProbeReportsUp() {
        given()
                .when()
                .get("/q/health/live")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void readinessProbeReportsUp() {
        given()
                .when()
                .get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void healthEndpointsAreReachableWithoutAuthentication() {
        // Probes are called by the orchestrator, which holds no token; the RBAC
        // gate must apply to application endpoints only.
        given()
                .when()
                .get("/q/health")
                .then()
                .statusCode(200);
    }
}
