package org.son.haruharu.domain.appupdate.storage;
import java.util.List;
import java.util.Optional;
import org.son.haruharu.domain.appupdate.implement.*;
import org.springframework.stereotype.Repository;
@Repository
public class AppUpdatePolicyRepositoryImpl implements AppUpdatePolicyRepository {
    private final AppUpdatePolicyJpaRepository policyRepository;
    private final AppUpdatePolicyHistoryJpaRepository historyRepository;
    public AppUpdatePolicyRepositoryImpl(AppUpdatePolicyJpaRepository policyRepository, AppUpdatePolicyHistoryJpaRepository historyRepository) {
        this.policyRepository = policyRepository;this.historyRepository = historyRepository;
    }
    public Optional<AppUpdatePolicy> find() { return policyRepository.findById(1L).map(AppUpdatePolicyEntity::toDomain); }
    public Optional<AppUpdatePolicy> findForUpdate() { return policyRepository.findForUpdate().map(AppUpdatePolicyEntity::toDomain); }
    public AppUpdatePolicy saveWithHistory(AppUpdatePolicy previous, AppUpdatePolicy next) {
        AppUpdatePolicy saved = policyRepository.save(AppUpdatePolicyEntity.from(next)).toDomain();
        historyRepository.save(AppUpdatePolicyHistoryEntity.from(previous, next));return saved;
    }
    public List<AppUpdatePolicyHistory> findHistory() { return historyRepository.findTop20ByOrderByRevisionDesc().stream().map(AppUpdatePolicyHistoryEntity::toDomain).toList(); }
}
