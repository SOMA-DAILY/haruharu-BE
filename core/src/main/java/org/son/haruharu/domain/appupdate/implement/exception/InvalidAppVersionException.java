package org.son.haruharu.domain.appupdate.implement.exception;
import org.son.haruharu.common.exception.BadRequestException;
public class InvalidAppVersionException extends BadRequestException {
    public InvalidAppVersionException() { super("APP_VERSION_INVALID", "버전은 1.2.3 형식으로 입력해 주세요. 각 숫자는 0~999999입니다."); }
}
