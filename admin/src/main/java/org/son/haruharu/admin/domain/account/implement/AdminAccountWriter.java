package org.son.haruharu.admin.domain.account.implement;

import org.springframework.stereotype.Component;

@Component
public class AdminAccountWriter {

    private final AdminAccountRepository adminAccountRepository;

    public AdminAccountWriter(AdminAccountRepository adminAccountRepository) {
        this.adminAccountRepository = adminAccountRepository;
    }

    public AdminAccount save(AdminAccount account) {
        return adminAccountRepository.save(account);
    }
}
