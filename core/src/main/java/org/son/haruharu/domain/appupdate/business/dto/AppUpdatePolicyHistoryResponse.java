package org.son.haruharu.domain.appupdate.business.dto;
import java.time.LocalDateTime;
import org.son.haruharu.domain.appupdate.implement.AppUpdatePolicyHistory;
public record AppUpdatePolicyHistoryResponse(Long id, long revision, String previousIosMinimumVersion,
        String previousAndroidMinimumVersion, String iosMinimumVersion, String androidMinimumVersion,
        Long changedBy, LocalDateTime changedAt) {
    public static AppUpdatePolicyHistoryResponse from(AppUpdatePolicyHistory p) { return new AppUpdatePolicyHistoryResponse(p.id(), p.revision(), p.previousIosMinimumVersion(), p.previousAndroidMinimumVersion(), p.iosMinimumVersion(), p.androidMinimumVersion(), p.changedBy(), p.changedAt()); }
}
