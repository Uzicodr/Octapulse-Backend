package com.octapulse.backend.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Reads the bearer token into the userId request attribute. When required, a missing or
 * invalid token is a 401; otherwise the request continues anonymously.
 */
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTRIBUTE = "userId";

    private final JwtService jwtService;
    private final boolean required;

    public AuthInterceptor(JwtService jwtService) {
        this(jwtService, true);
    }

    public AuthInterceptor(JwtService jwtService, boolean required) {
        this.jwtService = jwtService;
        this.required = required;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getAttribute(USER_ID_ATTRIBUTE) != null) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            if (required) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
            return true;
        }
        try {
            String subject = jwtService.decodeSubject(header.substring(7));
            request.setAttribute(USER_ID_ATTRIBUTE, UUID.fromString(subject));
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            if (!required) {
                // Treat as anonymous; endpoints that need a user answer 401 themselves.
                return true;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }
}
