package org.son.haruharu.domain.chat.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class VideoReplyInvalidException extends BadRequestException {
    public VideoReplyInvalidException(String message) {
        super("VIDEO_REPLY_INVALID", message);
    }
}
