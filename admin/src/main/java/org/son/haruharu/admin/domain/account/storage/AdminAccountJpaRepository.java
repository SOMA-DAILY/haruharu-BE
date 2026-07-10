package org.son.haruharu.admin.domain.account.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminAccountJpaRepository extends JpaRepository<AdminAccountEntity, Long> {
    Optional<AdminAccountEntity> findByEmail(String email);
}
