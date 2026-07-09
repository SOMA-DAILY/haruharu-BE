package org.son.haruharu.domain.value.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.value.application.dto.SaveValueAnswersRequest;
import org.son.haruharu.domain.value.business.ValueService;
import org.son.haruharu.domain.value.business.dto.MyValueAnswersResponse;
import org.son.haruharu.domain.value.business.dto.SavedAnswersResponse;
import org.son.haruharu.domain.value.business.dto.ValueQuestionsResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "가치관", description = "가치관 질문 조회 및 유저 답변 저장 (A/B 선택형)")
@RestController
@RequestMapping("/api/v1")
public class ValueController {

    private final ValueService valueService;

    public ValueController(ValueService valueService) {
        this.valueService = valueService;
    }

    @Operation(summary = "활성 가치관 질문 목록 조회",
            description = "온보딩·가치관 화면용 활성 질문 전체. 각 질문은 option_a/option_b 선택지를 포함한다.")
    @GetMapping("/value-questions")
    public ApiResponse<ValueQuestionsResponse> getQuestions() {
        return ApiResponse.ok(valueService.getActiveQuestions());
    }

    @Operation(summary = "내 가치관 답변 조회",
            description = "마이페이지 '가치관 수정' 프리필용. 활성 질문 전체와 내 답변을 함께 반환하며, 미응답 질문은 answer가 null이다.")
    @GetMapping("/value-answers")
    public ApiResponse<MyValueAnswersResponse> getMyAnswers(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(valueService.getMyAnswers(userId));
    }

    @Operation(summary = "가치관 답변 저장",
            description = "가치관 답변을 멱등 저장한다(question_id당 하나, 같은 질문 중복 시 last-wins). "
                    + "answer가 A/B가 아니면 400(INVALID_VALUE_ANSWER), 비활성·존재하지 않는 질문이면 404(VALUE_QUESTION_NOT_FOUND).")
    @PostMapping("/value-answers")
    public ApiResponse<SavedAnswersResponse> saveAnswers(
            @Valid @RequestBody SaveValueAnswersRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(valueService.saveAnswers(userId, request.toCommand()));
    }
}
