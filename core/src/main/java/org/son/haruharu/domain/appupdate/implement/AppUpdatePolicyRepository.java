package org.son.haruharu.domain.appupdate.implement;
import java.util.List;
import java.util.Optional;
public interface AppUpdatePolicyRepository {
    Optional<AppUpdatePolicy> find();
    Optional<AppUpdatePolicy> findForUpdate();
    AppUpdatePolicy saveWithHistory(AppUpdatePolicy previous, AppUpdatePolicy next);
    List<AppUpdatePolicyHistory> findHistory();
}
