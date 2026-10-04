package com.octapulse.backend.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh tokens are 384-bit random values, not user-chosen secrets, so a fast
 * deterministic digest is sufficient for storage and gives us an indexed lookup
 * (no per-row Argon2 verify scan over every live token).
 */
@Service
public class RefreshTokenService {

    private final SecureRandom random = new SecureRandom();

    public record RawAndHash(String raw, String hash) {
    }

    public RawAndHash generate() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new RawAndHash(raw, hash(raw));
    }

    public String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
