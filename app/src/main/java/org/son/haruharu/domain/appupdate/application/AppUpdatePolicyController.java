package org.son.haruharu.domain.appupdate.application;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.appupdate.business.AppUpdatePolicyService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/meta/app-update-policy")
public class AppUpdatePolicyController {
    private final AppUpdatePolicyService service;
    public AppUpdatePolicyController(AppUpdatePolicyService service) { this.service = service; }
    public record PolicyResponse(String iosMinimumVersion, String androidMinimumVersion, long revision) {}
    @GetMapping
    public ResponseEntity<ApiResponse<PolicyResponse>> get() {
        var p = service.get();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(new PolicyResponse(p.iosMinimumVersion(), p.androidMinimumVersion(), p.revision())));
    }
}
