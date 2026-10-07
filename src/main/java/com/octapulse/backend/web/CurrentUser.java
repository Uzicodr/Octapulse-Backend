package com.octapulse.backend.web;

import com.octapulse.backend.security.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

final class CurrentUser {

    private CurrentUser() {}

    /** The signed-in user, or null on public endpoints called anonymously. */
    static UUID optional(HttpServletRequest request) {
        return (UUID) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
    }

    static UUID require(HttpServletRequest request) {
        UUID userId = optional(request);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        }
        return userId;
    }

    static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), 100);
    }
}
