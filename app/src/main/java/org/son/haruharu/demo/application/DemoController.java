package org.son.haruharu.demo.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.demo.application.dto.DemoLoginRequest;
import org.son.haruharu.demo.business.DemoAccountService;
import org.son.haruharu.domain.auth.business.dto.AuthTokenResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 심사용 데모 진입점(#352). 어드민이 설정한 접근 코드로 게이트된 데모 로그인. prod 노출 —
 * 코드 없이는 세션이 발급되지 않으며(익명 오라클 차단), 데모 계정/방에만 영향을 준다(SecurityConfig permitAll).
 */
@Slf4j
@Tag(name = "Demo", description = "App Store 심사용 데모. 접근 코드로 게이트된 데모 로그인(도메인 무변경, 격리)")
@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final DemoAccountService demoAccountService;

    public DemoController(DemoAccountService demoAccountService) {
        this.demoAccountService = demoAccountService;
    }

    @Operation(summary = "데모 로그인",
            description = "어드민이 설정한 접근 코드가 맞으면 고정 데모 계정의 JWT(access/refresh)를 발급하고, "
                    + "그 순간 신선한 데모 방(유령 5명·GROUP 채팅·미션)을 세운다. 코드가 없거나 틀리면 401. OAuth 불필요.")
    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@RequestBody @Valid DemoLoginRequest request,
                                                HttpServletRequest servletRequest) {
        // 남용 모니터링용 — 인증 없는 공개 엔드포인트라 호출 흔적(IP·시각)을 남긴다(성공/실패 무관, 코드값은 로깅하지 않음).
        log.info("demo login attempt ip={}", servletRequest.getRemoteAddr());
        return ApiResponse.ok(demoAccountService.login(request.code()));
    }

    @Operation(summary = "데모 데이터 리셋",
            description = "접근 코드가 맞으면 데모 방과 그 안의 모든 데이터(영상·반응·채팅·미션)를 물리 삭제한다. "
                    + "다음 로그인이 새로 세운다. 코드가 없거나 틀리면 401.")
    @PostMapping("/reset")
    public ApiResponse<Void> reset(@RequestBody @Valid DemoLoginRequest request,
                                   HttpServletRequest servletRequest) {
        // 파괴적(물리삭제) 공개 엔드포인트 — 로그인과 대칭으로 호출 흔적(IP·시각)을 남긴다(코드값 미로깅).
        log.info("demo reset attempt ip={}", servletRequest.getRemoteAddr());
        demoAccountService.reset(request.code());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "데모 매칭 즉시 성사",
            description = "접근 코드가 맞으면 데모 계정을 반대 성별 유령과 상호 성사시켜 1:1(DIRECT) 채팅을 연다. "
                    + "최종선택 타이밍을 기다리지 않고 매칭 페이오프를 시연한다. 이미 성사됐으면 멱등. 코드 없으면 401.")
    @PostMapping("/match-result")
    public ApiResponse<Void> matchResult(@RequestBody @Valid DemoLoginRequest request,
                                         HttpServletRequest servletRequest) {
        log.info("demo match attempt ip={}", servletRequest.getRemoteAddr());
        demoAccountService.matchNow(request.code());
        return ApiResponse.ok(null);
    }
}
