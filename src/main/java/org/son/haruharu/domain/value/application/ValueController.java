package org.son.haruharu.domain.value.application;

import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.value.application.dto.SaveValueAnswersRequest;
import org.son.haruharu.domain.value.business.ValueService;
import org.son.haruharu.domain.value.business.dto.SavedAnswersResponse;
import org.son.haruharu.domain.value.business.dto.ValueQuestionsResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ValueController {

    private final ValueService valueService;

    public ValueController(ValueService valueService) {
        this.valueService = valueService;
    }

    @GetMapping("/value-questions")
    public ApiResponse<ValueQuestionsResponse> getQuestions() {
        return ApiResponse.ok(valueService.getActiveQuestions());
    }

    @PostMapping("/value-answers")
    public ApiResponse<SavedAnswersResponse> saveAnswers(
            @Valid @RequestBody SaveValueAnswersRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(valueService.saveAnswers(userId, request.toCommand()));
    }
}
