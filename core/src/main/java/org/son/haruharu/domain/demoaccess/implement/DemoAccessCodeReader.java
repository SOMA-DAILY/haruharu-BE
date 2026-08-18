package org.son.haruharu.domain.demoaccess.implement;

import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class DemoAccessCodeReader {

    private final DemoAccessCodeRepository demoAccessCodeRepository;

    public DemoAccessCodeReader(DemoAccessCodeRepository demoAccessCodeRepository) {
        this.demoAccessCodeRepository = demoAccessCodeRepository;
    }

    /** 현재 데모 접근 코드. 미설정이면 empty(데모 로그인 닫힘). */
    public Optional<DemoAccessCode> findCurrent() {
        return demoAccessCodeRepository.findCurrent();
    }
}
