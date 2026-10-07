package com.octapulse.backend.security;

import com.octapulse.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/** Runs after AuthInterceptor. Role is read from the database so demotion takes effect immediately. */
public class AdminInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    public AdminInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UUID userId = (UUID) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
        boolean admin = userId != null && userRepository.findById(userId)
                .map(u -> "admin".equals(u.getRole()))
                .orElse(false);
        if (!admin) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }
}
