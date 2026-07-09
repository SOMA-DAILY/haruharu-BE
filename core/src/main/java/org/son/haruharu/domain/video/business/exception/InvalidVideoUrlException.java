package org.son.haruharu.domain.video.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidVideoUrlException extends BadRequestException {

    public InvalidVideoUrlException() {
        super("VIDEO_INVALID_URL", "본인 업로드 경로의 영상 URL이 아닙니다.");
    }
}
