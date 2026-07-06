package org.son.haruharu.domain.identity.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserIdentityJpaRepository extends JpaRepository<UserIdentityEntity, Long> {
    Optional<UserIdentityEntity> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
    boolean existsByCiHash(String ciHash);
    void deleteByUserId(Long userId);
}
