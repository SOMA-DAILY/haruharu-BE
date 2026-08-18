package org.son.haruharu.admin.domain.demo.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.admin.domain.demo.business.AdminDemoService;
import org.son.haruharu.admin.domain.demo.business.dto.AdminDemoAccessCodeResponse;
import org.son.haruharu.admin.domain.demo.application.dto.SetDemoAccessCodeRequest;
import org.son.haruharu.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Demo", description = "데모 접근 코드 관리(SUPER 전용). 이 코드로 데모 로그인이 열린다.")
@RestController
@RequestMapping("/api/v1/admin/demo")
public class AdminDemoController {

    private final AdminDemoService adminDemoService;

    public AdminDemoController(AdminDemoService adminDemoService) {
        this.adminDemoService = adminDemoService;
    }

    @Operation(summary = "데모 접근 코드 조회", description = "현재 코드와 설정 여부. 미설정이면 데모 로그인이 닫혀 있다. SUPER 전용.")
    @GetMapping("/access-code")
    public ApiResponse<AdminDemoAccessCodeResponse> getAccessCode() {
        return ApiResponse.ok(adminDemoService.getCode());
    }

    @Operation(summary = "데모 접근 코드 설정/교체",
            description = "새 코드로 설정한다(단일 값). 유출 시 교체하면 옛 코드는 즉시 무효. SUPER 전용.")
    @PutMapping("/access-code")
    public ApiResponse<AdminDemoAccessCodeResponse> setAccessCode(@RequestBody @Valid SetDemoAccessCodeRequest request) {
        return ApiResponse.ok(adminDemoService.setCode(request.code()));
    }
}
