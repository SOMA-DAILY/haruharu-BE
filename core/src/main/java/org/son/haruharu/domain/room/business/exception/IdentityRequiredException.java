package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

/** PASS 본인확인을 하지 않은 계정의 초대코드 새 입장(#514). 앱은 이 코드를 받으면 본인확인 화면으로 보낸다. */
public class IdentityRequiredException extends ForbiddenException {

    public IdentityRequiredException() {
        super("IDENTITY_REQUIRED", "본인확인(PASS)을 완료해야 방에 입장할 수 있습니다.");
    }
}
