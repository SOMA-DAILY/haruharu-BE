package org.son.haruharu.demo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.demo.exception.DemoForbiddenException;
import org.son.haruharu.domain.room.business.DemoRoomService;
import org.son.haruharu.domain.room.business.dto.DemoRoomResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

/**
 * 데모 데이터 초기화(#267). 데모 계정 인가는 DemoModeInterceptor가 /api/v1/demo/** 경로에 강제하므로
 * 컨트롤러는 인증 principal(userId)만 사용한다. 방·멤버·가상계정은 재사용하고 Day1·피드·구름만 되돌린다.
 */
@Tag(name = "Demo Reset", description = "앱스토어 심사용 데모 데이터 초기화(데모 계정 전용)")
@RestController
@RequestMapping("/api/v1/demo/reset")
@Profile("!prod") // 데모는 dev/local/test에서만 로드, prod 제외(#284)
public class DemoResetController {

    private final DemoRoomService demoRoomService;

    public DemoResetController(DemoRoomService demoRoomService) {
        this.demoRoomService = demoRoomService;
    }

    @Operation(summary = "데모 데이터 초기화",
            description = "데모 방을 Day1로 되돌리고 피드를 재시드하며 구름을 리시드한다. 참가신청부터 재시연 가능.")
    @PostMapping
    public ApiResponse<DemoRoomResponse> reset(Authentication authentication) {
        // 인터셉터 선행 보장에 기대지 않고 principal 타입을 방어적으로 확인(DemoAccessGuard와 동일 패턴).
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new DemoForbiddenException();
        }
        return ApiResponse.ok(demoRoomService.reset(userId));
    }
}
