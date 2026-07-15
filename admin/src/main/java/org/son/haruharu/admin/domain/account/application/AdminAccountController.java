package org.son.haruharu.admin.domain.account.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.admin.domain.account.application.dto.CreateAdminAccountRequest;
import org.son.haruharu.admin.domain.account.application.dto.UpdateAdminAccountRequest;
import org.son.haruharu.admin.domain.account.business.AdminAccountService;
import org.son.haruharu.admin.domain.account.business.dto.AdminAccountResponse;
import org.son.haruharu.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Account", description = "어드민 계정 관리(SUPER 전용)")
@RestController
@RequestMapping("/api/v1/admin/admins")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @Operation(summary = "어드민 계정 생성", description = "이메일 중복 시 409. SUPER 전용.")
    @PostMapping
    public ApiResponse<AdminAccountResponse> create(@RequestBody @Valid CreateAdminAccountRequest request) {
        return ApiResponse.ok(adminAccountService.create(request.toCommand()));
    }

    @Operation(summary = "어드민 계정 목록", description = "전체 계정(비밀번호 제외). SUPER 전용.")
    @GetMapping
    public ApiResponse<List<AdminAccountResponse>> list() {
        return ApiResponse.ok(adminAccountService.getAccounts());
    }

    @Operation(summary = "어드민 계정 역할·상태 변경", description = "보낸 필드만 부분 갱신(역할·상태). SUPER 전용.")
    @PatchMapping("/{id}")
    public ApiResponse<AdminAccountResponse> update(
            @Parameter(description = "어드민 id", example = "1") @PathVariable Long id,
            @RequestBody @Valid UpdateAdminAccountRequest request) {
        return ApiResponse.ok(adminAccountService.update(id, request.toCommand()));
    }
}
