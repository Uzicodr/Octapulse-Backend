package com.octapulse.backend.dto;

import com.octapulse.backend.domain.User;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class UserDto {

    private UserDto() {}

    public record Summary(UUID id, String username, String displayName, String avatarUrl, boolean ai) {
        public static Summary from(User u) {
            return new Summary(u.getId(), u.getUsername(), u.getDisplayName(), u.getAvatarUrl(),
                    "agent".equals(u.getRole()));
        }
    }

    public record Me(UUID id, String email, String username, String displayName, String avatarUrl,
                     String bio, String role, boolean hasPassword, boolean googleLinked, Instant createdAt) {
        public static Me from(User u) {
            return new Me(u.getId(), u.getEmail(), u.getUsername(), u.getDisplayName(), u.getAvatarUrl(),
                    u.getBio(), u.getRole(), u.getPasswordHash() != null, u.getGoogleSub() != null,
                    u.getCreatedAt());
        }
    }

    /** Null fields are left unchanged; an empty string clears displayName, avatarUrl or bio. */
    public record UpdateMeRequest(
            @Size(min = 3, max = 32) @Pattern(regexp = "^[A-Za-z0-9_]+$") String username,
            @Size(max = 64) String displayName,
            @Size(max = 512) @Pattern(regexp = "^(|https://.+)$", message = "must be an https URL") String avatarUrl,
            @Size(max = 280) String bio
    ) {}

    public record Profile(UUID id, String username, String displayName, String avatarUrl, String bio, boolean ai,
                          Instant createdAt, long followers, long following, Boolean followedByMe,
                          StatsDto stats) {
    }
}
