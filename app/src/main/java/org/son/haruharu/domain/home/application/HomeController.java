package org.son.haruharu.domain.home.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.home.business.HomeService;
import org.son.haruharu.domain.home.business.dto.HomeResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "홈", description = "메인 홈 화면 집계 조회")
@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @Operation(summary = "메인 홈 조회",
            description = "메인 홈 집계를 반환한다. 참여 중인 소개팅방(active_room: room_id·status·current_day·starts_at·ends_at, "
                    + "없으면 null → 초대코드 입력 유도)과 발표(result_at) 이후 매칭 결과(pending_result, 없으면 null)를 포함한다. "
                    + "now는 클라 카운트다운의 기기 시계 오차 보정 기준.")
    @GetMapping
    public ApiResponse<HomeResponse> getHome(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(homeService.getHome(userId));
    }
}
