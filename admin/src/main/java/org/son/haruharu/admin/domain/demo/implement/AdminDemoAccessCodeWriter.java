package org.son.haruharu.admin.domain.demo.implement;

import org.son.haruharu.domain.demoaccess.implement.DemoAccessCode;
import org.springframework.stereotype.Component;

@Component
public class AdminDemoAccessCodeWriter {

    private final AdminDemoAccessCodeRepository adminDemoAccessCodeRepository;

    public AdminDemoAccessCodeWriter(AdminDemoAccessCodeRepository adminDemoAccessCodeRepository) {
        this.adminDemoAccessCodeRepository = adminDemoAccessCodeRepository;
    }

    /** 데모 접근 코드를 설정/교체한다(단일 행 upsert). */
    public DemoAccessCode save(DemoAccessCode code) {
        return adminDemoAccessCodeRepository.save(code);
    }
}
