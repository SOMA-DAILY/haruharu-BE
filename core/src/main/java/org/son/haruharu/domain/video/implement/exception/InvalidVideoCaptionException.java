package org.son.haruharu.domain.video.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidVideoCaptionException extends BadRequestException {
    public InvalidVideoCaptionException() {
        super("VIDEO_CAPTION_TOO_LONG", "캡션은 100자까지 작성할 수 있어요.");
    }
}
