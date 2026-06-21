package org.son.haruharu.infrastructure.pass;

import org.springframework.stereotype.Component;

@Component
public class PassVerificationAdapter implements PassVerificationPort {

    @Override
    public PassVerificationResult verify(String passToken) {
        // TODO: 실제 PASS API 연동 구현
        throw new UnsupportedOperationException("PASS 인증 API 연동 미구현");
    }
}
