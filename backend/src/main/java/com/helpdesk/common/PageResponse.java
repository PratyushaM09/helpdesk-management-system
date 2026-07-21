package com.helpdesk.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * The fixed paginated-list envelope every list endpoint returns
 * (04-API-Design.md §1: {@code { content, page, size, totalElements,
 * totalPages }}), wrapped as the {@code data} of an {@link ApiResponse}.
 * Deliberately not {@code Page<T>} itself — Spring Data's {@code Page}
 * carries extra, implementation-leaking fields (e.g. {@code pageable},
 * {@code sort}) that are not part of the reviewed API contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
