package com.octapulse.backend.dto;

public record PageDto(int page, int limit, long total, int totalPages) {
}
