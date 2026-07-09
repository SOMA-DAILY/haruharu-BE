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
            description = "메인 홈 집계를 반환한다. 포인트 잔액, 매칭 대기 상태, 진행 중인 소개팅방, "
                    + "다음 신청 마감·매칭 확정 시각 등 홈 카운트다운 기준값을 포함한다.")
    @GetMapping
    public ApiResponse<HomeResponse> getHome(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(homeService.getHome(userId));
    }
}
