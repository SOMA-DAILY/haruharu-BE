package org.son.haruharu.domain.appupdate.implement.exception;
import org.son.haruharu.common.exception.ConflictException;
public class AppUpdatePolicyConflictException extends ConflictException {
    public AppUpdatePolicyConflictException() { super("APP_UPDATE_POLICY_CONFLICT", "다른 관리자가 정책을 변경했습니다. 새로고침 후 다시 확인해 주세요."); }
}
