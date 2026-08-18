package org.son.haruharu.admin.domain.demo.implement;

import java.util.Optional;

import org.son.haruharu.domain.demoaccess.implement.DemoAccessCode;
import org.springframework.stereotype.Component;

@Component
public class AdminDemoAccessCodeReader {

    private final AdminDemoAccessCodeRepository adminDemoAccessCodeRepository;

    public AdminDemoAccessCodeReader(AdminDemoAccessCodeRepository adminDemoAccessCodeRepository) {
        this.adminDemoAccessCodeRepository = adminDemoAccessCodeRepository;
    }

    public Optional<DemoAccessCode> findCurrent() {
        return adminDemoAccessCodeRepository.findCurrent();
    }
}
