package org.son.haruharu.domain.appupdate.storage;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface AppUpdatePolicyJpaRepository extends JpaRepository<AppUpdatePolicyEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AppUpdatePolicyEntity p where p.id = 1")
    Optional<AppUpdatePolicyEntity> findForUpdate();
}
