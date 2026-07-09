package org.son.haruharu.domain.video.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidVideoExtensionException extends BadRequestException {

    public InvalidVideoExtensionException() {
        super("VIDEO_INVALID_EXTENSION", "지원하지 않는 영상 확장자입니다. (mp4, mov, webm)");
    }
}
