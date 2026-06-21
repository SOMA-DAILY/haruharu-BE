package org.son.haruharu.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.common.exception.ConflictException;
import org.son.haruharu.common.exception.NotFoundException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 테스트용 더미 예외 ──────────────────────────────────────────────────

    static class DummyNotFoundException extends NotFoundException {
        DummyNotFoundException() { super("USER_NOT_FOUND", "존재하지 않는 사용자입니다."); }
    }

    static class DummyConflictException extends ConflictException {
        DummyConflictException() { super("USER_ALREADY_EXISTS", "이미 존재하는 사용자입니다."); }
    }

    // ── 테스트용 더미 컨트롤러 ─────────────────────────────────────────────

    record DummyRequest(@NotBlank(message = "이름은 필수입니다.") String name) {}

    @RestController
    static class DummyController {
        @GetMapping("/test/not-found")    void throwNotFound()   { throw new DummyNotFoundException(); }
        @GetMapping("/test/conflict")     void throwConflict()   { throw new DummyConflictException(); }
        @GetMapping("/test/server-error") void throwUnexpected() { throw new RuntimeException("예상치 못한 오류"); }
        @PostMapping("/test/validation")  void validate(@RequestBody @Valid DummyRequest req) {}
    }

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new DummyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    // ── 테스트 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("NotFoundException 발생 시 404와 도메인 에러코드를 반환한다")
    void givenNotFoundException_whenHandle_thenReturns404WithCode() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("존재하지 않는 사용자입니다."))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("ConflictException 발생 시 409와 도메인 에러코드를 반환한다")
    void givenConflictException_whenHandle_thenReturns409WithCode() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("처리되지 않은 예외 발생 시 500과 일반 메시지를 반환한다")
    void givenUnexpectedException_whenHandle_thenReturns500WithGenericMessage() throws Exception {
        mockMvc.perform(get("/test/server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("알 수 없는 에러가 발생했습니다."))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    @DisplayName("Bean Validation 실패 시 400과 필드 에러 메시지를 반환한다")
    void givenInvalidRequest_whenValidate_thenReturns400WithFieldMessage() throws Exception {
        String body = objectMapper.writeValueAsString(new DummyRequest(""));

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("이름은 필수입니다."))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
