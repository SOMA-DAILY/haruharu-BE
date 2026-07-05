package org.son.haruharu.domain.home.application;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.home.business.HomeService;
import org.son.haruharu.domain.home.business.dto.HomeResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping
    public ApiResponse<HomeResponse> getHome(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(homeService.getHome(userId));
    }
}
