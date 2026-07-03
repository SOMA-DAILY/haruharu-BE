package org.son.haruharu.domain.profile.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserImageJpaRepository extends JpaRepository<UserImageEntity, Long> {

    List<UserImageEntity> findAllByUserIdOrderBySortedOrderAsc(Long userId);

    void deleteAllByUserId(Long userId);
}
