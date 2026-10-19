package org.son.haruharu.domain.notification.storage;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;

public interface PushBundleJpaRepository extends JpaRepository<PushBundleEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PushBundleEntity> findAllByUserIdInAndBundleKeyOrderByUserId(Collection<Long> userIds, String bundleKey);
}
