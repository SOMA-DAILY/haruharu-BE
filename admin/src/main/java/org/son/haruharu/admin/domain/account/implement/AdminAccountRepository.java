package org.son.haruharu.admin.domain.account.implement;

import java.util.Optional;

/** 어드민 계정 포트(도메인 계약). storage가 JPA로 구현한다. */
public interface AdminAccountRepository {
    AdminAccount save(AdminAccount account);
    Optional<AdminAccount> findByEmail(String email);
    Optional<AdminAccount> findById(Long id);
    long count();
}
