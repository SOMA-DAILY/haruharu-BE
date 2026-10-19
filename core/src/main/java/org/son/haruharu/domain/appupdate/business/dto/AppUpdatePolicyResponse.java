package org.son.haruharu.domain.appupdate.business.dto;
import java.time.LocalDateTime;
import org.son.haruharu.domain.appupdate.implement.AppUpdatePolicy;
public record AppUpdatePolicyResponse(String iosMinimumVersion, String androidMinimumVersion,
        long revision, Long updatedBy, LocalDateTime updatedAt) {
    public static AppUpdatePolicyResponse from(AppUpdatePolicy p) { return new AppUpdatePolicyResponse(p.iosMinimumVersion(), p.androidMinimumVersion(), p.revision(), p.updatedBy(), p.updatedAt()); }
}
