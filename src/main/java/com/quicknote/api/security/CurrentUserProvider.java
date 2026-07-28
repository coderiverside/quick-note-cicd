package com.quicknote.api.security;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Single source of truth for "who is calling".
 *
 * <p>The owner id is the {@code sub} claim of the Keycloak-issued JWT, taken from
 * the already-validated {@link SecurityIdentity}. Reading it from anywhere else —
 * a header, a path segment, a request body — would let a caller name a different
 * owner, so this is the only place the application answers that question.
 */
@RequestScoped
public class CurrentUserProvider {

    @Inject
    SecurityIdentity securityIdentity;

    public String ownerId() {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            throw new UnauthorizedException();
        }
        String subject = securityIdentity.getPrincipal().getName();
        if (subject == null || subject.isBlank()) {
            throw new UnauthorizedException();
        }
        return subject;
    }
}
