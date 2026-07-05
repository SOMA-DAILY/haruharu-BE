package org.son.haruharu.domain.notification.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenJpaRepository extends JpaRepository<DeviceTokenEntity, Long> {

    Optional<DeviceTokenEntity> findByToken(String token);

    List<DeviceTokenEntity> findAllByUserIdIn(Collection<Long> userIds);

    void deleteByUserIdAndToken(Long userId, String token);

    void deleteByTokenIn(Collection<String> tokens);
}
