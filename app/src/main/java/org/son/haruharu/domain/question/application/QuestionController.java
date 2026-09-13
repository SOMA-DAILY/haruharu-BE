package org.son.haruharu.domain.question.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.question.application.dto.*;
import org.son.haruharu.domain.question.business.QuestionService;
import org.son.haruharu.domain.question.business.dto.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "익명 질문", description = "방 시작 24시간 후 최종선택 전까지 익명 질문과 답변")
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/questions")
@RequiredArgsConstructor
public class QuestionController {
    private final QuestionService questionService;

    @Operation(summary = "익명 질문 상태 및 목록")
    @GetMapping
    public ApiResponse<QuestionListResponse> list(
            @PathVariable Long roomId,
            Authentication auth,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(
                questionService.list(roomId, (Long) auth.getPrincipal(), cursor, size));
    }

    @Operation(summary = "익명 질문 상세", description = "조회는 읽음을 변경하지 않습니다.")
    @GetMapping("/{id}")
    public ApiResponse<QuestionDetailResponse> detail(
            @PathVariable Long roomId, @PathVariable Long id, Authentication auth) {
        return ApiResponse.ok(questionService.detail(roomId, (Long) auth.getPrincipal(), id));
    }

    @Operation(summary = "익명 질문 전송", description = "request_id는 UUID이며 동일 요청 재시도에 재사용합니다.")
    @PostMapping
    public ApiResponse<QuestionDetailResponse> create(
            @PathVariable Long roomId,
            Authentication auth,
            @RequestBody @Valid CreateQuestionRequest request) {
        return ApiResponse.ok(
                questionService.create(roomId, (Long) auth.getPrincipal(), request.toCommand()));
    }

    @Operation(summary = "익명 질문 답변")
    @PostMapping("/{id}/answer")
    public ApiResponse<QuestionDetailResponse> answer(
            @PathVariable Long roomId,
            @PathVariable Long id,
            Authentication auth,
            @RequestBody @Valid AnswerQuestionRequest request) {
        return ApiResponse.ok(
                questionService.answer(
                        roomId, (Long) auth.getPrincipal(), id, request.toCommand()));
    }

    @Operation(summary = "질문 또는 답변 읽음")
    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(
            @PathVariable Long roomId, @PathVariable Long id, Authentication auth) {
        questionService.read(roomId, (Long) auth.getPrincipal(), id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "질문권 지급 소식 읽음")
    @PostMapping("/grant/read")
    public ApiResponse<Void> readGrant(@PathVariable Long roomId, Authentication auth) {
        questionService.readGrant(roomId, (Long) auth.getPrincipal());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "익명 질문 또는 답변 신고", description = "작성자 식별자는 서버에서만 결정합니다.")
    @PostMapping("/{id}/report")
    public ApiResponse<Void> report(
            @PathVariable Long roomId,
            @PathVariable Long id,
            Authentication auth,
            @RequestBody @Valid ReportQuestionRequest request) {
        questionService.report(roomId, (Long) auth.getPrincipal(), id, request.toCommand());
        return ApiResponse.ok(null);
    }
}
