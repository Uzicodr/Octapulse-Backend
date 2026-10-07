package com.octapulse.backend.service;

import com.octapulse.backend.config.JwtProperties;
import com.octapulse.backend.domain.PasswordResetToken;
import com.octapulse.backend.domain.RefreshToken;
import com.octapulse.backend.domain.User;
import com.octapulse.backend.dto.AuthDto.TokenResponse;
import com.octapulse.backend.repository.PasswordResetTokenRepository;
import com.octapulse.backend.repository.RefreshTokenRepository;
import com.octapulse.backend.repository.UserRepository;
import com.octapulse.backend.security.JwtService;
import com.octapulse.backend.security.PasswordService;
import com.octapulse.backend.security.RefreshTokenService;
import com.octapulse.backend.service.notify.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private static final long PASSWORD_RESET_MINUTES = 30;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordService passwordService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailSender emailSender;
    private final String frontendUrl;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordService passwordService,
            RefreshTokenService refreshTokenService,
            JwtService jwtService,
            JwtProperties jwtProperties,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailSender emailSender,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordService = passwordService;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailSender = emailSender;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public TokenResponse signup(String email, String username, String password) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordService.hash(password));
        user.setCreatedAt(Instant.now());
        user = userRepository.save(user);
        return issueTokens(user.getId());
    }

    @Transactional
    public TokenResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (user.getPasswordHash() == null || !passwordService.verify(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return issueTokens(user.getId());
    }

    @Transactional
    public TokenResponse loginWithGoogle(String googleSub, String email) {
        User user = userRepository.findByGoogleSub(googleSub).orElseGet(() -> {
            User u = new User();
            u.setGoogleSub(googleSub);
            u.setEmail(email);
            u.setUsername(deriveUsername(email));
            u.setCreatedAt(Instant.now());
            return userRepository.save(u);
        });
        return issueTokens(user.getId());
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        String hash = refreshTokenService.hash(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .filter(t -> t.getRevokedAt() == null && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));

        token.setRevokedAt(Instant.now());
        refreshTokenRepository.save(token);
        return issueTokens(token.getUserId());
    }

    /** Revokes one refresh token. Unknown or already revoked tokens are ignored. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(refreshTokenService.hash(rawRefreshToken))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> {
                    t.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(t);
                });
    }

    @Transactional
    public void logoutEverywhere(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    /** Always succeeds from the caller's view so the endpoint can't be used to probe for accounts. */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            RefreshTokenService.RawAndHash pair = refreshTokenService.generate();
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(user.getId());
            token.setTokenHash(pair.hash());
            token.setExpiresAt(Instant.now().plus(PASSWORD_RESET_MINUTES, ChronoUnit.MINUTES));
            passwordResetTokenRepository.save(token);
            String link = frontendUrl + "/reset-password?token=" + pair.raw();
            emailSender.send(user.getEmail(), "Reset your Octapulse password",
                    "Use this link within " + PASSWORD_RESET_MINUTES + " minutes to choose a new password:\n\n" + link
                            + "\n\nIf you didn't ask for this, ignore this email.");
        });
    }

    /** Sets the new password and signs the user out of every device. */
    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(refreshTokenService.hash(rawToken))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token"));
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token"));
        user.setPasswordHash(passwordService.hash(newPassword));
        userRepository.save(user);
        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
    }

    private TokenResponse issueTokens(UUID userId) {
        String accessToken = jwtService.createAccessToken(userId.toString());
        RefreshTokenService.RawAndHash pair = refreshTokenService.generate();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(pair.hash());
        refreshToken.setExpiresAt(Instant.now().plus(jwtProperties.refreshTokenDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);

        return new TokenResponse(accessToken, pair.raw());
    }

    private String deriveUsername(String email) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");
        String candidate = base;
        int suffix = 0;
        while (userRepository.existsByUsernameIgnoreCase(candidate)) {
            suffix++;
            candidate = base + suffix;
        }
        return candidate;
    }
}
