package org.son.haruharu.demo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.demo.dto.DemoMatchResultRequest;
import org.son.haruharu.demo.exception.DemoForbiddenException;
import org.son.haruharu.domain.room.business.DemoMatchService;
import org.son.haruharu.domain.room.business.dto.DemoMatchResultResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데모 최종선택 결과(#266). 데모 계정 인가는 DemoModeInterceptor가 /api/v1/demo/** 경로에 강제하므로
 * 컨트롤러는 인증 principal(userId)만 사용한다. MATCHED면 실제 채팅방·대화가 시드돼 chat_room_id가 반환된다.
 */
@Tag(name = "Demo Match", description = "앱스토어 심사용 데모 최종선택 결과(데모 계정 전용)")
@RestController
@RequestMapping("/api/v1/demo/match-result")
public class DemoMatchResultController {

    private final DemoMatchService demoMatchService;

    public DemoMatchResultController(DemoMatchService demoMatchService) {
        this.demoMatchService = demoMatchService;
    }

    @Operation(summary = "데모 최종선택 결과 생성",
            description = "MATCHED: 가상 상대와 성사 + 실제 채팅방·대화 시드. FAILED: 위로 구름 지급(chat_room_id=null).")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<DemoMatchResultResponse> createResult(Authentication authentication,
                                                             @Valid @RequestBody DemoMatchResultRequest request) {
        // 인터셉터 선행 보장에 기대지 않고 principal 타입을 방어적으로 확인(DemoAccessGuard와 동일 패턴).
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new DemoForbiddenException();
        }
        return ApiResponse.ok(demoMatchService.createResult(userId, request.toOutcome()));
    }
}
