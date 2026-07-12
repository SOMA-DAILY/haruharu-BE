package org.son.haruharu.domain.profile.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserImageJpaRepository extends JpaRepository<UserImageEntity, Long> {

    List<UserImageEntity> findAllByUserIdOrderBySortedOrderAsc(Long userId);

    List<UserImageEntity> findAllByUserIdInOrderByUserIdAscSortedOrderAsc(Collection<Long> userIds);

    void deleteAllByUserId(Long userId);
}
