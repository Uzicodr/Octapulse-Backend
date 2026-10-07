package com.octapulse.backend.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PagedResponse<T>(List<T> data, PageDto meta) {

    /** page is 1-based, matching the request parameter. */
    public static <E, T> PagedResponse<T> of(Page<E> result, int page, int limit, Function<E, T> mapper) {
        return new PagedResponse<>(
                result.getContent().stream().map(mapper).toList(),
                new PageDto(page, limit, result.getTotalElements(), result.getTotalPages())
        );
    }

    public static <T> PagedResponse<T> of(List<T> data, int page, int limit, long total) {
        int totalPages = limit == 0 ? 0 : (int) ((total + limit - 1) / limit);
        return new PagedResponse<>(data, new PageDto(page, limit, total, totalPages));
    }
}
