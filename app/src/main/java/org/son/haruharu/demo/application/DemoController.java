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
            description = "어드민이 설정한 접근 코드가 맞으면 고정 데모 계정의 JWT(access/refresh)를 발급한다. "
                    + "계정이 없으면 그 자리에서 생성(lazy·멱등). 코드가 없거나 틀리면 401. OAuth 불필요.")
    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@RequestBody @Valid DemoLoginRequest request,
                                                HttpServletRequest servletRequest) {
        // 남용 모니터링용 — 인증 없는 공개 엔드포인트라 호출 흔적(IP·시각)을 남긴다(성공/실패 무관, 코드값은 로깅하지 않음).
        log.info("demo login attempt ip={}", servletRequest.getRemoteAddr());
        return ApiResponse.ok(demoAccountService.login(request.code()));
    }
}
