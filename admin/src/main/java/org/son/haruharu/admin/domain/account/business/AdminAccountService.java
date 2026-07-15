package org.son.haruharu.admin.domain.account.business;

import org.son.haruharu.admin.domain.account.business.command.CreateAdminAccountCommand;
import org.son.haruharu.admin.domain.account.business.command.UpdateAdminAccountCommand;
import org.son.haruharu.admin.domain.account.business.dto.AdminAccountResponse;
import org.son.haruharu.admin.domain.account.business.exception.AdminAccountDuplicateException;
import org.son.haruharu.admin.domain.account.implement.AdminAccount;
import org.son.haruharu.admin.domain.account.implement.AdminAccountReader;
import org.son.haruharu.admin.domain.account.implement.AdminAccountWriter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 어드민 계정 관리(추가·목록·역할/상태 변경). 전부 SUPER 권한 — AdminSecurityConfig에서 제한. */
@Service
@Transactional(readOnly = true)
public class AdminAccountService {

    private final AdminAccountReader adminAccountReader;
    private final AdminAccountWriter adminAccountWriter;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountService(AdminAccountReader adminAccountReader,
                               AdminAccountWriter adminAccountWriter,
                               PasswordEncoder passwordEncoder) {
        this.adminAccountReader = adminAccountReader;
        this.adminAccountWriter = adminAccountWriter;
        this.passwordEncoder = passwordEncoder;
    }

    public List<AdminAccountResponse> getAccounts() {
        return adminAccountReader.findAll().stream().map(AdminAccountResponse::from).toList();
    }

    @Transactional
    public AdminAccountResponse create(CreateAdminAccountCommand command) {
        if (adminAccountReader.existsByEmail(command.email())) {
            throw new AdminAccountDuplicateException();
        }
        AdminAccount saved = adminAccountWriter.save(AdminAccount.create(
                command.email(),
                passwordEncoder.encode(command.rawPassword()),
                command.name(),
                command.role()));
        return AdminAccountResponse.from(saved);
    }

    @Transactional
    public AdminAccountResponse update(Long id, UpdateAdminAccountCommand command) {
        AdminAccount existing = adminAccountReader.getById(id);
        // 부분 수정: 제공된 필드만 갱신. email·passwordHash는 불변.
        AdminAccount updated = existing.changedTo(
                command.role() != null ? command.role() : existing.role(),
                command.status() != null ? command.status() : existing.status());
        return AdminAccountResponse.from(adminAccountWriter.save(updated));
    }
}
