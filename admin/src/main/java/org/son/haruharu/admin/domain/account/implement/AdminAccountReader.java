package org.son.haruharu.admin.domain.account.implement;

import org.son.haruharu.admin.domain.account.storage.exception.AdminAccountNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AdminAccountReader {

    private final AdminAccountRepository adminAccountRepository;

    public AdminAccountReader(AdminAccountRepository adminAccountRepository) {
        this.adminAccountRepository = adminAccountRepository;
    }

    public AdminAccount getById(Long id) {
        return adminAccountRepository.findById(id)
                .orElseThrow(AdminAccountNotFoundException::new);
    }

    public List<AdminAccount> findAll() {
        return adminAccountRepository.findAll();
    }

    public Optional<AdminAccount> findByEmail(String email) {
        return adminAccountRepository.findByEmail(email);
    }

    public boolean existsByEmail(String email) {
        return adminAccountRepository.existsByEmail(email);
    }

    public long count() {
        return adminAccountRepository.count();
    }
}
