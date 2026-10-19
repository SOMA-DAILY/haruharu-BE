package org.son.haruharu.admin.domain.user.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.user.business.AdminUserService;
import org.son.haruharu.admin.domain.user.business.dto.AdminUserDetailResponse;
import org.son.haruharu.admin.domain.user.business.dto.AdminUserSummary;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin User", description = "운영 유저 조회")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "유저 목록", description = "닉네임 keyword·status 필터 + 페이지네이션.")
    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserSummary>> users(
            @Parameter(description = "닉네임 검색어", example = "여울") @RequestParam(required = false) String keyword,
            @Parameter(description = "상태 필터", example = "ACTIVE") @RequestParam(required = false) UserStatus status,
            @Parameter(description = "0-based 페이지", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기(최대 100)", example = "20") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminUserService.getUsers(status, keyword, page, size));
    }

    @Operation(summary = "유저 상세", description = "프로필·티켓 잔액·결제이력·참여이력. 없는 id는 404.")
    @GetMapping("/users/{userId}")
    public ApiResponse<AdminUserDetailResponse> user(
            @Parameter(description = "유저 id", example = "901") @PathVariable Long userId) {
        return ApiResponse.ok(adminUserService.getUser(userId));
    }
}
