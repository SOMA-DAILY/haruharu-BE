package org.son.haruharu.admin.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 어드민 목록 공통 페이지 응답. 0-based page. */
public record PageResponse<T>(
        @Schema(description = "현재 페이지 항목") List<T> content,
        @Schema(description = "페이지 메타") PageMeta page
) {
    public record PageMeta(
            @Schema(description = "0-based 페이지 번호", example = "0") int page,
            @Schema(description = "페이지 크기", example = "20") int size,
            @Schema(description = "전체 항목 수", example = "137") long totalElements,
            @Schema(description = "전체 페이지 수", example = "7") int totalPages
    ) {}

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, new PageMeta(page, size, totalElements, totalPages));
    }
}
