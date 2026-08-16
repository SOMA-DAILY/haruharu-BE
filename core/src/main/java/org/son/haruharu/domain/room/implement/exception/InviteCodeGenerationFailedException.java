package org.son.haruharu.domain.room.implement.exception;

import org.son.haruharu.common.exception.ConflictException;

/**
 * 초대코드 자동 생성이 재시도 한도까지 계속 기존 코드와 충돌했을 때(#318).
 * 32^6 공간에서 정상적으로는 발생하지 않는다 — 발생 시 코드 누적량 점검 신호다.
 * <p>
 * 던지는 주체가 implement 계층(InviteCodeGenerator)이므로 예외도 implement/exception 에 둔다
 * — implement 가 business 를 import 하면 의존 방향이 뒤집힌다.
 */
public class InviteCodeGenerationFailedException extends ConflictException {

    public InviteCodeGenerationFailedException() {
        super("INVITE_CODE_GENERATION_FAILED", "초대코드 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }
}
