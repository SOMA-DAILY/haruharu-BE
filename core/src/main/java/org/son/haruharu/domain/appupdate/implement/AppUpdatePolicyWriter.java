package org.son.haruharu.domain.appupdate.implement;
import java.time.LocalDateTime;
import java.util.Objects;
import org.son.haruharu.domain.appupdate.implement.exception.AppUpdatePolicyConflictException;
import org.springframework.stereotype.Component;
@Component
public class AppUpdatePolicyWriter {
    private final AppUpdatePolicyRepository repository;
    public AppUpdatePolicyWriter(AppUpdatePolicyRepository repository) { this.repository = repository; }
    public AppUpdatePolicy update(String iosVersion, String androidVersion, long revision, Long adminId, LocalDateTime now) {
        String ios = AppUpdatePolicy.normalizeVersion(iosVersion);
        String android = AppUpdatePolicy.normalizeVersion(androidVersion);
        AppUpdatePolicy current = repository.findForUpdate()
                .orElseThrow(() -> new IllegalStateException("App update policy schema/seed is not initialized"));
        if (current.revision() != revision) throw new AppUpdatePolicyConflictException();
        if (Objects.equals(current.iosMinimumVersion(), ios) && Objects.equals(current.androidMinimumVersion(), android)) return current;
        return repository.saveWithHistory(current, new AppUpdatePolicy(ios, android, current.revision() + 1, adminId, now));
    }
}
