package org.son.haruharu.domain.appupdate.storage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AppUpdatePolicyHistoryJpaRepository extends JpaRepository<AppUpdatePolicyHistoryEntity, Long> {
    List<AppUpdatePolicyHistoryEntity> findTop20ByOrderByRevisionDesc();
}
