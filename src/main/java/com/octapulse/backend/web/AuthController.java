package com.octapulse.backend.web;

import com.octapulse.backend.dto.AuthDto.*;
import com.octapulse.backend.security.GoogleTokenVerifier;
import com.octapulse.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthController(AuthService authService, GoogleTokenVerifier googleTokenVerifier) {
        this.authService = authService;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    @PostMapping("/signup")
    public TokenResponse signup(@Valid @RequestBody SignupRequest req) {
        return authService.signup(req.email(), req.username(), req.password());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req.email(), req.password());
    }

    @PostMapping("/google")
    public TokenResponse google(@Valid @RequestBody GoogleRequest req) {
        try {
            var payload = googleTokenVerifier.verify(req.idToken());
            return authService.loginWithGoogle(payload.getSubject(), payload.getEmail());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token");
        }
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }
}
