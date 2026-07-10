package org.son.haruharu.admin.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.admin.domain.account.implement.AdminAccount;
import org.son.haruharu.admin.domain.account.implement.AdminAccountReader;
import org.son.haruharu.admin.domain.account.implement.AdminAccountWriter;
import org.son.haruharu.admin.domain.account.implement.AdminRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최초 SUPER 관리자 부트스트랩. 계정이 0건이고 부트스트랩 자격증명이 주입돼 있을 때만 1회 생성한다.
 * (없으면 로그인 가능한 계정이 전무 → 어드민 진입 불가)
 */
@Slf4j
@Component
public class AdminAccountBootstrap implements ApplicationRunner {

    private final AdminAccountReader adminAccountReader;
    private final AdminAccountWriter adminAccountWriter;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapEmail;
    private final String bootstrapPassword;

    public AdminAccountBootstrap(AdminAccountReader adminAccountReader, AdminAccountWriter adminAccountWriter,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${admin.bootstrap.email:}") String bootstrapEmail,
                                 @Value("${admin.bootstrap.password:}") String bootstrapPassword) {
        this.adminAccountReader = adminAccountReader;
        this.adminAccountWriter = adminAccountWriter;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) {
            return;
        }
        if (adminAccountReader.count() > 0) {
            return;
        }
        adminAccountWriter.save(AdminAccount.create(
                bootstrapEmail, passwordEncoder.encode(bootstrapPassword), "슈퍼관리자", AdminRole.SUPER));
        log.info("부트스트랩 SUPER 관리자 계정을 생성했습니다: {}", bootstrapEmail);
    }
}
