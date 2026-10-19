package org.son.haruharu.admin.domain.appupdate.application;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.appupdate.business.AppUpdatePolicyService;
import org.son.haruharu.domain.appupdate.business.command.UpdateAppUpdatePolicyCommand;
import org.son.haruharu.domain.appupdate.business.dto.AppUpdatePolicyResponse;
import org.son.haruharu.domain.appupdate.business.dto.AppUpdatePolicyHistoryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/admin/app-update-policy")
public class AdminAppUpdatePolicyController {
    private final AppUpdatePolicyService service;
    public AdminAppUpdatePolicyController(AppUpdatePolicyService service) { this.service = service; }
    public record UpdateRequest(@Size(max = 40) String iosMinimumVersion, @Size(max = 40) String androidMinimumVersion,
            @NotNull @Min(0) Long revision) {
        UpdateAppUpdatePolicyCommand toCommand() { return new UpdateAppUpdatePolicyCommand(iosMinimumVersion, androidMinimumVersion, revision); }
    }
    @GetMapping public ApiResponse<AppUpdatePolicyResponse> get() { return ApiResponse.ok(service.get()); }
    @GetMapping("/history") public ApiResponse<List<AppUpdatePolicyHistoryResponse>> history() { return ApiResponse.ok(service.history()); }
    @PutMapping public ApiResponse<AppUpdatePolicyResponse> update(@Valid @RequestBody UpdateRequest request, @AuthenticationPrincipal Long adminId) {
        return ApiResponse.ok(service.update(request.toCommand(), adminId));
    }
}
