package org.son.haruharu.admin.domain.account.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.admin.domain.account.implement.AdminAccount;
import org.son.haruharu.admin.domain.account.implement.AdminAccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AdminAccountRepositoryImpl implements AdminAccountRepository {

    private final AdminAccountJpaRepository adminAccountJpaRepository;

    @Override
    public AdminAccount save(AdminAccount account) {
        return adminAccountJpaRepository.save(AdminAccountEntity.from(account)).toDomain();
    }

    @Override
    public Optional<AdminAccount> findByEmail(String email) {
        return adminAccountJpaRepository.findByEmail(email).map(AdminAccountEntity::toDomain);
    }

    @Override
    public Optional<AdminAccount> findById(Long id) {
        return adminAccountJpaRepository.findById(id).map(AdminAccountEntity::toDomain);
    }

    @Override
    public long count() {
        return adminAccountJpaRepository.count();
    }
}
