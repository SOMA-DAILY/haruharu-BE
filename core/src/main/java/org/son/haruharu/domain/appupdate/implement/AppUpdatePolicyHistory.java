package org.son.haruharu.domain.appupdate.implement;
import java.time.LocalDateTime;
public record AppUpdatePolicyHistory(Long id, long revision, String previousIosMinimumVersion,
        String previousAndroidMinimumVersion, String iosMinimumVersion, String androidMinimumVersion,
        Long changedBy, LocalDateTime changedAt) {}
