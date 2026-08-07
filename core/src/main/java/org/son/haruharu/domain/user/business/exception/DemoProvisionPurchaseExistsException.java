package org.son.haruharu.domain.user.business.exception;

import org.son.haruharu.common.exception.ConflictException;

/**
 * 실결제(IAP) 이력이 있는 계정에 데모 프로비저닝을 시도했을 때(#277, #292).
 * env(DEMO_PROVISION_USER_IDS) 오배포로 실사용자 id가 섞여도 결제 원장을 보호하기 위한 가드다.
 */
public class DemoProvisionPurchaseExistsException extends ConflictException {

    public DemoProvisionPurchaseExistsException() {
        super("DEMO_PROVISION_PURCHASE_EXISTS", "실결제 이력이 있는 계정은 데모 프로비저닝 대상이 아닙니다.");
    }
}
