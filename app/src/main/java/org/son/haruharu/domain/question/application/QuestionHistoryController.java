package org.son.haruharu.domain.question.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.question.business.QuestionService;
import org.son.haruharu.domain.question.business.dto.QuestionHistoryResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "익명 질문")
@RestController
@RequestMapping("/api/v1/questions/rooms")
@RequiredArgsConstructor
public class QuestionHistoryController {
    private final QuestionService questionService;

    @Operation(summary = "종료·취소 방의 익명 질문 기록 목록")
    @GetMapping
    public ApiResponse<QuestionHistoryResponse> history(
            Authentication auth,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(questionService.history((Long) auth.getPrincipal(), cursor, size));
    }
}
