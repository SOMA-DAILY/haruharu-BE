package org.son.haruharu.domain.video.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidVideoCaptionException extends BadRequestException {
    public InvalidVideoCaptionException() {
        super("INVALID_INPUT", "신규 영상 캡션은 20자 이하여야 합니다.");
    }
}
