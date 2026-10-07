package com.octapulse.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.octapulse.backend.domain.Comment;
import com.octapulse.backend.domain.League;
import com.octapulse.backend.domain.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SocialDto {

    private SocialDto() {}

    public record CommentRequest(@NotBlank @Size(max = 1000) String body) {}

    public record CommentResponse(UUID id, UUID fightId, UserDto.Summary user, String body, Instant createdAt) {
        public static CommentResponse from(Comment c, UserDto.Summary user) {
            return new CommentResponse(c.getId(), c.getFightId(), user, c.getBody(), c.getCreatedAt());
        }
    }

    public record CreateLeagueRequest(@NotBlank @Size(max = 64) String name) {}

    public record JoinLeagueRequest(@NotBlank @Size(max = 16) String inviteCode) {}

    /** inviteCode is only included for members. */
    public record LeagueResponse(UUID id, String name, String inviteCode, UUID ownerId, long memberCount,
                                 Instant createdAt) {
        public static LeagueResponse from(League l, long memberCount) {
            return new LeagueResponse(l.getId(), l.getName(), l.getInviteCode(), l.getOwnerId(), memberCount,
                    l.getCreatedAt());
        }
    }

    public record LeagueDetail(LeagueResponse league, List<UserDto.Summary> members) {
    }

    public record FeedItem(UserDto.Summary user, PickDto.PickResponse pick, FightDto fight,
                           FightDto.EventSummary event) {
    }

    public record NotificationResponse(UUID id, String type, String title, String body, JsonNode data,
                                       Instant readAt, Instant createdAt) {
        public static NotificationResponse from(Notification n, JsonNode data) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), data,
                    n.getReadAt(), n.getCreatedAt());
        }
    }

    public record UnreadCount(long unread) {}

    public record DeviceRequest(@NotBlank @Size(max = 512) String token,
                                @NotBlank @Pattern(regexp = "^(ios|android|web)$") String platform) {}

    public record DeviceUnregisterRequest(@NotBlank String token) {}
}
