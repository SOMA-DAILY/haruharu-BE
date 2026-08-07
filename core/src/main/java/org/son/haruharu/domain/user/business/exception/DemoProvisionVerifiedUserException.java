package org.son.haruharu.domain.user.business.exception;

import org.son.haruharu.common.exception.ConflictException;

/**
 * 데모 계정이 아닌데 이미 본인인증을 마친 계정 = 실사용자에게 데모 프로비저닝을 시도했을 때(#292).
 * env 오배포로 실계정이 섞인 것으로 보고 상태 전이·구름 지급을 시작하지 않는다.
 */
public class DemoProvisionVerifiedUserException extends ConflictException {

    public DemoProvisionVerifiedUserException() {
        super("DEMO_PROVISION_VERIFIED_USER", "본인인증을 마친 실사용자는 데모 프로비저닝 대상이 아닙니다.");
    }
}
