package org.son.haruharu.admin.domain.demo.business;

import org.son.haruharu.admin.domain.demo.business.dto.AdminDemoAccessCodeResponse;
import org.son.haruharu.admin.domain.demo.implement.AdminDemoAccessCodeReader;
import org.son.haruharu.admin.domain.demo.implement.AdminDemoAccessCodeWriter;
import org.son.haruharu.domain.demoaccess.implement.DemoAccessCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모 접근 코드 관리(#352, SUPER 전용 — AdminSecurityConfig 제한). 데모 로그인을 여는 코드를 조회·교체한다.
 * 흐름 조율만 담당하고, 단일 행 upsert·Entity 변환은 implement/storage 계층에 맡긴다.
 */
@Service
@Transactional(readOnly = true)
public class AdminDemoService {

    private final AdminDemoAccessCodeReader adminDemoAccessCodeReader;
    private final AdminDemoAccessCodeWriter adminDemoAccessCodeWriter;

    public AdminDemoService(AdminDemoAccessCodeReader adminDemoAccessCodeReader,
                            AdminDemoAccessCodeWriter adminDemoAccessCodeWriter) {
        this.adminDemoAccessCodeReader = adminDemoAccessCodeReader;
        this.adminDemoAccessCodeWriter = adminDemoAccessCodeWriter;
    }

    public AdminDemoAccessCodeResponse getCode() {
        return adminDemoAccessCodeReader.findCurrent()
                .map(code -> AdminDemoAccessCodeResponse.configured(code.code()))
                .orElseGet(AdminDemoAccessCodeResponse::notConfigured);
    }

    @Transactional
    public AdminDemoAccessCodeResponse setCode(String code) {
        DemoAccessCode saved = adminDemoAccessCodeWriter.save(DemoAccessCode.of(code));
        return AdminDemoAccessCodeResponse.configured(saved.code());
    }
}
