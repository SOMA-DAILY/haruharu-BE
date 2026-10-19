package org.son.haruharu.domain.appupdate.implement;
import java.util.List;
import org.springframework.stereotype.Component;
@Component
public class AppUpdatePolicyReader {
    private final AppUpdatePolicyRepository repository;
    public AppUpdatePolicyReader(AppUpdatePolicyRepository repository) { this.repository = repository; }
    public AppUpdatePolicy get() { return repository.find().orElseGet(AppUpdatePolicy::disabled); }
    public List<AppUpdatePolicyHistory> history() { return repository.findHistory(); }
}
