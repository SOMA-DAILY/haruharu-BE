package org.son.haruharu.domain.video.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

public class NotVideoOwnerException extends ForbiddenException {

    public NotVideoOwnerException() {
        super("VIDEO_403_NOT_OWNER", "본인의 영상만 삭제할 수 있습니다.");
    }
}
